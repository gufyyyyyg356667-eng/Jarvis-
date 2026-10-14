package com.jarvis.assistant.planner

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Voice tools for Gemini Live: alarms, timers, reminders, places, daily brief, automation rules,
 * scheduled WhatsApp messages, to-do list, birthdays and health reminders.
 */
object PlannerTools {

    private val NAMES = setOf(
        "set_alarm", "set_timer", "stopwatch", "set_reminder", "save_place", "set_place_reminder",
        "morning_brief", "automation_rule", "schedule_whatsapp_message", "todo_list", "birthdays",
        "health_reminder", "list_planner", "cancel_planner_item", "unlock_phone", "lock_phone"
    )

    fun handles(name: String): Boolean = name in NAMES

    // ------------------------------------------------------------------ declarations

    private fun prop(type: String, desc: String): JSONObject {
        val o = JSONObject()
        o.put("type", type)
        o.put("description", desc)
        return o
    }

    private fun decl(name: String, desc: String, props: List<Pair<String, JSONObject>>, required: List<String>): JSONObject {
        val p = JSONObject()
        for (pair in props) p.put(pair.first, pair.second)
        val params = JSONObject()
        params.put("type", "OBJECT")
        params.put("properties", p)
        if (required.isNotEmpty()) {
            val req = JSONArray()
            for (r in required) req.put(r)
            params.put("required", req)
        }
        val o = JSONObject()
        o.put("name", name)
        o.put("description", desc)
        o.put("parameters", params)
        return o
    }

    private const val TIME_HELP =
        "You do NOT know the current date or time. For relative times (\"in 20 minutes\") use in_minutes. " +
            "For clock times use time_of_day in 24-hour HH:mm (7 pm = 19:00) and date_offset_days (0 = today, 1 = tomorrow, 2 = day after)."

