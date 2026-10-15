package com.jarvis.assistant.unlock

import android.app.KeyguardManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.TypedValue
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.R
import com.jarvis.assistant.automation.accessibility.JarvisAccessibilityService
import com.jarvis.assistant.background.JarvisConversationService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Shows exactly what Jarvis can and cannot see on this phone, so problems can be pinned down fast. */
class DiagnosticsActivity : AppCompatActivity() {

    private lateinit var output: TextView
    private var lastReport = ""
    private var cText = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cText = attr(R.attr.themeTextPrimary)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(14), dp(40), dp(14), dp(14))

        val title = TextView(this)
        title.text = "Diagnostics"
        title.textSize = 22f
        title.setTextColor(cText)
        title.setTypeface(title.typeface, Typeface.BOLD)
        root.addView(title)

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.addView(btn("Run again") { runReport(null) })
        row.addView(btn("Copy") { copy() })
        root.addView(row)
        root.addView(btn("Typing test (starts in 8 s: open any app and tap a text box)") { typingTest() })
        root.addView(btn("Lock-screen scan (locks the phone, then unlock it yourself)") { lockScan() })

        output = TextView(this)
        output.setTextColor(cText)
        output.textSize = 11f
        output.typeface = Typeface.MONOSPACE
        output.setTextIsSelectable(true)
        output.text = "Collecting..."
        val scroll = ScrollView(this)
        scroll.addView(output)
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        runReport(null)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun attr(id: Int): Int {
        val tv = TypedValue()
        theme.resolveAttribute(id, tv, true)
        return tv.data
    }

    private fun btn(label: String, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = label
        b.isAllCaps = false
        b.setOnClickListener { onClick() }
        return b
    }

    private fun copy() {
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("Jarvis diagnostics", lastReport))
            Toast.makeText(this, "Copied. Paste it in the chat.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Could not copy. Long-press the text to select it.", Toast.LENGTH_LONG).show()
        }
    }

    private fun runReport(extra: String?) {
        output.text = "Collecting..."
        Thread {
            val text = try {
                buildReport() + (if (extra != null) "\n" + extra else "")
            } catch (e: Exception) {
                "Report failed: ${e.message}"
            }
            runOnUiThread {
                lastReport = text
                output.text = text
            }
        }.start()
    }

    private fun typingTest() {
        Toast.makeText(this, "Switch to any app and tap inside a text box now. Test runs in 8 seconds.", Toast.LENGTH_LONG).show()
        Thread {
            try {
                Thread.sleep(8000)
            } catch (e: InterruptedException) {
            }
            val sb = StringBuilder()
            sb.append("---- TYPING TEST ----\n")
            val svc = JarvisAccessibilityService.instance
            if (svc == null) {
                sb.append("Accessibility service is NOT connected, so typing is impossible.\n")
            } else {
                val root = try { svc.rootInActiveWindow } catch (e: Exception) { null }
                sb.append("Active window package: ").append(root?.packageName).append('\n')
                val focused = try { root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) } catch (e: Exception) { null }
                sb.append("Focused input: ")
                    .append(if (focused == null) "none found" else "${focused.className} editable=${focused.isEditable}")
                    .append('\n')
                sb.append("Editable fields visible: ").append(countEditable(root, 0)).append('\n')
                val ok = try { svc.typeText("Jarvis test") } catch (e: Exception) { false }
                sb.append("typeText result: ").append(if (ok) "SUCCESS (text was typed)" else "FAILED").append('\n')
            }
            runOnUiThread { runReport(sb.toString()) }
        }.start()
    }

    private fun lockScan() {
        Toast.makeText(this, "The phone will lock, wake and be scanned. Do not touch it for 12 seconds, then unlock it yourself.", Toast.LENGTH_LONG).show()
        Thread {
            val sb = StringBuilder("---- LOCK SCREEN SCAN ----\n")
            sb.append("lock: ").append(PhoneUnlocker.lock(applicationContext)).append('\n')
            try {
                Thread.sleep(3500)
            } catch (e: InterruptedException) {
            }
            sb.append(PhoneUnlocker.scanLockScreen(applicationContext))
            runOnUiThread { runReport(sb.toString()) }
        }.start()
    }

    private fun countNodes(n: AccessibilityNodeInfo?, depth: Int): Int {
        if (n == null || depth > 25) return 0
        var c = 1
        for (i in 0 until n.childCount) {
            c += countNodes(n.getChild(i), depth + 1)
            if (c > 4000) return c
        }
        return c
    }

    private fun countEditable(n: AccessibilityNodeInfo?, depth: Int): Int {
        if (n == null || depth > 25) return 0
        var c = if (n.isEditable) 1 else 0
        for (i in 0 until n.childCount) c += countEditable(n.getChild(i), depth + 1)
        return c
    }

    private fun buildReport(): String {
        val sb = StringBuilder()
        fun line(k: String, v: Any?) {
            sb.append(k).append(": ").append(v).append('\n')
        }
        line("Time", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
        line("Phone", Build.MANUFACTURER + " " + Build.MODEL + ", Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")")
        line("ROM build", Build.DISPLAY)

        sb.append("\n[Accessibility]\n")
        val svc = JarvisAccessibilityService.instance
        line("Service connected inside Jarvis", svc != null)
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        line("Enabled in Android settings", enabled.contains(packageName))
        line("Enabled services (first 250 chars)", enabled.take(250))
        if (svc != null) {
            line("Service flags", "0x" + Integer.toHexString(svc.serviceInfo?.flags ?: -1))
            val wins = try { svc.windows } catch (e: Exception) { null }
            line("Windows Jarvis can see", wins?.size ?: "error")
            if (wins != null) {
                for (w in wins.take(8)) {
                    val r = try { w.root } catch (e: Exception) { null }
                    line(
                        "  window",
                        "type=" + w.type + " layer=" + w.layer + " active=" + w.isActive + " pkg=" + r?.packageName +
                            " nodes=" + countNodes(r, 0) + " editable=" + countEditable(r, 0)
                    )
                }
            }
            val ar = try { svc.rootInActiveWindow } catch (e: Exception) { null }
            line("Active window package", ar?.packageName)
            line("Active window nodes", countNodes(ar, 0))
        }

        sb.append("\n[Permissions]\n")
        line("Draw over other apps", Settings.canDrawOverlays(this))
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        line("Battery optimisation ignored", pm.isIgnoringBatteryOptimizations(packageName))
        line("Screen on (interactive)", pm.isInteractive)
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        line("Lock screen showing", km.isKeyguardLocked)
        line("Phone has a PIN/pattern/password", km.isDeviceSecure)

        sb.append("\n[Jarvis]\n")
        line("Voice session on", JarvisConversationService.isSessionOn.value)
        line("Connection", JarvisConversationService.connectionStatus.value)
        line("Conversation state", JarvisConversationService.conversationState.value)
        line("Last error", JarvisConversationService.liveError.value)
        try {
            val prefs = JarvisApp.instance.preferences
            line("Owner voice check on", prefs.isOwnerVoiceEnabled)
            line("Owner voice profile saved", prefs.hasOwnerVoiceProfile())
            line("Persona", prefs.personality)
            line("Voice", prefs.voice)
        } catch (e: Exception) {
            line("Preferences", "unavailable: " + e.message)
        }
        line("PIN saved / voice unlock / auto-unlock", PinVault.hasPin(this).toString() + " / " + PinVault.isEnabled(this) + " / " + PinVault.isAuto(this))
        return sb.toString()
    }
}
