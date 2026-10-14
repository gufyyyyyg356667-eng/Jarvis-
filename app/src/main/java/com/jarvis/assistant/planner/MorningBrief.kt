package com.jarvis.assistant.planner

import android.content.Context
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.util.LocationWeatherHelper
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object MorningBrief {

    /** Plain-text brief: greeting, date, weather, today's plans, to-dos and birthdays. Blocking (call off the main thread). */
    fun build(ctx: Context): String {
        val sb = StringBuilder()
        val now = Calendar.getInstance()
        val hour = now.get(Calendar.HOUR_OF_DAY)
        val greeting = if (hour < 12) "Good morning" else if (hour < 17) "Good afternoon" else "Good evening"
        val name = try {
            JarvisApp.instance.preferences.userName
        } catch (e: Exception) {
            ""
        }
        sb.append(greeting)
        if (name.isNotBlank()) sb.append(", ").append(name)
        sb.append(". ")
        sb.append("Today is ").append(SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now.time)).append(". ")

        val weather = try {
            runBlocking { withTimeoutOrNull(8_000L) { LocationWeatherHelper.fetchWeather(ctx) } }
        } catch (e: Exception) {
            null
        }
        if (weather != null) {
            sb.append("It is ").append(weather.temperature).append(" and ").append(weather.condition)
                .append(" in ").append(weather.cityName).append(". ")
        }

        // plans for the rest of today
        val endOfDay = PlannerTime.atTime(now.timeInMillis, 0, 23 * 60 + 59)
        val today = PlannerStore.items(ctx)
            .filter { it.enabled && it.triggerAt > now.timeInMillis && it.triggerAt <= endOfDay }
            .filter { it.kind == Kind.REMINDER || it.kind == Kind.ALARM || it.kind == Kind.MESSAGE || it.kind == Kind.TIMER }
            .sortedBy { it.triggerAt }
        if (today.isEmpty()) {
            sb.append("You have no reminders or alarms left today. ")
        } else {
            sb.append("Today you have ").append(today.size).append(if (today.size == 1) " item: " else " items: ")
            sb.append(
                today.take(5).joinToString("; ") {
                    val t = SimpleDateFormat("h:mm a", Locale.getDefault()).format(it.triggerAt)
                    it.title.ifBlank { it.kind } + " at " + t
                }
            )
            sb.append(". ")
        }

        val todos = PlannerStore.todos(ctx).filter { !it.done }
        if (todos.isNotEmpty()) {
            sb.append("Your to-do list has ").append(todos.size).append(if (todos.size == 1) " task: " else " tasks: ")
            sb.append(todos.take(4).joinToString(", ") { it.text })
            sb.append(". ")
        }

        val birthdays = BirthdayHelper.upcoming(ctx, 0)
        if (birthdays.isNotEmpty()) {
            sb.append("Today is the birthday of ").append(birthdays.joinToString(", ") { it.first }).append(". ")
        }
        return sb.toString().trim()
    }
}