    fun declarations(): List<JSONObject> {
        val timeProps = listOf(
            Pair("time_of_day", prop("STRING", "Clock time in 24-hour HH:mm, e.g. 06:30 or 19:00.")),
            Pair("in_minutes", prop("NUMBER", "Minutes from now, e.g. 20 for 'in twenty minutes'.")),
            Pair("date_offset_days", prop("INTEGER", "0 = today, 1 = tomorrow, 2 = the day after tomorrow."))
        )
        val list = ArrayList<JSONObject>()

        list.add(decl("set_alarm", "Sets a phone alarm that rings loudly. $TIME_HELP",
            timeProps + listOf(
                Pair("label", prop("STRING", "Optional label for the alarm.")),
                Pair("repeat_days", prop("STRING", "Optional repeat: 'daily', 'weekdays', 'weekend' or day names like 'mon,wed,fri'. Omit for one time."))
            ), emptyList()))

        list.add(decl("set_timer", "Starts a countdown timer that rings when it finishes (e.g. 'set a 10 minute timer').",
            listOf(
                Pair("minutes", prop("NUMBER", "Timer length in minutes (can be 0 if only seconds are given).")),
                Pair("seconds", prop("NUMBER", "Extra seconds.")),
                Pair("label", prop("STRING", "Optional label, e.g. 'tea'."))
            ), emptyList()))

        list.add(decl("stopwatch", "Controls a stopwatch: start, stop, status (read elapsed time) or reset.",
            listOf(Pair("action", prop("STRING", "One of: start, stop, status, reset."))), listOf("action")))

        list.add(decl("set_reminder", "Creates a reminder that notifies and speaks at the chosen time. Can repeat. $TIME_HELP",
            timeProps + listOf(
                Pair("text", prop("STRING", "What to remind the user about.")),
                Pair("repeat", prop("STRING", "One of: none, daily, weekly, every_n_minutes. Default none.")),
                Pair("repeat_days", prop("STRING", "For weekly/daily repeats: 'weekdays', 'weekend' or day names like 'mon,thu'.")),
                Pair("interval_minutes", prop("NUMBER", "For every_n_minutes repeats, the interval in minutes."))
            ), listOf("text")))

        list.add(decl("save_place", "Saves the user's CURRENT location under a name (e.g. 'home', 'office') for location reminders.",
            listOf(Pair("name", prop("STRING", "Name of the place."))), listOf("name")))

        list.add(decl("set_place_reminder", "Reminds the user when they arrive at or leave a saved place (e.g. 'remind me to buy milk when I reach home'). The place must be saved first with save_place.",
            listOf(
                Pair("text", prop("STRING", "What to remind about.")),
                Pair("place_name", prop("STRING", "Saved place name.")),
                Pair("event", prop("STRING", "'arrive' (default) or 'leave'."))
            ), listOf("text", "place_name")))

        list.add(decl("morning_brief", "Daily brief with date, weather, today's reminders, to-dos and birthdays. action: 'enable' (daily at time_of_day, default 07:30), 'disable', or 'read_now' (returns the brief text so you can read it aloud).",
            listOf(
                Pair("action", prop("STRING", "enable, disable or read_now.")),
                Pair("time_of_day", prop("STRING", "24-hour HH:mm for the daily brief."))
            ), listOf("action")))

        list.add(decl("automation_rule", "Creates 'when X happens, do Y' rules. trigger: charger_connected, charger_disconnected, battery_low or daily_time. do_action: notify, speak, ringer_normal, ringer_vibrate or ringer_silent. action: add, remove or list.",
            listOf(
                Pair("action", prop("STRING", "add, remove or list.")),
                Pair("trigger", prop("STRING", "charger_connected, charger_disconnected, battery_low or daily_time.")),
                Pair("time_of_day", prop("STRING", "24-hour HH:mm, only for daily_time.")),
                Pair("do_action", prop("STRING", "notify, speak, ringer_normal, ringer_vibrate or ringer_silent.")),
                Pair("text", prop("STRING", "Message to show or speak.")),
                Pair("rule_id", prop("STRING", "Rule id, for remove."))
            ), listOf("action")))

        list.add(decl("schedule_whatsapp_message", "Schedules a WhatsApp text to be sent later (e.g. 'send Rahul good night at 11 pm'). The phone must be unlocked and Jarvis accessibility must be on at that time. $TIME_HELP",
            timeProps + listOf(
                Pair("recipient_name", prop("STRING", "Contact name.")),
                Pair("message", prop("STRING", "Message text."))
            ), listOf("recipient_name", "message")))

        list.add(decl("todo_list", "Manages the to-do list. action: add, list, complete, remove or clear_done. For complete/remove, text is part of the task name.",
            listOf(
                Pair("action", prop("STRING", "add, list, complete, remove or clear_done.")),
                Pair("text", prop("STRING", "Task text (or part of it)."))
            ), listOf("action")))

        list.add(decl("birthdays", "Birthday reminders from contacts. action: enable (notify every day at 08:00 on birthdays), disable, or check (who has a birthday today and in the next 7 days).",
            listOf(
                Pair("action", prop("STRING", "enable, disable or check.")),
                Pair("time_of_day", prop("STRING", "Optional 24-hour HH:mm for the daily birthday check."))
            ), listOf("action")))

        list.add(decl("health_reminder", "Recurring healthy-habit reminders: type water, break, stretch, sleep or custom. Repeats within active hours.",
            listOf(
                Pair("type", prop("STRING", "water, break, stretch, sleep or custom.")),
                Pair("enabled", prop("BOOLEAN", "true to turn on (default), false to turn off.")),
                Pair("interval_minutes", prop("NUMBER", "How often, e.g. 90. Not used for sleep.")),
                Pair("time_of_day", prop("STRING", "For sleep: bedtime reminder time, 24-hour HH:mm.")),
                Pair("active_from", prop("STRING", "Start of active hours HH:mm, default 08:00.")),
                Pair("active_to", prop("STRING", "End of active hours HH:mm, default 22:00.")),
                Pair("text", prop("STRING", "Message for type custom."))
            ), listOf("type")))

        list.add(decl("list_planner", "Lists upcoming alarms, timers, reminders, scheduled messages, to-dos, saved places and rules.",
            emptyList(), emptyList()))

        list.add(decl("cancel_planner_item", "Cancels/deletes an alarm, timer, reminder, scheduled message or health reminder. Match by words from its title, or by kind (alarm, timer, reminder, message, health). If several match and all is false, the tool returns the matches so you can ask the user which one.",
            listOf(
                Pair("query", prop("STRING", "Words from the title, e.g. 'tea' or 'medicine'.")),
                Pair("kind", prop("STRING", "Optional: alarm, timer, reminder, message, health, place.")),
                Pair("all", prop("BOOLEAN", "true to cancel every match."))
            ), emptyList()))

        list.addAll(com.jarvis.assistant.unlock.PhoneUnlocker.declarations())
        return list
    }

    // ------------------------------------------------------------------ execution

