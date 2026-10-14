package com.jarvis.assistant.planner

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log

/** Started once from JarvisApp.onCreate. Everything here is additive and wrapped in try/catch. */
object PlannerRuntime {
    private const val TAG = "PlannerRuntime"
    @Volatile
    private var started = false

    private val eventReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val event = when (intent.action) {
                Intent.ACTION_POWER_CONNECTED -> "charger_connected"
                Intent.ACTION_POWER_DISCONNECTED -> "charger_disconnected"
                Intent.ACTION_BATTERY_LOW -> "battery_low"
                else -> return
            }
            val app = context.applicationContext
            val pending = goAsync()
            Thread {
                try {
                    AutomationRunner.runEvent(app, event)
                } catch (e: Exception) {
                    Log.e(TAG, "event $event failed: ${e.message}")
                } finally {
                    pending.finish()
                }
            }.start()
        }
    }

    fun init(app: Application) {
        if (started) return
        started = true
        try {
            PlannerNotifier.ensureChannels(app)
            val filter = IntentFilter()
            filter.addAction(Intent.ACTION_POWER_CONNECTED)
            filter.addAction(Intent.ACTION_POWER_DISCONNECTED)
            filter.addAction(Intent.ACTION_BATTERY_LOW)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                app.registerReceiver(eventReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                app.registerReceiver(eventReceiver, filter)
            }
            Thread {
                try {
                    PlannerScheduler.rescheduleAll(app)
                } catch (e: Exception) {
                    Log.e(TAG, "rescheduleAll failed: ${e.message}")
                }
            }.start()
        } catch (e: Exception) {
            Log.e(TAG, "init failed: ${e.message}", e)
        }
    }
}
