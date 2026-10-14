package com.jarvis.assistant.planner

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Item kinds stored by the planner. */
object Kind {
    const val ALARM = "alarm"
    const val REMINDER = "reminder"
    const val TIMER = "timer"
    const val HEALTH = "health"
    const val MESSAGE = "message"
    const val PLACE = "place"
    const val BRIEF = "brief"
    const val BIRTHDAY = "birthday"
    const val RULE = "rule"
}

fun newPlannerId(): String = UUID.randomUUID().toString().substring(0, 8)

data class PlannerItem(
    val id: String,
    val kind: String,
    val title: String,
    val triggerAt: Long = 0L,
    val repeat: String = "none",      // none | daily | weekly | interval
    val daysMask: Int = 0,            // bit0 = Sunday ... bit6 = Saturday, 0 = every day
    val intervalMin: Int = 0,
    val timeMin: Int = -1,            // minute of day for daily/weekly
    val windowStartMin: Int = -1,     // active window for interval reminders
    val windowEndMin: Int = -1,
    val extra1: String = "",          // MESSAGE: recipient | PLACE: place name | RULE: rule id
    val extra2: String = "",          // MESSAGE: text | PLACE: arrive/leave
    val enabled: Boolean = true
) {
    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("id", id)
        o.put("kind", kind)
        o.put("title", title)
        o.put("triggerAt", triggerAt)
        o.put("repeat", repeat)
        o.put("daysMask", daysMask)
        o.put("intervalMin", intervalMin)
        o.put("timeMin", timeMin)
        o.put("ws", windowStartMin)
        o.put("we", windowEndMin)
        o.put("e1", extra1)
        o.put("e2", extra2)
        o.put("enabled", enabled)
        return o
    }

    companion object {
        fun fromJson(o: JSONObject): PlannerItem {
            return PlannerItem(
                id = o.optString("id"),
                kind = o.optString("kind"),
                title = o.optString("title"),
                triggerAt = o.optLong("triggerAt", 0L),
                repeat = o.optString("repeat", "none"),
                daysMask = o.optInt("daysMask", 0),
                intervalMin = o.optInt("intervalMin", 0),
                timeMin = o.optInt("timeMin", -1),
                windowStartMin = o.optInt("ws", -1),
                windowEndMin = o.optInt("we", -1),
                extra1 = o.optString("e1", ""),
                extra2 = o.optString("e2", ""),
                enabled = o.optBoolean("enabled", true)
            )
        }
    }
}

data class TodoItem(val id: String, val text: String, val done: Boolean)

data class PlannerPlace(val name: String, val lat: Double, val lon: Double)

data class AutoRule(
    val id: String,
    val trigger: String,   // charger_connected | charger_disconnected | battery_low | daily_time
    val timeMin: Int,      // for daily_time
    val action: String,    // notify | speak | ringer_normal | ringer_vibrate | ringer_silent
    val text: String,
    val enabled: Boolean = true
)

/** Very small JSON-backed storage. Lives in its own SharedPreferences file, so the app database is untouched. */
object PlannerStore {
    private const val PREFS = "jarvis_planner"

    private fun sp(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---------- items ----------
    @Synchronized
    fun items(ctx: Context): MutableList<PlannerItem> {
        val out = mutableListOf<PlannerItem>()
        try {
            val arr = JSONArray(sp(ctx).getString("items", "[]"))
            for (i in 0 until arr.length()) out.add(PlannerItem.fromJson(arr.getJSONObject(i)))
        } catch (e: Exception) {
        }
        return out
    }

    @Synchronized
    fun saveItems(ctx: Context, list: List<PlannerItem>) {
        val arr = JSONArray()
        for (entry in list) arr.put(entry.toJson())
        sp(ctx).edit().putString("items", arr.toString()).apply()
    }

    @Synchronized
    fun upsert(ctx: Context, item: PlannerItem) {
        val list = items(ctx)
        val idx = list.indexOfFirst { it.id == item.id }
        if (idx >= 0) list[idx] = item else list.add(item)
        saveItems(ctx, list)
    }

    @Synchronized
    fun remove(ctx: Context, id: String): Boolean {
        val list = items(ctx)
        val removed = list.removeAll { it.id == id }
        if (removed) saveItems(ctx, list)
        return removed
    }

    fun find(ctx: Context, id: String): PlannerItem? = items(ctx).firstOrNull { it.id == id }

    // ---------- to-do ----------
    @Synchronized
    fun todos(ctx: Context): MutableList<TodoItem> {
        val out = mutableListOf<TodoItem>()
        try {
            val arr = JSONArray(sp(ctx).getString("todos", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(TodoItem(o.optString("id"), o.optString("text"), o.optBoolean("done", false)))
            }
        } catch (e: Exception) {
        }
        return out
    }

    @Synchronized
    fun saveTodos(ctx: Context, list: List<TodoItem>) {
        val arr = JSONArray()
        for (t in list) {
            val o = JSONObject()
            o.put("id", t.id)
            o.put("text", t.text)
            o.put("done", t.done)
            arr.put(o)
        }
        sp(ctx).edit().putString("todos", arr.toString()).apply()
    }

    // ---------- places ----------
    @Synchronized
    fun places(ctx: Context): MutableList<PlannerPlace> {
        val out = mutableListOf<PlannerPlace>()
        try {
            val arr = JSONArray(sp(ctx).getString("places", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(PlannerPlace(o.optString("name"), o.optDouble("lat"), o.optDouble("lon")))
            }
        } catch (e: Exception) {
        }
        return out
    }

    @Synchronized
    fun savePlaces(ctx: Context, list: List<PlannerPlace>) {
        val arr = JSONArray()
        for (p in list) {
            val o = JSONObject()
            o.put("name", p.name)
            o.put("lat", p.lat)
            o.put("lon", p.lon)
            arr.put(o)
        }
        sp(ctx).edit().putString("places", arr.toString()).apply()
    }

    // ---------- automation rules ----------
    @Synchronized
    fun rules(ctx: Context): MutableList<AutoRule> {
        val out = mutableListOf<AutoRule>()
        try {
            val arr = JSONArray(sp(ctx).getString("rules", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    AutoRule(
                        o.optString("id"), o.optString("trigger"), o.optInt("timeMin", -1),
                        o.optString("action"), o.optString("text"), o.optBoolean("enabled", true)
                    )
                )
            }
        } catch (e: Exception) {
        }
        return out
    }

    @Synchronized
    fun saveRules(ctx: Context, list: List<AutoRule>) {
        val arr = JSONArray()
        for (r in list) {
            val o = JSONObject()
            o.put("id", r.id)
            o.put("trigger", r.trigger)
            o.put("timeMin", r.timeMin)
            o.put("action", r.action)
            o.put("text", r.text)
            o.put("enabled", r.enabled)
            arr.put(o)
        }
        sp(ctx).edit().putString("rules", arr.toString()).apply()
    }

    // ---------- stopwatch ----------
    fun getLong(ctx: Context, key: String): Long = sp(ctx).getLong(key, 0L)
    fun putLong(ctx: Context, key: String, value: Long) {
        sp(ctx).edit().putLong(key, value).apply()
    }
}
