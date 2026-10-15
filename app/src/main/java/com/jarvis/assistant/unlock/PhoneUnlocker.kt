package com.jarvis.assistant.unlock

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.KeyguardManager
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.PowerManager
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.jarvis.assistant.automation.accessibility.JarvisAccessibilityService
import org.json.JSONObject
import java.util.Locale

/**
 * Wakes the screen, swipes up and types the saved PIN through the accessibility service.
 * Works only with a PIN lock, and only if the phone's lock screen exposes its keypad to accessibility.
 */
object PhoneUnlocker {
    private const val TAG = "PhoneUnlocker"

    /** Tools that need the screen to be unlocked before they can work. */
    private val SCREEN_TOOLS = setOf(
        "open_app", "open_website", "search_in_chrome", "search_and_play_youtube", "search_youtube",
        "youtube_accessibility_action", "send_whatsapp_message", "whatsapp_call", "call_contact",
        "builtin_chrome_search", "click_ui_element", "type_text", "scroll_screen", "press_key", "inspect_screen"
    )

    fun isLocked(ctx: Context): Boolean {
        return try {
            val km = ctx.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            km.isKeyguardLocked
        } catch (e: Exception) {
            false
        }
    }

    /** Called before every phone-control tool. Does nothing unless the user switched auto-unlock on. */
    fun ensureUnlockedFor(ctx: Context, toolName: String) {
        try {
            if (toolName !in SCREEN_TOOLS) return
            if (!PinVault.isAuto(ctx)) return
            if (!isLocked(ctx)) return
            Log.i(TAG, "Phone is locked, auto-unlocking before '$toolName'")
            unlock(ctx)
        } catch (e: Exception) {
            Log.e(TAG, "ensureUnlockedFor failed: ${e.message}")
        }
    }

    /** Blocking (takes a few seconds). Returns a short human-readable result. */
    fun unlock(ctx: Context): String {
        if (!isLocked(ctx)) return "The phone is already unlocked."
        val svc = JarvisAccessibilityService.instance
            ?: return "Jarvis accessibility is turned off, so I cannot touch the lock screen."
        val pin = PinVault.loadPin(ctx) ?: return "No PIN is saved yet."

        wakeScreen(ctx)
        sleep(800)
        for (attempt in 1..2) {
            swipeUp(svc, ctx)
            sleep(1000)
            if (!isLocked(ctx)) return "Unlocked."
            val typed = enterPin(svc, pin)
            sleep(1000)
            if (!isLocked(ctx)) return "Unlocked."
            if (!typed) Log.w(TAG, "attempt $attempt: keypad not found")
            sleep(600)
        }
        return if (isLocked(ctx)) {
            "I could not unlock the phone. Your lock screen may hide its keypad from accessibility, or it is not a PIN lock."
        } else {
            "Unlocked."
        }
    }

