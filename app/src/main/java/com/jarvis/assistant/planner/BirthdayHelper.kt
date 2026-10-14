package com.jarvis.assistant.planner

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import java.util.Calendar

object BirthdayHelper {

    /** Birthdays happening within [daysAhead] days (0 = today only), as (name, daysFromNow). */
    fun upcoming(ctx: Context, daysAhead: Int): List<Pair<String, Int>> {
        val out = ArrayList<Pair<String, Int>>()
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return out
        }
        val columns = arrayOf(
            ContactsContract.Data.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Event.START_DATE
        )
        val selection = ContactsContract.Data.MIMETYPE + "=? AND " + ContactsContract.CommonDataKinds.Event.TYPE + "=?"
        val args = arrayOf(
            ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY.toString()
        )
        try {
            val cursor = ctx.contentResolver.query(ContactsContract.Data.CONTENT_URI, columns, selection, args, null)
            cursor?.use { c ->
                while (c.moveToNext()) {
                    val name = c.getString(0) ?: continue
                    val date = c.getString(1) ?: continue
                    val md = parseMonthDay(date) ?: continue
                    val diff = daysUntil(md.first, md.second)
                    if (diff in 0..daysAhead) out.add(Pair(name, diff))
                }
            }
        } catch (e: Exception) {
        }
        return out.sortedBy { it.second }
    }

    /** "1990-05-17" or "--05-17"  ->  (5, 17) */
    private fun parseMonthDay(raw: String): Pair<Int, Int>? {
        val s = raw.trim()
        val parts = if (s.startsWith("--")) s.substring(2).split("-") else s.split("-")
        val n = parts.size
        if (n < 2) return null
        val month = parts[n - 2].toIntOrNull() ?: return null
        val day = parts[n - 1].take(2).toIntOrNull() ?: return null
        if (month < 1 || month > 12 || day < 1 || day > 31) return null
        return Pair(month, day)
    }

    private fun daysUntil(month: Int, day: Int): Int {
        val today = Calendar.getInstance()
        today.set(Calendar.HOUR_OF_DAY, 0)
        today.set(Calendar.MINUTE, 0)
        today.set(Calendar.SECOND, 0)
        today.set(Calendar.MILLISECOND, 0)
        val target = today.clone() as Calendar
        target.set(Calendar.MONTH, month - 1)
        target.set(Calendar.DAY_OF_MONTH, day)
        if (target.before(today)) target.add(Calendar.YEAR, 1)
        val diffMs = target.timeInMillis - today.timeInMillis
        return Math.round(diffMs / 86_400_000.0).toInt()
    }
}
