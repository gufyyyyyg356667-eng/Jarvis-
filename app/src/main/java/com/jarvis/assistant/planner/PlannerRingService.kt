package com.jarvis.assistant.planner

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.Locale

/** Short-lived foreground service: plays the alarm tone and/or speaks the reminder, then stops itself. */
class PlannerRingService : Service() {

    companion object {
        private const val TAG = "PlannerRing"
        const val ACTION_START = "com.jarvis.assistant.planner.RING_START"
        const val EXTRA_ID = "id"
        const val EXTRA_KIND = "kind"
        const val EXTRA_TITLE = "title"
        const val EXTRA_SPEAK = "speak"
        const val EXTRA_TONE = "tone"   // none | once | loop
        const val NOTIF_ID = 7101

        fun start(ctx: Context, id: String, kind: String, title: String, speak: String, tone: String) {
            val i = Intent(ctx, PlannerRingService::class.java)
            i.action = ACTION_START
            i.putExtra(EXTRA_ID, id)
            i.putExtra(EXTRA_KIND, kind)
            i.putExtra(EXTRA_TITLE, title)
            i.putExtra(EXTRA_SPEAK, speak)
            i.putExtra(EXTRA_TONE, tone)
            ContextCompat.startForegroundService(ctx, i)
        }

        fun speakOnly(ctx: Context, text: String) {
            start(ctx, "speak", "speak", "Jarvis", text, "none")
        }

        fun stop(ctx: Context) {
            try {
                ctx.stopService(Intent(ctx, PlannerRingService::class.java))
            } catch (e: Exception) {
            }
        }
    }

    private var player: MediaPlayer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pendingSpeech: String? = null
    private var wake: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private var loopTone = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getStringExtra(EXTRA_ID) ?: ""
        val kind = intent?.getStringExtra(EXTRA_KIND) ?: ""
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "Jarvis"
        val speakText = intent?.getStringExtra(EXTRA_SPEAK) ?: ""
        val tone = intent?.getStringExtra(EXTRA_TONE) ?: "once"

        PlannerNotifier.ensureChannels(this)
        val withActions = kind == Kind.ALARM || kind == Kind.TIMER || kind == Kind.REMINDER
        val notification = PlannerNotifier.build(
            this, title, if (speakText.isBlank()) title else speakText, id, kind, PlannerNotifier.CH_RING, withActions
        )
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(NOTIF_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed: ${e.message}")
            stopSelf()
            return START_NOT_STICKY
        }

        handler.removeCallbacksAndMessages(null)
        acquireWake()
        stopMedia()
        loopTone = tone == "loop"
        if (tone != "none") playTone(loopTone)
        if (speakText.isNotBlank()) speak(speakText)

        val timeout = if (loopTone) 90_000L else 30_000L
        handler.postDelayed({ stopSelf() }, timeout)
        return START_NOT_STICKY
    }

    private fun acquireWake() {
        try {
            if (wake?.isHeld == true) return
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            val wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "jarvis:planner-ring")
            wl.acquire(100_000L)
            wake = wl
        } catch (e: Exception) {
        }
    }

    private fun playTone(loop: Boolean) {
        try {
            val type = if (loop) RingtoneManager.TYPE_ALARM else RingtoneManager.TYPE_NOTIFICATION
            val uri = RingtoneManager.getDefaultUri(type)
            val mp = MediaPlayer()
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(if (loop) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            mp.setDataSource(this, uri)
            mp.isLooping = loop
            mp.prepare()
            mp.start()
            player = mp
        } catch (e: Exception) {
            Log.e(TAG, "playTone failed: ${e.message}")
        }
    }

    private fun speak(text: String) {
        pendingSpeech = text
        if (tts == null) {
            tts = TextToSpeech(applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    ttsReady = true
                    handler.post { speakPending() }
                }
            }
        } else if (ttsReady) {
            speakPending()
        }
    }

    private fun speakPending() {
        val text = pendingSpeech ?: return
        val engine = tts ?: return
        pendingSpeech = null
        try {
            var locale: Locale = Locale.getDefault()
            val hasBengali = text.any { it in '\u0980'..'\u09FF' }
            if (hasBengali) {
                val bn = Locale("bn", "BD")
                if (engine.isLanguageAvailable(bn) >= TextToSpeech.LANG_AVAILABLE) locale = bn
            }
            engine.setLanguage(locale)
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    if (!loopTone) handler.postDelayed({ stopSelf() }, 1200L)
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    if (!loopTone) handler.post { stopSelf() }
                }
            })
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "planner_speech")
        } catch (e: Exception) {
            Log.e(TAG, "speak failed: ${e.message}")
        }
    }

    private fun stopMedia() {
        try {
            player?.stop()
        } catch (e: Exception) {
        }
        try {
            player?.release()
        } catch (e: Exception) {
        }
        player = null
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        stopMedia()
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
        }
        tts = null
        try {
            if (wake?.isHeld == true) wake?.release()
        } catch (e: Exception) {
        }
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
        }
        super.onDestroy()
    }
}
