package com.jarvis.assistant.planner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.PowerManager
import android.util.Log
import com.jarvis.assistant.util.WhatsAppMessenger

/** Receives alarm fires, notification buttons and geofence events. Not exported. */
class PlannerReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "PlannerReceiver"
        const val ACTION_FIRE = "com.jarvis.assistant.planner.FIRE"
        const val ACTION_DISMISS = "com.jarvis.assistant.planner.DISMISS"
        const val ACTION_SNOOZE = "com.jarvis.assistant.planner.SNOOZE"
        const val ACTION_PLACE = "com.jarvis.assistant.planner.PLACE"
        const val EXTRA_ID = "id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_KIND = "kind"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val action = intent.action ?: return
        val id = intent.getStringExtra(EXTRA_ID) ?: ""
        val title = intent.getStringExtra(EXTRA_TITLE) ?: ""
        val kind = intent.getStringExtra(EXTRA_KIND) ?: ""
        val entering = intent.getBooleanExtra(LocationManager.KEY_PROXIMITY_ENTERING, true)
        val pending = goAsync()
        Thread {
            try {
                when (action) {
                    ACTION_FIRE -> fire(app, id)
                    ACTION_DISMISS -> dismiss(app, id)
                    ACTION_SNOOZE -> snooze(app, id, title, kind)
                    ACTION_PLACE -> placeEvent(app, id, entering)
                }
            } catch (e: Exception) {
                Log.e(TAG, "handling $action failed: ${e.message}", e)
            } finally {
                pending.finish()
            }
        }.start()
    }

    private fun fire(ctx: Context, id: String) {
        val item = PlannerStore.find(ctx, id) ?: return
        if (!item.enabled) return

        // Plan the next occurrence (or consume the one-shot item) before doing the work.
        var next = 0L
        if (item.repeat != "none") next = PlannerTime.nextRepeat(item, System.currentTimeMillis())
        if (next > 0L) {
            val updated = item.copy(triggerAt = next)
            PlannerStore.upsert(ctx, updated)
            PlannerScheduler.schedule(ctx, updated)
        } else if (item.kind == Kind.ALARM) {
            PlannerStore.upsert(ctx, item.copy(enabled = false))
        } else {
            PlannerStore.remove(ctx, item.id)
        }

        when (item.kind) {
            Kind.ALARM -> ring(ctx, item, "Alarm", item.title.ifBlank { "Alarm" }, "loop")
            Kind.TIMER -> ring(ctx, item, "Timer finished", ("Timer finished. " + item.title.replace("Timer", "")).trim(), "loop")
            Kind.REMINDER -> ring(ctx, item, "Reminder", "Reminder: " + item.title, "once")
            Kind.HEALTH -> ring(ctx, item, "Health reminder", item.title, "once")
            Kind.MESSAGE -> sendScheduledMessage(ctx, item)
            Kind.BRIEF -> deliverBrief(ctx)
            Kind.BIRTHDAY -> deliverBirthdays(ctx)
            Kind.RULE -> AutomationRunner.runRuleById(ctx, item.extra1)
        }
    }

    private fun ring(ctx: Context, item: PlannerItem, heading: String, speak: String, tone: String) {
        val text = item.title.ifBlank { heading }
        try {
            PlannerRingService.start(ctx, item.id, item.kind, heading + ": " + text, speak, tone)
        } catch (e: Exception) {
            // Foreground service not allowed right now: fall back to a normal heads-up notification.
            Log.w(TAG, "Ring service could not start (${e.message}); posting notification instead")
            PlannerNotifier.post(ctx, item.id.hashCode(), heading, text, item.id, item.kind, true)
        }
    }

    private fun dismiss(ctx: Context, id: String) {
        PlannerRingService.stop(ctx)
        PlannerNotifier.cancel(ctx, id.hashCode())
        PlannerNotifier.cancel(ctx, PlannerRingService.NOTIF_ID)
    }

    private fun snooze(ctx: Context, id: String, title: String, kind: String) {
        dismiss(ctx, id)
        val clean = title.substringAfter(": ", title).ifBlank { "Reminder" }
        val newKind = if (kind == Kind.ALARM) Kind.ALARM else Kind.REMINDER
        val item = PlannerItem(
            id = newPlannerId(),
            kind = newKind,
            title = clean,
            triggerAt = System.currentTimeMillis() + 10 * 60_000L
        )
        PlannerScheduler.add(ctx, item)
    }

    private fun placeEvent(ctx: Context, id: String, entering: Boolean) {
        val item = PlannerStore.find(ctx, id) ?: return
        val wantArrive = item.extra2 != "leave"
        if (wantArrive != entering) return
        PlannerScheduler.delete(ctx, id)
        ring(ctx, item, "Location reminder", item.title, "once")
    }

    private fun sendScheduledMessage(ctx: Context, item: PlannerItem) {
        wakeScreen(ctx)
        val result = try {
            WhatsAppMessenger.sendMessage(ctx, item.extra1, item.extra2, null, true)
        } catch (e: Exception) {
            null
        }
        val msg = if (result is WhatsAppMessenger.SendResult.Success) {
            "Opened WhatsApp and queued your scheduled message to ${result.contactName}. Check that it was sent."
        } else {
            "Could not send the scheduled WhatsApp message to ${item.extra1}. Please send it manually: ${item.extra2}"
        }
        PlannerNotifier.post(ctx, item.id.hashCode(), "Scheduled message", msg)
    }

    @Suppress("DEPRECATION")
    private fun wakeScreen(ctx: Context) {
        try {
            val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
            val wl = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "jarvis:planner-message"
            )
            wl.acquire(15_000L)
        } catch (e: Exception) {
        }
    }

    private fun deliverBrief(ctx: Context) {
        val text = MorningBrief.build(ctx)
        PlannerNotifier.post(ctx, 7200, "Your daily brief", text)
        try {
            PlannerRingService.start(ctx, "brief", Kind.BRIEF, "Your daily brief", text, "none")
        } catch (e: Exception) {
        }
    }

    private fun deliverBirthdays(ctx: Context) {
        val today = BirthdayHelper.upcoming(ctx, 0)
        if (today.isEmpty()) return
        val names = today.joinToString(", ") { it.first }
        val text = "Today is the birthday of $names."
        PlannerNotifier.post(ctx, 7201, "Birthday", text)
        try {
            PlannerRingService.start(ctx, "birthday", Kind.BIRTHDAY, "Birthday", text, "once")
        } catch (e: Exception) {
        }
    }
}