    fun lock(ctx: Context): String {
        val svc = JarvisAccessibilityService.instance ?: return "Jarvis accessibility is turned off."
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return "Locking the screen needs Android 9 or newer."
        return if (svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)) "Screen locked." else "Could not lock the screen."
    }

    // ------------------------------------------------------------------ internals
    private fun sleep(ms: Long) {
        try {
            Thread.sleep(ms)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    @Suppress("DEPRECATION")
    private fun wakeScreen(ctx: Context) {
        try {
            val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
            val wl = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                "jarvis:unlock"
            )
            wl.acquire(10_000L)
        } catch (e: Exception) {
            Log.w(TAG, "wakeScreen failed: ${e.message}")
        }
    }

    private fun swipeUp(svc: JarvisAccessibilityService, ctx: Context) {
        try {
            val dm = ctx.resources.displayMetrics
            val path = Path()
            path.moveTo(dm.widthPixels / 2f, dm.heightPixels * 0.85f)
            path.lineTo(dm.widthPixels / 2f, dm.heightPixels * 0.25f)
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0L, 350L))
                .build()
            svc.dispatchGesture(gesture, null, null)
        } catch (e: Exception) {
            Log.w(TAG, "swipeUp failed: ${e.message}")
        }
    }

    private fun roots(svc: JarvisAccessibilityService): List<AccessibilityNodeInfo> {
        val out = ArrayList<AccessibilityNodeInfo>()
        try {
            for (w in svc.windows) {
                val r = w.root
                if (r != null) out.add(r)
            }
        } catch (e: Exception) {
        }
        try {
            val active = svc.rootInActiveWindow
            if (active != null) out.add(active)
        } catch (e: Exception) {
        }
        return out
    }

    private fun enterPin(svc: JarvisAccessibilityService, pin: String): Boolean {
        for (ch in pin) {
            val node = findKey(svc, ch.toString()) ?: return false
            if (!press(svc, node)) return false
            sleep(220)
        }
        sleep(300)
        val enter = findEnter(svc)
        if (enter != null) press(svc, enter)
        return true
    }

    private fun findKey(svc: JarvisAccessibilityService, digit: String): AccessibilityNodeInfo? {
        for (root in roots(svc)) {
            val n = search(root, 0) { node ->
                val id = node.viewIdResourceName ?: ""
                val text = node.text?.toString()?.trim() ?: ""
                val desc = node.contentDescription?.toString()?.trim() ?: ""
                text == digit || desc == digit ||
                    id.endsWith("key$digit") || id.endsWith("key_$digit") || id.endsWith("digit_$digit") ||
                    id.endsWith("num$digit") || id.endsWith("button$digit")
            }
            if (n != null) return n
        }
        return null
    }

    private fun findEnter(svc: JarvisAccessibilityService): AccessibilityNodeInfo? {
        val words = listOf("enter", "ok", "done", "confirm", "submit", "unlock")
        for (root in roots(svc)) {
            val n = search(root, 0) { node ->
                val id = (node.viewIdResourceName ?: "").lowercase(Locale.US)
                val text = node.text?.toString()?.trim()?.lowercase(Locale.US) ?: ""
                val desc = node.contentDescription?.toString()?.trim()?.lowercase(Locale.US) ?: ""
                words.any { w -> text == w || desc == w } || id.endsWith("key_enter") || id.endsWith("enter") || id.endsWith("ok_button")
            }
            if (n != null) return n
        }
        return null
    }

    private fun search(node: AccessibilityNodeInfo?, depth: Int, matches: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (node == null || depth > 25) return null
        if (matches(node)) return node
        for (i in 0 until node.childCount) {
            val found = search(node.getChild(i), depth + 1, matches)
            if (found != null) return found
        }
        return null
    }

    private fun press(svc: JarvisAccessibilityService, node: AccessibilityNodeInfo): Boolean {
        // climb to the nearest clickable view (the digit label is often inside a clickable key)
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

    /** For diagnostics only: wakes the screen, swipes up, then lists every visible node. No PIN is typed. */
    fun scanLockScreen(ctx: Context): String {
        val svc = JarvisAccessibilityService.instance ?: return "Accessibility is not connected.\n"
        wakeScreen(ctx)
        sleep(800)
        swipeUp(svc, ctx)
        sleep(1500)
        val sb = StringBuilder()
        sb.append("Lock screen showing: ").append(isLocked(ctx)).append('\n')
        var lines = 0
        for (root in roots(svc)) {
            sb.append("== window root package: ").append(root.packageName).append('\n')
            lines = dumpNodes(root, 0, sb, lines)
            if (lines > 220) break
        }
        if (lines == 0) sb.append("No nodes visible: the lock screen hides its content from accessibility.\n")
        return sb.toString()
    }

    private fun dumpNodes(n: AccessibilityNodeInfo?, depth: Int, sb: StringBuilder, count: Int): Int {
        if (n == null || depth > 25 || count > 220) return count
        var c = count
        val id = n.viewIdResourceName ?: ""
        val text = if (n.isPassword) "***" else (n.text?.toString() ?: "")
        val desc = n.contentDescription?.toString() ?: ""
        if (id.isNotEmpty() || text.isNotEmpty() || desc.isNotEmpty() || n.isClickable) {
            val r = Rect()
            n.getBoundsInScreen(r)
            sb.append("  ".repeat(if (depth > 6) 6 else depth))
                .append(n.className).append(" id=").append(id.substringAfterLast('/'))
                .append(" t='").append(text.take(20)).append("' d='").append(desc.take(20)).append("'")
                .append(if (n.isClickable) " CLICK " else " ")
                .append(r.toShortString()).append('\n')
            c++
        }
        for (i in 0 until n.childCount) c = dumpNodes(n.getChild(i), depth + 1, sb, c)
        return c
    }

    // ------------------------------------------------------------------ voice tools
    private val TOOLS = setOf("unlock_phone", "lock_phone")

    fun handles(name: String): Boolean = name in TOOLS

    fun declarations(): List<JSONObject> {
        val list = ArrayList<JSONObject>()
        val phrase = JSONObject()
        phrase.put("type", "STRING")
        phrase.put("description", "The secret unlock phrase exactly as the user said it. Only needed if the user set one up; never ask the user to say their PIN.")
        val props = JSONObject()
        props.put("passphrase", phrase)
        val params = JSONObject()
        params.put("type", "OBJECT")
        params.put("properties", props)
        val unlock = JSONObject()
        unlock.put("name", "unlock_phone")
        unlock.put("description", "Unlocks the phone screen using the PIN saved on the device (the PIN is never spoken). Use when the user asks you to unlock the phone or when the phone is locked and a task needs the screen.")
        unlock.put("parameters", params)
        list.add(unlock)

        val lockParams = JSONObject()
        lockParams.put("type", "OBJECT")
        lockParams.put("properties", JSONObject())
        val lock = JSONObject()
        lock.put("name", "lock_phone")
        lock.put("description", "Locks the phone screen.")
        lock.put("parameters", lockParams)
        list.add(lock)
        return list
    }

    fun executeTool(ctx: Context, name: String, args: JSONObject, r: JSONObject) {
        if (name == "lock_phone") {
            val msg = lock(ctx)
            r.put("success", msg.startsWith("Screen locked"))
            r.put("message", msg)
            return
        }
        // unlock_phone
        if (!PinVault.isEnabled(ctx)) {
            r.put("success", false)
            r.put("message", "Voice unlock is not turned on. Tell the user to open Settings, then Phone unlock, save the PIN and switch it on.")
            return
        }
        if (PinVault.isLockedOut(ctx)) {
            r.put("success", false)
            r.put("message", "Too many wrong secret phrases. Voice unlock is paused for 10 minutes. Do not try again.")
            return
        }
        if (PinVault.hasPhrase(ctx) && !PinVault.checkPhrase(ctx, args.optString("passphrase", ""))) {
            PinVault.registerFailure(ctx)
            r.put("success", false)
            r.put("message", "The secret phrase was missing or wrong. The phone stays locked. Ask the user to say the secret phrase.")
            return
        }
        PinVault.resetFailures(ctx)
        val msg = unlock(ctx)
        r.put("success", msg.startsWith("Unlocked") || msg.startsWith("The phone is already"))
        r.put("message", msg)
    }
}
