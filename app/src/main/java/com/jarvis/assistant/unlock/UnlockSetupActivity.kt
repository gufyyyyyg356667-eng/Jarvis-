package com.jarvis.assistant.unlock

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.jarvis.assistant.R
import com.jarvis.assistant.automation.accessibility.JarvisAccessibilityService

/** Settings screen for voice unlock. The PIN is stored encrypted on this phone only. */
class UnlockSetupActivity : AppCompatActivity() {

    private lateinit var content: LinearLayout
    private var cText = 0
    private var cMuted = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cText = attr(R.attr.themeTextPrimary)
        cMuted = attr(R.attr.themeTextMuted)
        val scroll = ScrollView(this)
        content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(dp(18), dp(44), dp(18), dp(40))
        scroll.addView(content)
        setContentView(scroll)
        build()
    }

    override fun onResume() {
        super.onResume()
        if (::content.isInitialized) build()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun attr(id: Int): Int {
        val tv = TypedValue()
        theme.resolveAttribute(id, tv, true)
        return tv.data
    }

    private fun text(msg: String, sp: Float, color: Int, bold: Boolean = false): TextView {
        val t = TextView(this)
        t.text = msg
        t.textSize = sp
        t.setTextColor(color)
        if (bold) t.setTypeface(t.typeface, android.graphics.Typeface.BOLD)
        t.setPadding(0, dp(6), 0, dp(2))
        return t
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()

    private fun build() {
        content.removeAllViews()
        content.addView(text("Phone unlock", 24f, cText, true))
        content.addView(
            text(
                "Lets Jarvis unlock your phone for you. Your PIN is encrypted with a key that never leaves this phone, " +
                    "and it is never sent to the internet. You never have to say the PIN aloud.",
                13f, cMuted
            )
        )

        val accessOn = JarvisAccessibilityService.instance != null
        content.addView(text(if (accessOn) "Accessibility: ON" else "Accessibility: OFF (turn on Jarvis in Accessibility settings)", 13f, cText, true))
        if (!accessOn) {
            content.addView(button("Open Accessibility settings") {
                try {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                } catch (e: Exception) {
                    toast("Open Android Settings > Accessibility")
                }
            })
        }
        content.addView(text("PIN saved: " + (if (PinVault.hasPin(this)) "yes" else "no"), 13f, cText, true))

        content.addView(text("PIN (digits only, 4 to 16)", 13f, cMuted))
        val pin = EditText(this)
        pin.hint = "Your phone lock PIN"
        pin.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        content.addView(pin)

        content.addView(text("Secret phrase (optional, recommended)", 13f, cMuted))
        val phrase = EditText(this)
        phrase.hint = "A word or sentence only you know"
        phrase.inputType = InputType.TYPE_CLASS_TEXT
        content.addView(phrase)
        content.addView(text("If you set a phrase, Jarvis unlocks only after you say it. Leaving this box empty keeps the current phrase.", 12f, cMuted))
        if (PinVault.hasPhrase(this)) {
            content.addView(button("Remove the secret phrase") {
                PinVault.savePhrase(this, "")
                toast("Phrase removed")
                build()
            })
        }

        content.addView(button("Save") {
            val p = pin.text.toString().trim()
            val ph = phrase.text.toString()
            if (p.isNotEmpty()) {
                if (p.length < 4 || p.length > 16 || !p.all { it.isDigit() }) {
                    toast("The PIN must be 4 to 16 digits.")
                    return@button
                }
                if (!PinVault.savePin(this, p)) {
                    toast("Could not save the PIN securely on this phone.")
                    return@button
                }
            }
            if (ph.isNotBlank()) PinVault.savePhrase(this, ph)
            toast("Saved")
            build()
        })

        val enable = SwitchCompat(this)
        enable.text = "Allow voice unlock (\"unlock my phone\")"
        enable.setTextColor(cText)
        enable.isChecked = PinVault.isEnabled(this)
        enable.setOnCheckedChangeListener { _, on ->
            if (on && !PinVault.hasPin(this)) {
                enable.isChecked = false
                toast("Save a PIN first.")
            } else {
                PinVault.setEnabled(this, on)
            }
        }
        content.addView(enable)

        val auto = SwitchCompat(this)
        auto.text = "Unlock automatically before phone tasks"
        auto.setTextColor(cText)
        auto.isChecked = PinVault.isAuto(this)
        auto.setOnCheckedChangeListener { _, on ->
            if (on && !PinVault.isEnabled(this)) {
                auto.isChecked = false
                toast("Turn on voice unlock first.")
            } else {
                PinVault.setAuto(this, on)
            }
        }
        content.addView(auto)
        content.addView(text("With this on, if your phone is locked and you ask for something that needs the screen (open an app, WhatsApp, YouTube), Jarvis unlocks first. Anyone whose voice Jarvis accepts can do the same, so keep Owner Voice protection on.", 12f, cMuted))

        content.addView(text("Not working? Run the checker and send me the result", 15f, cText, true))
        content.addView(button("Run diagnostics") {
            startActivity(Intent(this, DiagnosticsActivity::class.java))
        })

        content.addView(text("Test", 15f, cText, true))
        content.addView(button("Lock the screen now, then unlock") {
            if (!PinVault.hasPin(this)) {
                toast("Save a PIN first.")
                return@button
            }
            toast("Locking now. Watch the screen for about 10 seconds.")
            Thread {
                val first = PhoneUnlocker.lock(applicationContext)
                try {
                    Thread.sleep(3500)
                } catch (e: InterruptedException) {
                }
                val second = if (first.startsWith("Screen locked")) PhoneUnlocker.unlock(applicationContext) else first
                runOnUiThread { toast(second) }
            }.start()
        })

        content.addView(button("Delete saved PIN and phrase") {
            PinVault.clearPin(this)
            PinVault.savePhrase(this, "")
            toast("Deleted")
            build()
        })

        content.addView(text("Good to know", 15f, cText, true))
        content.addView(
            text(
                "- Works only with a PIN lock. Pattern, fingerprint-only and face-only locks cannot be unlocked this way.\n" +
                    "- Some phones (often Xiaomi/Redmi/Poco) hide the lock-screen keypad from accessibility. If the test fails, that is the reason.\n" +
                    "- Anyone who can open this app can see that a PIN is saved, but not the PIN itself.\n" +
                    "- If you lose the phone, delete the PIN here or remove Jarvis from Accessibility.",
                12f, cMuted
            )
        )
    }

    private fun button(label: String, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = label
        b.isAllCaps = false
        b.setOnClickListener { onClick() }
        return b
    }
}
