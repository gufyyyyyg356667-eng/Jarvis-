package com.jarvis.assistant.typing

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.jarvis.assistant.automation.accessibility.JarvisAccessibilityService
import java.util.Locale

/**
 * Universal "typing like a hand" fallback.
 * Many apps (code editors, note apps, custom text views) do not expose their text box to Android accessibility,
 * so setting text directly is impossible. Every one of them still accepts text from the on-screen keyboard,
 * so we copy the text and tap it in the keyboard's clipboard (Gboard), exactly like a person would.
 */
object KeyboardTyper {
    private const val TAG = "KeyboardTyper"

    class Outcome(val success: Boolean, val message: String)

    fun type(ctx: Context, text: String, submit: Boolean): Outcome {
        return try {
            typeInternal(ctx, text, submit)
        } catch (e: Exception) {
            Log.e(TAG, "keyboard typing failed: ${e.message}", e)
            Outcome(false, "Keyboard typing failed: ${e.message}")
        }
    }

    private fun typeInternal(ctx: Context, text: String, submit: Boolean): Outcome {
        val svc = JarvisAccessibilityService.instance ?: return Outcome(false, "Accessibility is not connected.")
        if (text.isBlank()) return Outcome(false, "Nothing to type.")
        if (imeRoots(svc).isEmpty()) {
            return Outcome(false, "No on-screen keyboard is visible. Tap the text field first so the keyboard opens.")
        }

        val focused = try {
            svc.rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        } catch (e: Exception) {
            null
        }
        val focusId = focused?.viewIdResourceName ?: ""
        if (focusId.contains("url_bar", ignoreCase = true) || focusId.contains("omnibox", ignoreCase = true)) {
            return Outcome(false, "The focused field is the browser address bar, so I did not type there.")
        }
        if (focused != null && focused.isPassword) {
            return Outcome(false, "That is a password field. I do not send passwords through the clipboard.")
        }

        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("JarvisInput", text))
        sleep(900)

        val prefix = norm(text).take(8)
        var pasted = false

        // 1) open the keyboard's clipboard panel and tap the newest item (ours)
        val clipIcon = findNode(imeRoots(svc)) { n ->
            (norm(n.contentDescription) == "clipboard" || norm(n.text) == "clipboard") && (n.isClickable || n.isFocusable)
        }
        if (clipIcon != null && press(svc, clipIcon)) {
            sleep(900)
            val item = findNode(imeRoots(svc)) { n ->
                val t = norm(n.text)
                t.length >= minOf(prefix.length, 2) && t.startsWith(prefix.take(6)) && n !== clipIcon
            }
            if (item != null && press(svc, item)) {
                pasted = true
                sleep(500)
            }
        }

        // 2) the "recently copied" chip in the keyboard's suggestion strip (shortened text ending in an ellipsis)
        if (!pasted) {
            val chip = findNode(imeRoots(svc)) { n ->
                val raw = n.text?.toString() ?: ""
                val t = norm(raw)
                t.length >= 3 && t.startsWith(prefix.take(6)) && (raw.contains("…") || raw.contains("...") || norm(n.contentDescription).contains("clip"))
            }
            if (chip != null && press(svc, chip)) {
                pasted = true
                sleep(500)
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) cm.clearPrimaryClip()
        } catch (e: Exception) {
        }

        if (!pasted) {
            return Outcome(
                false,
                "I could not find the keyboard's clipboard. In Gboard turn on Settings > Clipboard, and keep the keyboard open on the text field."
            )
        }

        if (submit) {
            val enter = findNode(imeRoots(svc)) { n ->
                val d = norm(n.contentDescription)
                val t = norm(n.text)
                listOf("enter", "search", "go", "done", "next", "send").any { w -> d == w || t == w }
            }
            if (enter != null) {
                press(svc, enter)
                sleep(300)
            }
        }
        return Outcome(true, "Pasted through the keyboard clipboard.")
    }

    // ------------------------------------------------------------------ helpers
    private fun sleep(ms: Long) {
        try {
            Thread.sleep(ms)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun norm(s: CharSequence?): String = (s?.toString() ?: "").replace("\n", " ").trim().lowercase(Locale.ROOT)

    private fun imeRoots(svc: JarvisAccessibilityService): List<AccessibilityNodeInfo> {
        val out = ArrayList<AccessibilityNodeInfo>()
        try {
            for (w in svc.windows) {
                if (w.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                    val r = w.root
                    if (r != null) out.add(r)
                }
            }
        } catch (e: Exception) {
        }
        return out
    }

    private fun findNode(roots: List<AccessibilityNodeInfo>, matches: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        for (root in roots) {
            val found = search(root, 0, matches)
            if (found != null) return found
        }
        return null
    }

    private fun search(node: AccessibilityNodeInfo?, depth: Int, matches: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (node == null || depth > 25) return null
        if (matches(node)) return node
        for (i in 0 until node.childCount) {
            val f = search(node.getChild(i), depth + 1, matches)
            if (f != null) return f
        }
        return null
    }

    private fun press(svc: JarvisAccessibilityService, node: AccessibilityNodeInfo): Boolean {
        var target: AccessibilityNodeInfo? = node
        var guard = 0
        while (target != null && !target.isClickable && guard < 5) {
            target = target.parent
            guard++
        }
        val clickable = if (target != null && target.isClickable) target else node
        if (clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        val r = Rect()
        clickable.getBoundsInScreen(r)
        if (r.isEmpty) return false
        return svc.clickAt(r.exactCenterX(), r.exactCenterY())
    }
}
