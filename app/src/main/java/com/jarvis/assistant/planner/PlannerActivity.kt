package com.jarvis.assistant.planner

import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.jarvis.assistant.R
import java.util.Calendar

/** Simple screen to see and manage everything the voice assistant planned. Built in code (no XML). */
class PlannerActivity : AppCompatActivity() {

    private lateinit var content: LinearLayout
    private var cText = 0
    private var cMuted = 0
    private var cAccent = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cText = attr(R.attr.themeTextPrimary)
        cMuted = attr(R.attr.themeTextMuted)
        cAccent = attr(R.attr.themeAccent)

        val scroll = ScrollView(this)
        scroll.isFillViewport = true
        content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(dp(18), dp(44), dp(18), dp(40))
        scroll.addView(content)
        setContentView(scroll)
        PlannerNotifier.ensureChannels(this)
        refresh()
    }

    override fun onResume() {
        super.onResume()
        if (::content.isInitialized) refresh()
    }

    // ------------------------------------------------------------------ helpers
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun attr(id: Int): Int {
        val tv = TypedValue()
        theme.resolveAttribute(id, tv, true)
        return tv.data
    }

    private fun label(text: String, sizeSp: Float, color: Int, bold: Boolean): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = sizeSp
        t.setTextColor(color)
        if (bold) t.setTypeface(t.typeface, android.graphics.Typeface.BOLD)
        return t
    }

    private fun section(title: String) {
        val t = label(title.uppercase(), 12f, cMuted, true)
        t.setPadding(0, dp(22), 0, dp(6))
        content.addView(t)
    }

    private fun smallButton(text: String, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = text
        b.isAllCaps = false
        b.setOnClickListener { onClick() }
        return b
    }

    private fun row(left: String, sub: String, buttonText: String, onClick: () -> Unit) {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.setPadding(0, dp(4), 0, dp(4))
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.addView(label(left, 15f, cText, false))
        if (sub.isNotEmpty()) col.addView(label(sub, 12f, cMuted, false))
        r.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        r.addView(smallButton(buttonText, onClick))
        content.addView(r)
    }

    private fun note(text: String) {
        val t = label(text, 12f, cMuted, false)
        t.setPadding(0, dp(2), 0, dp(2))
        content.addView(t)
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    // ------------------------------------------------------------------ screen
    private fun refresh() {
        content.removeAllViews()
        content.addView(label("Planner & Reminders", 24f, cText, true))
        note("Everything you ask Jarvis to remember by voice appears here. You can also add things yourself.")

        val buttons = LinearLayout(this)
        buttons.orientation = LinearLayout.HORIZONTAL
        buttons.setPadding(0, dp(12), 0, 0)
        buttons.addView(smallButton("+ Reminder") { addReminderDialog() })
        buttons.addView(smallButton("+ Alarm") { addAlarmDialog() })
        buttons.addView(smallButton("+ To-do") { addTodoDialog() })
        content.addView(buttons)

        // ---- upcoming
        section("Upcoming")
        val items = PlannerStore.items(this)
            .filter { it.enabled && it.kind != Kind.RULE && it.kind != Kind.BRIEF && it.kind != Kind.BIRTHDAY && !it.id.startsWith("health_") }
            .sortedBy { if (it.triggerAt > 0L) it.triggerAt else Long.MAX_VALUE }
        if (items.isEmpty()) note("Nothing planned yet. Try saying: \"Set an alarm for 6 AM\" or \"Remind me in 20 minutes to call mom\".")
        for (i in items) {
            val whenText = if (i.kind == Kind.PLACE) {
                "When you " + (if (i.extra2 == "leave") "leave " else "reach ") + i.extra1
            } else {
                PlannerTime.describe(i.triggerAt) + (if (i.repeat != "none") "  (repeats)" else "")
            }
            row(i.title.ifBlank { i.kind }, i.kind.replaceFirstChar { it.uppercase() } + " - " + whenText, "Delete") {
                PlannerScheduler.delete(this, i.id)
                refresh()
            }
        }

        // ---- to-do
        section("To-do")
        val todos = PlannerStore.todos(this)
        if (todos.isEmpty()) note("No tasks. Say: \"Add buy milk to my to-do list\".")
        for (t in todos) {
            val line = LinearLayout(this)
            line.orientation = LinearLayout.HORIZONTAL
            line.gravity = Gravity.CENTER_VERTICAL
            val cb = CheckBox(this)
            cb.isChecked = t.done
            cb.text = t.text
            cb.setTextColor(cText)
            cb.setOnCheckedChangeListener { _, checked ->
                val all = PlannerStore.todos(this)
                val idx = all.indexOfFirst { it.id == t.id }
                if (idx >= 0) {
                    all[idx] = all[idx].copy(done = checked)
                    PlannerStore.saveTodos(this, all)
                }
            }
            line.addView(cb, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            line.addView(smallButton("Delete") {
                val all = PlannerStore.todos(this)
                all.removeAll { it.id == t.id }
                PlannerStore.saveTodos(this, all)
                refresh()
            })
            content.addView(line)
        }

        // ---- daily helpers
        section("Daily helpers")
        toggleRow("Daily brief (weather, plans, to-do)", PlannerStore.find(this, "sys_brief") != null) { on ->
            if (on) {
                val base = PlannerItem("sys_brief", Kind.BRIEF, "Daily brief", 0L, "daily", 0, 0, 7 * 60 + 30)
                PlannerScheduler.add(this, base.copy(triggerAt = PlannerTime.nextAt(7 * 60 + 30, System.currentTimeMillis())))
                toast("Daily brief at 07:30")
            } else PlannerScheduler.delete(this, "sys_brief")
        }
        toggleRow("Birthday reminders (from contacts)", PlannerStore.find(this, "sys_birthday") != null) { on ->
            if (on) {
                val base = PlannerItem("sys_birthday", Kind.BIRTHDAY, "Birthday check", 0L, "daily", 0, 0, 8 * 60)
                PlannerScheduler.add(this, base.copy(triggerAt = PlannerTime.nextAt(8 * 60, System.currentTimeMillis())))
                toast("Birthday check every day at 08:00")
            } else PlannerScheduler.delete(this, "sys_birthday")
        }
        healthToggle("Drink water (every 90 min)", "water", "Time to drink some water.", 90)
        healthToggle("Short break (every 60 min)", "break", "Take a short break and rest your eyes.", 60)
        healthToggle("Stretch (every 90 min)", "stretch", "Time to stand up and stretch.", 90)
        note("Tip: say \"Change the brief time to 6:45\" to Jarvis to set a different time.")

        // ---- rules
        section("Automation rules")
        val rules = PlannerStore.rules(this)
        if (rules.isEmpty()) note("No rules. Say: \"When I plug in the charger, say battery charging\".")
        for (r in rules) {
            val trig = r.trigger.replace('_', ' ') + (if (r.timeMin >= 0) " at " + PlannerTime.clock(r.timeMin) else "")
            row("When $trig", "Do: " + r.action.replace('_', ' ') + (if (r.text.isNotBlank()) " - " + r.text else ""), "Delete") {
                val all = PlannerStore.rules(this)
                all.removeAll { it.id == r.id }
                PlannerStore.saveRules(this, all)
                PlannerScheduler.delete(this, "rule_" + r.id)
                refresh()
            }
        }

        // ---- places
        section("Saved places")
        val places = PlannerStore.places(this)
        if (places.isEmpty()) note("No places. Stand at a place and say: \"Save this place as home\".")
        for (p in places) {
            row(p.name, "Used for location reminders", "Delete") {
                val all = PlannerStore.places(this)
                all.removeAll { it.name == p.name }
                PlannerStore.savePlaces(this, all)
                refresh()
            }
        }

        // ---- reliability
        section("Make reminders reliable")
        row("Battery: allow in background", "Needed on Xiaomi, Redmi, Poco, Oppo, Vivo and Samsung phones", "Open") { openBatterySettings() }
        row("Autostart / app launch", "On Xiaomi/Redmi: allow Autostart for Jarvis", "Open") { openAppDetails() }
        row("Location: allow all the time", "Only for 'remind me when I reach a place'", "Open") { openAppDetails() }
        row("Notifications", "Allow notifications, sound and pop-ups", "Open") { openNotificationSettings() }
        note("Phone locked, or Do Not Disturb on? Alarms still ring. Scheduled WhatsApp messages need the phone unlocked.")
    }

    private fun toggleRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
        val sw = SwitchCompat(this)
        sw.text = text
        sw.setTextColor(cText)
        sw.isChecked = checked
        sw.setPadding(0, dp(6), 0, dp(6))
        sw.setOnCheckedChangeListener { _, on ->
            try {
                onChange(on)
            } catch (e: Exception) {
                toast("Could not change this setting")
            }
        }
        content.addView(sw)
    }

    private fun healthToggle(text: String, type: String, title: String, every: Int) {
        val id = "health_$type"
        toggleRow(text, PlannerStore.find(this, id) != null) { on ->
            if (on) {
                val first = System.currentTimeMillis() + every * 60_000L
                val item = PlannerItem(id, Kind.HEALTH, title, first, "interval", 0, every, -1, 8 * 60, 22 * 60)
                PlannerScheduler.add(this, item)
                toast("Next reminder: " + PlannerTime.describe(first))
            } else PlannerScheduler.delete(this, id)
        }
    }

    // ------------------------------------------------------------------ dialogs
    private fun askText(title: String, hint: String, onOk: (String) -> Unit) {
        val input = EditText(this)
        input.hint = hint
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotEmpty()) onOk(text)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun askTime(onPicked: (Int) -> Unit) {
        val c = Calendar.getInstance()
        TimePickerDialog(this, { _, h, m -> onPicked(h * 60 + m) }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), false).show()
    }

    private fun addReminderDialog() {
        askText("New reminder", "What should I remind you about?") { text ->
            askTime { minutes ->
                val at = PlannerTime.nextAt(minutes, System.currentTimeMillis())
                PlannerScheduler.add(this, PlannerItem(newPlannerId(), Kind.REMINDER, text, at))
                toast("Reminder set for " + PlannerTime.describe(at))
                refresh()
            }
        }
    }

    private fun addAlarmDialog() {
        askTime { minutes ->
            val at = PlannerTime.nextAt(minutes, System.currentTimeMillis())
            PlannerScheduler.add(this, PlannerItem(newPlannerId(), Kind.ALARM, "Alarm", at))
            toast("Alarm set for " + PlannerTime.describe(at))
            refresh()
        }
    }

    private fun addTodoDialog() {
        askText("New to-do", "Task") { text ->
            val all = PlannerStore.todos(this)
            all.add(TodoItem(newPlannerId(), text, false))
            PlannerStore.saveTodos(this, all)
            refresh()
        }
    }

    // ------------------------------------------------------------------ settings shortcuts
    private fun openBatterySettings() {
        try {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (e2: Exception) {
                openAppDetails()
            }
        }
    }

    private fun openAppDetails() {
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        } catch (e: Exception) {
            toast("Open Android Settings > Apps > Jarvis")
        }
    }

    private fun openNotificationSettings() {
        try {
            val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            i.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            startActivity(i)
        } catch (e: Exception) {
            openAppDetails()
        }
    }
}
