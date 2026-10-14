package com.jarvis.assistant.planner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** Restores alarms after reboot, time change or app update. Only reschedules, never fires anything. */
class PlannerBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pending = goAsync()
        Thread {
            try {
                PlannerNotifier.ensureChannels(app)
                PlannerScheduler.rescheduleAll(app)
            } catch (e: Exception) {
                Log.e("PlannerBoot", "reschedule failed: ${e.message}")
            } finally {
                pending.finish()
            }
        }.start()
    }
}
