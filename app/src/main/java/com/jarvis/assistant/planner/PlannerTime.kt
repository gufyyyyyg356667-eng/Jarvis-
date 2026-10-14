package com.jarvis.assistant.planner

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object PlannerTime {

    /** Accepts "07:30", "7:30", "7.30", "7pm", "7:30 am". Returns minutes after midnight or null. */
    fun parseMinutes(raw: String?): Int? {
        if (raw == null) return null
        var s = raw.trim().lowercase(Locale.US).replace(".", ":")
        if (s.isEmpty()) return null
        var pm = false
        var am = false
        if (s.endsWith("pm")) {
            pm = true
            s = s.dropLast(2).trim()
        } else if (s.endsWith("am")) {
            am = true
            s = s.dropLast(2).trim()
        }
        val parts = s.split(":")
        val h0 = parts[0].trim().toIntOrNull() ?: return null
        val m = if (parts.size > 1) (parts[1].trim().toIntOrNull() ?: return null) else 0
        var h = h0
        if (pm && h < 12) h += 12
        if (am && h == 12) h = 0
        if (h < 0 || h > 23 || m < 0 || m > 59) return null
        return h * 60 + m
    }

    fun atTime(base: Long, dayOffset: Int, minutes: Int): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = base
        c.add(Calendar.DAY_OF_YEAR, dayOffset)
        c.set(Calendar.HOUR_OF_DAY, minutes / 60)
        c.set(Calendar.MINUTE, minutes % 60)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    /** Next moment (strictly after [afterMs]) that has the given minute-of-day. */
    fun nextAt(minutes: Int, afterMs: Long): Long {
        val t = atTime(afterMs, 0, minutes)
        return if (t > afterMs) t else atTime(afterMs, 1, minutes)
    }

    fun dayAllowed(mask: Int, cal: Calendar): Boolean {
        if (mask == 0) return true
        val bit = 1 shl (cal.get(Calendar.DAY_OF_WEEK) - 1)
        return (mask and bit) != 0
    }

    /** "mon,tue", "weekdays", "weekend", "daily"  ->  bit mask (0 = every day). */
    fun parseDays(raw: String?): Int {
        if (raw == null) return 0
        val s = raw.lowercase(Locale.US)
        if (s.isBlank() || s.contains("daily") || s.contains("every day") || s.contains("everyday")) return 0
        var mask = 0
        if (s.contains("weekday")) mask = mask or 0b0111110
        if (s.contains("weekend")) mask = mask or 0b1000001
        val names = listOf("sun", "mon", "tue", "wed", "thu", "fri", "sat")
        for (i in names.indices) {
            if (s.contains(names[i])) mask = mask or (1 shl i)
        }
        return mask
    }

    fun nextRepeat(item: PlannerItem, from: Long): Long {
        if (item.repeat == "interval") {
            if (item.intervalMin <= 0) return 0L
            var t = from + item.intervalMin * 60_000L
            if (item.windowStartMin >= 0 && item.windowEndMin >= 0) {
                val c = Calendar.getInstance()
                c.timeInMillis = t
                val m = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
                if (m < item.windowStartMin) {
                    t = atTime(t, 0, item.windowStartMin)
                } else if (m > item.windowEndMin) {
                    t = atTime(t, 1, item.windowStartMin)
                }
            }
            return t
        }
        if (item.repeat == "daily" || item.repeat == "weekly") {
            if (item.timeMin < 0) return 0L
            for (d in 0..7) {
                val t = atTime(from, d, item.timeMin)
                if (t <= from) continue
                val c = Calendar.getInstance()
                c.timeInMillis = t
                if (dayAllowed(item.daysMask, c)) return t
            }
        }
        return 0L
    }

    fun describe(ms: Long): String {
        return try {
            SimpleDateFormat("EEE d MMM, h:mm a", Locale.getDefault()).format(ms)
        } catch (e: Exception) {
            ms.toString()
        }
    }

    fun clock(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return String.format(Locale.US, "%02d:%02d", h, m)
    }

    fun durationText(totalSeconds: Long): String {
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        val parts = ArrayList<String>()
        if (h > 0) parts.add("$h hour" + (if (h == 1L) "" else "s"))
        if (m > 0) parts.add("$m minute" + (if (m == 1L) "" else "s"))
        if (s > 0 || parts.isEmpty()) parts.add("$s second" + (if (s == 1L) "" else "s"))
        return parts.joinToString(" ")
    }
}