    fun execute(ctx: Context, name: String, args: JSONObject): JSONObject {
        val r = JSONObject()
        try {
            PlannerNotifier.ensureChannels(ctx)
            when (name) {
                "set_alarm" -> setAlarm(ctx, args, r)
                "set_timer" -> setTimer(ctx, args, r)
                "stopwatch" -> stopwatch(ctx, args, r)
                "set_reminder" -> setReminder(ctx, args, r)
                "save_place" -> savePlace(ctx, args, r)
                "set_place_reminder" -> setPlaceReminder(ctx, args, r)
                "morning_brief" -> morningBrief(ctx, args, r)
                "automation_rule" -> automationRule(ctx, args, r)
                "schedule_whatsapp_message" -> scheduleMessage(ctx, args, r)
                "todo_list" -> todo(ctx, args, r)
                "birthdays" -> birthdays(ctx, args, r)
                "health_reminder" -> health(ctx, args, r)
                "list_planner" -> listAll(ctx, r)
                "cancel_planner_item" -> cancelItem(ctx, args, r)
                "unlock_phone", "lock_phone" -> com.jarvis.assistant.unlock.PhoneUnlocker.executeTool(ctx, name, args, r)
                else -> fail(r, "Unknown planner tool: $name")
            }
        } catch (e: Exception) {
            fail(r, "Planner error: ${e.message}")
        }
        r.put("current_time", PlannerTime.describe(System.currentTimeMillis()))
        return r
    }

    private fun fail(r: JSONObject, msg: String) {
        r.put("success", false)
        r.put("message", msg)
    }

    private fun ok(r: JSONObject, msg: String) {
        r.put("success", true)
        r.put("message", msg)
    }

