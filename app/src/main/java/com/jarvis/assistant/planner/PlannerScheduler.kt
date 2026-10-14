package com.jarvis.assistant.planner

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log

object PlannerScheduler {
    private const val TAG = "PlannerScheduler"

    fun firePending(ctx: Context, id: String): PendingIntent {
        val i = Intent(ctx, PlannerReceiver::class.java)
        i.action = PlannerReceiver.ACTION_FIRE
        i.data = Uri.parse("planner://item/$id")
        i.putExtra(PlannerReceiver.EXTRA_ID, id)
        return PendingIntent.getBroadcast(
            ctx, id.hashCode(), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun schedule(ctx: Context, item: PlannerItem) {
        if (!item.enabled || item.triggerAt <= 0L) return
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = firePending(ctx, item.id)
        try {
            if (item.kind == Kind.ALARM) {
                // setAlarmClock is exempt from exact-alarm restrictions and survives Doze.
                val show = PendingIntent.getActivity(
                    ctx, 0, Intent(ctx, PlannerActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                am.setAlarmClock(AlarmManager.AlarmClockInfo(item.triggerAt, show), pi)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, item.triggerAt, pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, item.triggerAt, pi)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Exact alarm not allowed, using inexact: ${e.message}")
            try {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, item.triggerAt, pi)
            } catch (e2: Exception) {
                Log.e(TAG, "schedule failed: ${e2.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "schedule failed: ${e.message}")
        }
    }

    fun cancel(ctx: Context, item: PlannerItem) {
        try {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.cancel(firePending(ctx, item.id))
        } catch (e: Exception) {
        }
        if (item.kind == Kind.PLACE) PlannerGeo.unregister(ctx, item.id)
    }

    /** Stores the item and sets its alarm (or geofence). */
    fun add(ctx: Context, item: PlannerItem) {
        PlannerStore.upsert(ctx, item)
        if (item.kind == Kind.PLACE) PlannerGeo.register(ctx, item) else schedule(ctx, item)
    }

    /** Removes an item and cancels its alarm. */
    fun delete(ctx: Context, id: String) {
        val item = PlannerStore.find(ctx, id) ?: return
        cancel(ctx, item)
        PlannerStore.remove(ctx, id)
    }

    /** Called on app start, after reboot, time/zone change and app update. */
    fun rescheduleAll(ctx: Context) {
        val now = System.currentTimeMillis()
        for (item in PlannerStore.items(ctx)) {
            if (!item.enabled) continue
            if (item.kind == Kind.PLACE) {
                PlannerGeo.register(ctx, item)
                continue
            }
            var current = item
            if (item.triggerAt <= now) {
                if (item.repeat != "none") {
                    val n = PlannerTime.nextRepeat(item, now)
                    if (n <= 0L) continue
                    current = item.copy(triggerAt = n)
                    PlannerStore.upsert(ctx, current)
                } else if (now - item.triggerAt < 15 * 60_000L) {
                    // missed just now (for example the phone was off): fire shortly
                    current = item.copy(triggerAt = now + 5_000L)
                    PlannerStore.upsert(ctx, current)
                } else {
                    if (item.kind == Kind.ALARM) {
                        PlannerStore.upsert(ctx, item.copy(enabled = false))
                    } else {
                        PlannerStore.remove(ctx, item.id)
                    }
                    continue
                }
            }
            schedule(ctx, current)
        }
    }
}
