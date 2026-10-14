package com.jarvis.assistant.planner

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.util.Log

/** Runs the "when X happens, do Y" rules. */
object AutomationRunner {
    private const val TAG = "AutomationRunner"

    fun runEvent(ctx: Context, event: String) {
        for (rule in PlannerStore.rules(ctx)) {
            if (rule.enabled && rule.trigger == event) run(ctx, rule)
        }
    }

    fun runRuleById(ctx: Context, ruleId: String) {
        val rule = PlannerStore.rules(ctx).firstOrNull { it.id == ruleId } ?: return
        if (rule.enabled) run(ctx, rule)
    }

    fun run(ctx: Context, rule: AutoRule) {
        val text = rule.text.ifBlank { "Automation triggered" }
        when (rule.action) {
            "notify" -> PlannerNotifier.post(ctx, rule.id.hashCode(), "Jarvis automation", text)
            "speak" -> {
                try {
                    PlannerRingService.speakOnly(ctx, text)
                } catch (e: Exception) {
                    PlannerNotifier.post(ctx, rule.id.hashCode(), "Jarvis automation", text)
                }
            }
            "ringer_normal" -> setRinger(ctx, AudioManager.RINGER_MODE_NORMAL, "normal")
            "ringer_vibrate" -> setRinger(ctx, AudioManager.RINGER_MODE_VIBRATE, "vibrate")
            "ringer_silent" -> setRinger(ctx, AudioManager.RINGER_MODE_SILENT, "silent")
        }
    }

    private fun setRinger(ctx: Context, mode: Int, label: String) {
        try {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (mode == AudioManager.RINGER_MODE_SILENT && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                !nm.isNotificationPolicyAccessGranted
            ) {
                PlannerNotifier.post(
                    ctx, 7300, "Silent mode needs permission",
                    "Open Android Settings > Do Not Disturb access and allow Jarvis, then try again."
                )
                return
            }
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.ringerMode = mode
            PlannerNotifier.post(ctx, 7301, "Jarvis automation", "Ringer mode set to $label.")
        } catch (e: Exception) {
            Log.e(TAG, "setRinger failed: ${e.message}")
        }
    }
}