    /** Works out the target time from in_minutes / time_of_day / date_offset_days / date. Returns null if missing or in the past. */
    private fun resolveTime(args: JSONObject): Long? {
        val now = System.currentTimeMillis()
        val inMinutes = args.optDouble("in_minutes", 0.0)
        if (inMinutes > 0.0) return now + (inMinutes * 60_000.0).toLong()
        val minutes = PlannerTime.parseMinutes(args.optString("time_of_day", "")) ?: return null
        val dateStr = args.optString("date", "")
        if (dateStr.isNotBlank()) {
            try {
                val d = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dateStr)
                if (d != null) {
                    val at = PlannerTime.atTime(d.time, 0, minutes)
                    return if (at > now) at else null
                }
            } catch (e: Exception) {
            }
        }
        val offset = args.optInt("date_offset_days", 0)
        var at = PlannerTime.atTime(now, offset, minutes)
        if (at <= now) {
            if (offset == 0) at = PlannerTime.atTime(now, 1, minutes) else return null
        }
        return at
    }

    private const val NEED_TIME = "I need a time. Ask the user when (a clock time, or 'in N minutes')."

    private fun setAlarm(ctx: Context, a: JSONObject, r: JSONObject) {
        val label = a.optString("label", "").ifBlank { "Alarm" }
        val days = a.optString("repeat_days", "")
        val minutes = PlannerTime.parseMinutes(a.optString("time_of_day", ""))
        val repeating = days.isNotBlank() && minutes != null
        val item: PlannerItem
        if (repeating && minutes != null) {
            val base = PlannerItem(newPlannerId(), Kind.ALARM, label, 0L, "daily", PlannerTime.parseDays(days), 0, minutes)
            val next = PlannerTime.nextRepeat(base, System.currentTimeMillis())
            if (next <= 0L) return fail(r, "Could not work out the next alarm time.")
            item = base.copy(triggerAt = next)
        } else {
            val at = resolveTime(a) ?: return fail(r, NEED_TIME)
            item = PlannerItem(newPlannerId(), Kind.ALARM, label, at)
        }
        PlannerScheduler.add(ctx, item)
        ok(r, "Alarm set for ${PlannerTime.describe(item.triggerAt)}" + (if (repeating) " and it repeats ($days)." else "."))
        r.put("id", item.id)
    }

    private fun setTimer(ctx: Context, a: JSONObject, r: JSONObject) {
        val total = (a.optDouble("minutes", 0.0) * 60.0 + a.optDouble("seconds", 0.0)).toLong()
        if (total <= 0L) return fail(r, "I need a timer length. Ask the user how long.")
        val label = a.optString("label", "")
        val item = PlannerItem(newPlannerId(), Kind.TIMER, if (label.isBlank()) "Timer" else "Timer $label", System.currentTimeMillis() + total * 1000L)
        PlannerScheduler.add(ctx, item)
        ok(r, "Timer started for ${PlannerTime.durationText(total)}.")
        r.put("id", item.id)
    }

    private fun stopwatch(ctx: Context, a: JSONObject, r: JSONObject) {
        val now = System.currentTimeMillis()
        var startedAt = PlannerStore.getLong(ctx, "sw_start")
        var elapsed = PlannerStore.getLong(ctx, "sw_elapsed")
        when (a.optString("action", "status").lowercase(Locale.US)) {
            "start" -> {
                if (startedAt > 0L) return ok(r, "The stopwatch is already running.")
                startedAt = now
                PlannerStore.putLong(ctx, "sw_start", startedAt)
                ok(r, "Stopwatch started.")
            }
            "stop" -> {
                if (startedAt > 0L) {
                    elapsed += now - startedAt
                    PlannerStore.putLong(ctx, "sw_elapsed", elapsed)
                    PlannerStore.putLong(ctx, "sw_start", 0L)
                    ok(r, "Stopwatch stopped at ${PlannerTime.durationText(elapsed / 1000L)}.")
                } else {
                    ok(r, "The stopwatch is not running. Total ${PlannerTime.durationText(elapsed / 1000L)}.")
                }
            }
            "reset" -> {
                PlannerStore.putLong(ctx, "sw_start", 0L)
                PlannerStore.putLong(ctx, "sw_elapsed", 0L)
                ok(r, "Stopwatch reset.")
            }
            else -> {
                val total = elapsed + (if (startedAt > 0L) now - startedAt else 0L)
                ok(r, (if (startedAt > 0L) "Running: " else "Stopped: ") + PlannerTime.durationText(total / 1000L))
            }
        }
    }

    private fun setReminder(ctx: Context, a: JSONObject, r: JSONObject) {
        val text = a.optString("text", "").trim()
        if (text.isEmpty()) return fail(r, "What should I remind the user about?")
        val repeat = a.optString("repeat", "none").lowercase(Locale.US)
        val days = PlannerTime.parseDays(a.optString("repeat_days", ""))
        val item: PlannerItem
        if (repeat == "every_n_minutes" || repeat == "interval") {
            val every = a.optDouble("interval_minutes", 0.0).toInt()
            if (every <= 0) return fail(r, "I need the interval in minutes.")
            item = PlannerItem(newPlannerId(), Kind.REMINDER, text, System.currentTimeMillis() + every * 60_000L, "interval", 0, every)
        } else if (repeat == "daily" || repeat == "weekly") {
            val minutes = PlannerTime.parseMinutes(a.optString("time_of_day", "")) ?: return fail(r, NEED_TIME)
            var mask = days
            if (repeat == "weekly" && mask == 0) {
                val cal = Calendar.getInstance()
                cal.timeInMillis = resolveTime(a) ?: PlannerTime.nextAt(minutes, System.currentTimeMillis())
                mask = 1 shl (cal.get(Calendar.DAY_OF_WEEK) - 1)
            }
            val base = PlannerItem(newPlannerId(), Kind.REMINDER, text, 0L, repeat, mask, 0, minutes)
            val next = PlannerTime.nextRepeat(base, System.currentTimeMillis())
            if (next <= 0L) return fail(r, "Could not work out the next time.")
            item = base.copy(triggerAt = next)
        } else {
            val at = resolveTime(a) ?: return fail(r, NEED_TIME)
            item = PlannerItem(newPlannerId(), Kind.REMINDER, text, at)
        }
        PlannerScheduler.add(ctx, item)
        ok(r, "Reminder set for ${PlannerTime.describe(item.triggerAt)}" + (if (item.repeat != "none") " (repeats ${item.repeat})." else "."))
        r.put("id", item.id)
    }

    private fun savePlace(ctx: Context, a: JSONObject, r: JSONObject) {
        val name = a.optString("name", "").trim()
        if (name.isEmpty()) return fail(r, "What should I call this place?")
        val loc = PlannerGeo.lastLocation(ctx)
            ?: return fail(r, "I could not get the current location. Turn on location (GPS) and allow location permission for Jarvis, then try again.")
        val places = PlannerStore.places(ctx)
        places.removeAll { it.name.equals(name, ignoreCase = true) }
        places.add(PlannerPlace(name, loc.latitude, loc.longitude))
        PlannerStore.savePlaces(ctx, places)
        ok(r, "Saved your current location as $name.")
    }

    private fun setPlaceReminder(ctx: Context, a: JSONObject, r: JSONObject) {
        val text = a.optString("text", "").trim()
        val place = a.optString("place_name", "").trim()
        if (text.isEmpty() || place.isEmpty()) return fail(r, "I need the reminder text and the place name.")
        if (PlannerStore.places(ctx).none { it.name.equals(place, ignoreCase = true) }) {
            return fail(r, "I do not have a place called $place. Ask the user to go there and say 'save this place as $place' first.")
        }
        val event = if (a.optString("event", "arrive").lowercase(Locale.US).startsWith("leav")) "leave" else "arrive"
        val item = PlannerItem(newPlannerId(), Kind.PLACE, text, 0L, "none", 0, 0, -1, -1, -1, place, event)
        PlannerScheduler.add(ctx, item)
        var msg = "I will remind you when you ${if (event == "leave") "leave" else "reach"} $place."
        if (!PlannerGeo.hasBackgroundLocation(ctx)) {
            msg += " For this to work when the app is closed, set Jarvis location permission to 'Allow all the time' in Android settings."
        }
        ok(r, msg)
        r.put("id", item.id)
    }

    private fun morningBrief(ctx: Context, a: JSONObject, r: JSONObject) {
        when (a.optString("action", "").lowercase(Locale.US)) {
            "enable" -> {
                val minutes = PlannerTime.parseMinutes(a.optString("time_of_day", "")) ?: (7 * 60 + 30)
                val base = PlannerItem("sys_brief", Kind.BRIEF, "Daily brief", 0L, "daily", 0, 0, minutes)
                val next = PlannerTime.nextAt(minutes, System.currentTimeMillis())
                PlannerScheduler.add(ctx, base.copy(triggerAt = next))
                ok(r, "Daily brief turned on for ${PlannerTime.clock(minutes)} every day.")
            }
            "disable" -> {
                PlannerScheduler.delete(ctx, "sys_brief")
                ok(r, "Daily brief turned off.")
            }
            else -> {
                val text = MorningBrief.build(ctx)
                ok(r, "Read this brief to the user in a natural way.")
                r.put("brief", text)
            }
        }
    }

    private fun automationRule(ctx: Context, a: JSONObject, r: JSONObject) {
        val rules = PlannerStore.rules(ctx)
        when (a.optString("action", "").lowercase(Locale.US)) {
            "add" -> {
                val trigger = a.optString("trigger", "").lowercase(Locale.US)
                if (trigger !in setOf("charger_connected", "charger_disconnected", "battery_low", "daily_time")) {
                    return fail(r, "trigger must be charger_connected, charger_disconnected, battery_low or daily_time.")
                }
                val doAction = a.optString("do_action", "notify").lowercase(Locale.US)
                if (doAction !in setOf("notify", "speak", "ringer_normal", "ringer_vibrate", "ringer_silent")) {
                    return fail(r, "do_action must be notify, speak, ringer_normal, ringer_vibrate or ringer_silent.")
                }
                var minutes = -1
                if (trigger == "daily_time") {
                    minutes = PlannerTime.parseMinutes(a.optString("time_of_day", "")) ?: return fail(r, NEED_TIME)
                }
                val rule = AutoRule(newPlannerId(), trigger, minutes, doAction, a.optString("text", ""))
                rules.add(rule)
                PlannerStore.saveRules(ctx, rules)
                if (trigger == "daily_time") {
                    val base = PlannerItem("rule_" + rule.id, Kind.RULE, "Rule", 0L, "daily", 0, 0, minutes, -1, -1, rule.id)
                    PlannerScheduler.add(ctx, base.copy(triggerAt = PlannerTime.nextAt(minutes, System.currentTimeMillis())))
                }
                ok(r, "Rule saved: when $trigger, $doAction.")
                r.put("rule_id", rule.id)
            }
            "remove" -> {
                val id = a.optString("rule_id", "")
                val trig = a.optString("trigger", "")
                val doomed = rules.filter { (id.isNotBlank() && it.id == id) || (id.isBlank() && trig.isNotBlank() && it.trigger == trig) }
                if (doomed.isEmpty()) return fail(r, "No matching rule found.")
                rules.removeAll(doomed)
                PlannerStore.saveRules(ctx, rules)
                for (d in doomed) PlannerScheduler.delete(ctx, "rule_" + d.id)
                ok(r, "Removed ${doomed.size} rule(s).")
            }
            else -> {
                val arr = JSONArray()
                for (x in rules) {
                    val o = JSONObject()
                    o.put("id", x.id)
                    o.put("trigger", x.trigger + (if (x.timeMin >= 0) " at " + PlannerTime.clock(x.timeMin) else ""))
                    o.put("do", x.action)
                    o.put("text", x.text)
                    arr.put(o)
                }
                ok(r, "You have ${rules.size} rule(s).")
                r.put("rules", arr)
            }
        }
    }

    private fun scheduleMessage(ctx: Context, a: JSONObject, r: JSONObject) {
        val who = a.optString("recipient_name", "").trim()
        val msg = a.optString("message", "").trim()
        if (who.isEmpty() || msg.isEmpty()) return fail(r, "I need the contact name and the message.")
        val at = resolveTime(a) ?: return fail(r, NEED_TIME)
        val item = PlannerItem(newPlannerId(), Kind.MESSAGE, "WhatsApp to $who", at, "none", 0, 0, -1, -1, -1, who, msg)
        PlannerScheduler.add(ctx, item)
        ok(r, "I will send your WhatsApp message to $who at ${PlannerTime.describe(at)}. The phone must be unlocked then.")
        r.put("id", item.id)
    }

    private fun todo(ctx: Context, a: JSONObject, r: JSONObject) {
        val list = PlannerStore.todos(ctx)
        val text = a.optString("text", "").trim()
        when (a.optString("action", "list").lowercase(Locale.US)) {
            "add" -> {
                if (text.isEmpty()) return fail(r, "What should I add?")
                list.add(TodoItem(newPlannerId(), text, false))
                PlannerStore.saveTodos(ctx, list)
                ok(r, "Added to your to-do list: $text.")
            }
            "complete" -> {
                val idx = list.indexOfFirst { !it.done && it.text.contains(text, ignoreCase = true) }
                if (text.isEmpty() || idx < 0) return fail(r, "I could not find that task.")
                list[idx] = list[idx].copy(done = true)
                PlannerStore.saveTodos(ctx, list)
                ok(r, "Marked done: ${list[idx].text}.")
            }
            "remove" -> {
                val before = list.size
                list.removeAll { text.isNotEmpty() && it.text.contains(text, ignoreCase = true) }
                if (list.size == before) return fail(r, "I could not find that task.")
                PlannerStore.saveTodos(ctx, list)
                ok(r, "Removed from your list.")
            }
            "clear_done" -> {
                list.removeAll { it.done }
                PlannerStore.saveTodos(ctx, list)
                ok(r, "Cleared the finished tasks.")
            }
            else -> {
                val pending = list.filter { !it.done }
                val arr = JSONArray()
                for (t in pending) arr.put(t.text)
                ok(r, if (pending.isEmpty()) "Your to-do list is empty." else "You have ${pending.size} task(s) to do.")
                r.put("tasks", arr)
            }
        }
    }

    private fun birthdays(ctx: Context, a: JSONObject, r: JSONObject) {
        when (a.optString("action", "check").lowercase(Locale.US)) {
            "enable" -> {
                val minutes = PlannerTime.parseMinutes(a.optString("time_of_day", "")) ?: (8 * 60)
                val base = PlannerItem("sys_birthday", Kind.BIRTHDAY, "Birthday check", 0L, "daily", 0, 0, minutes)
                PlannerScheduler.add(ctx, base.copy(triggerAt = PlannerTime.nextAt(minutes, System.currentTimeMillis())))
                ok(r, "Birthday reminders are on. I will check your contacts every day at ${PlannerTime.clock(minutes)}.")
            }
            "disable" -> {
                PlannerScheduler.delete(ctx, "sys_birthday")
                ok(r, "Birthday reminders are off.")
            }
            else -> {
                val found = BirthdayHelper.upcoming(ctx, 7)
                val arr = JSONArray()
                for (f in found) arr.put(f.first + (if (f.second == 0) " (today)" else " (in ${f.second} days)"))
                ok(r, if (found.isEmpty()) "No birthdays in the next 7 days (or contacts permission/birthdays are missing)." else "Birthdays found.")
                r.put("birthdays", arr)
            }
        }
    }

    private fun health(ctx: Context, a: JSONObject, r: JSONObject) {
        val type = a.optString("type", "").lowercase(Locale.US).trim()
        if (type.isEmpty()) return fail(r, "Which reminder: water, break, stretch, sleep or custom?")
        val id = "health_" + type
        if (!a.optBoolean("enabled", true)) {
            PlannerScheduler.delete(ctx, id)
            return ok(r, "Turned off the $type reminder.")
        }
        val from = PlannerTime.parseMinutes(a.optString("active_from", "")) ?: (8 * 60)
        val to = PlannerTime.parseMinutes(a.optString("active_to", "")) ?: (22 * 60)
        val now = System.currentTimeMillis()
        val item: PlannerItem
        if (type == "sleep") {
            val minutes = PlannerTime.parseMinutes(a.optString("time_of_day", "")) ?: (22 * 60 + 30)
            item = PlannerItem(id, Kind.HEALTH, "It is almost bedtime. Time to wind down and sleep.", PlannerTime.nextAt(minutes, now), "daily", 0, 0, minutes)
        } else {
            val defaultEvery = when (type) {
                "water" -> 90
                "break" -> 60
                "stretch" -> 90
                else -> 120
            }
            var every = a.optDouble("interval_minutes", 0.0).toInt()
            if (every <= 0) every = defaultEvery
            val title = when (type) {
                "water" -> "Time to drink some water."
                "break" -> "Take a short break and rest your eyes."
                "stretch" -> "Time to stand up and stretch."
                else -> a.optString("text", "").ifBlank { "Health reminder" }
            }
            var first = now + every * 60_000L
            val c = Calendar.getInstance()
            c.timeInMillis = first
            val m = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
            if (m < from) first = PlannerTime.atTime(first, 0, from) else if (m > to) first = PlannerTime.atTime(first, 1, from)
            item = PlannerItem(id, Kind.HEALTH, title, first, "interval", 0, every, -1, from, to)
        }
        PlannerScheduler.add(ctx, item)
        ok(r, "Health reminder '$type' is on. Next one: ${PlannerTime.describe(item.triggerAt)}.")
    }

    private fun listAll(ctx: Context, r: JSONObject) {
        val items = PlannerStore.items(ctx).filter { it.enabled && it.kind != Kind.RULE }
        val arr = JSONArray()
        for (i in items.sortedBy { if (it.triggerAt > 0L) it.triggerAt else Long.MAX_VALUE }) {
            val o = JSONObject()
            o.put("id", i.id)
            o.put("kind", i.kind)
            o.put("title", i.title)
            o.put("when", if (i.kind == Kind.PLACE) "when you ${if (i.extra2 == "leave") "leave" else "reach"} ${i.extra1}" else PlannerTime.describe(i.triggerAt))
            if (i.repeat != "none") o.put("repeats", i.repeat)
            arr.put(o)
        }
        val todos = PlannerStore.todos(ctx).filter { !it.done }
        r.put("items", arr)
        r.put("todo_pending", todos.size)
        r.put("places", JSONArray(PlannerStore.places(ctx).map { it.name }))
        r.put("rules", PlannerStore.rules(ctx).size)
        ok(r, "You have ${items.size} planned item(s) and ${todos.size} open to-do task(s).")
    }

    private fun cancelItem(ctx: Context, a: JSONObject, r: JSONObject) {
        val query = a.optString("query", "").trim()
        val kind = a.optString("kind", "").trim().lowercase(Locale.US)
        val all = a.optBoolean("all", false)
        if (query.isEmpty() && kind.isEmpty()) return fail(r, "Tell me which one to cancel (words from its title, or the kind).")
        val matches = PlannerStore.items(ctx).filter {
            it.kind != Kind.RULE &&
                (kind.isEmpty() || it.kind == kind) &&
                (query.isEmpty() || it.title.contains(query, ignoreCase = true) || it.extra1.contains(query, ignoreCase = true))
        }
        if (matches.isEmpty()) return fail(r, "I could not find a matching item.")
        if (matches.size > 1 && !all) {
            val arr = JSONArray()
            for (m in matches) arr.put(m.title + " (" + m.kind + ", " + PlannerTime.describe(m.triggerAt) + ")")
            fail(r, "More than one item matches. Ask the user which one, or call again with all=true.")
            r.put("matches", arr)
            return
        }
        for (m in matches) PlannerScheduler.delete(ctx, m.id)
        ok(r, "Cancelled ${matches.size} item(s).")
    }
}
