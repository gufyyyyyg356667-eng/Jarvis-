package com.jarvis.assistant.planner

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat

object PlannerNotifier {
    private const val TAG = "PlannerNotifier"
    const val CH_ALERT = "planner_alert"
    const val CH_RING = "planner_ring"

    fun ensureChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val alert = NotificationChannel(CH_ALERT, "Reminders and alarms", NotificationManager.IMPORTANCE_HIGH)
            alert.description = "Alarms, timers, reminders and scheduled actions"
            alert.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), attrs)
            alert.enableVibration(true)
            nm.createNotificationChannel(alert)

            val ring = NotificationChannel(CH_RING, "Ringing now", NotificationManager.IMPORTANCE_LOW)
            ring.description = "Shown while an alarm or reminder is playing"
            ring.setSound(null, null)
            nm.createNotificationChannel(ring)
        } catch (e: Exception) {
            Log.e(TAG, "ensureChannels failed: ${e.message}")
        }
    }

    fun build(
        ctx: Context,
        title: String,
        text: String,
        itemId: String,
        kind: String,
        channel: String,
        withActions: Boolean
    ): Notification {
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, PlannerActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val b = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(open)
        if (withActions) {
            b.addAction(0, "Dismiss", actionIntent(ctx, PlannerReceiver.ACTION_DISMISS, itemId, title, kind))
            b.addAction(0, "Snooze 10 min", actionIntent(ctx, PlannerReceiver.ACTION_SNOOZE, itemId, title, kind))
        }
        return b.build()
    }

    private fun actionIntent(ctx: Context, action: String, itemId: String, title: String, kind: String): PendingIntent {
        val i = Intent(ctx, PlannerReceiver::class.java)
        i.action = action
        i.putExtra(PlannerReceiver.EXTRA_ID, itemId)
        i.putExtra(PlannerReceiver.EXTRA_TITLE, title)
        i.putExtra(PlannerReceiver.EXTRA_KIND, kind)
        return PendingIntent.getBroadcast(
            ctx, (action + itemId).hashCode(), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Plain heads-up notification (used when the ring service cannot start, and for status messages). */
    fun post(ctx: Context, notifId: Int, title: String, text: String, itemId: String = "", kind: String = "", withActions: Boolean = false) {
        try {
            ensureChannels(ctx)
            val n = build(ctx, title, text, itemId, kind, CH_ALERT, withActions)
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(notifId, n)
        } catch (e: Exception) {
            Log.e(TAG, "post failed: ${e.message}")
        }
    }

    fun cancel(ctx: Context, notifId: Int) {
        try {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(notifId)
        } catch (e: Exception) {
        }
    }
}
