package com.adman234.pillbox.reminder

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.adman234.pillbox.app
import kotlinx.coroutines.launch

/**
 * Plays the alarm sound and vibration for alarm-type reminders until the dose is handled,
 * or for [RING_LIMIT_MS] at most (auto repeat, if on, brings it back).
 */
class AlarmService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val active = linkedSetOf<Long>()
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val timeout = Runnable { giveUpRinging() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getLongExtra(EXTRA_EVENT, -1) ?: -1
        when (intent?.action) {
            ACTION_DISMISS -> {
                active.remove(id)
                if (active.isEmpty()) stopRinging()
                return START_NOT_STICKY
            }
            else -> if (id >= 0) ring(id, startId)
        }
        return START_NOT_STICKY
    }

    private fun ring(eventId: Long, startId: Int) {
        // Start the foreground state right away; the service has only a few seconds to do so.
        startForegroundCompat(eventId, placeholder(eventId))
        active += eventId
        app.appScope.launch {
            val db = app.db
            val e = db.doses().get(eventId)
            val med = e?.let { db.meds().get(it.medId) }
            if (e == null || med == null) {
                handler.post { active.remove(eventId); if (active.isEmpty()) stopRinging() }
                return@launch
            }
            val s = app.settings.current()
            val n = Notifier.build(this@AlarmService, med, e, db.doses().lastTaken(med.id), s)
            handler.post {
                startForegroundCompat(eventId, n)
                startSound(s.alarmSound, s.alarmVibrate)
                handler.removeCallbacks(timeout)
                handler.postDelayed(timeout, RING_LIMIT_MS)
            }
        }
    }

    private fun placeholder(eventId: Long) =
        androidx.core.app.NotificationCompat.Builder(this, Notifier.CH_ALARM)
            .setSmallIcon(com.adman234.pillbox.R.drawable.ic_stat_pill)
            .setContentTitle("Medication reminder")
            .setCategory(androidx.core.app.NotificationCompat.CATEGORY_ALARM)
            .build()

    private fun startForegroundCompat(eventId: Long, n: android.app.Notification) {
        val id = Notifier.notifId(eventId)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(id, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(id, n)
        }
    }

    private fun startSound(sound: String, vibrate: Boolean) {
        if (player == null) {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val uris = listOfNotNull(Notifier.soundUri(sound, alarm = true), Notifier.defaultUri(true))
                .takeIf { sound != com.adman234.pillbox.data.SOUND_SILENT } ?: emptyList()
            for (uri in uris) {
                try {
                    player = MediaPlayer().apply {
                        setAudioAttributes(attrs)
                        setDataSource(this@AlarmService, uri)
                        isLooping = true
                        prepare()
                        start()
                    }
                    break
                } catch (_: Exception) {
                    player?.release()
                    player = null
                }
            }
        }
        if (vibrate && vibrator == null) {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(VibratorManager::class.java).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as Vibrator
            }
            val effect = VibrationEffect.createWaveform(longArrayOf(0, 600, 400), 0)
            @Suppress("DEPRECATION")
            vibrator?.vibrate(
                effect,
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
            )
        }
    }

    /** Nobody answered: go quiet but leave a normal notification so the dose is not forgotten. */
    private fun giveUpRinging() {
        val ids = active.toList()
        app.appScope.launch {
            val s = app.settings.current()
            for (id in ids) {
                val e = app.db.doses().get(id) ?: continue
                val med = app.db.meds().get(e.medId) ?: continue
                handler.post {
                    stopRinging()
                    Notifier.post(this@AlarmService, med, e, null, s, alarm = false)
                }
            }
        }
        if (ids.isEmpty()) stopRinging()
    }

    private fun stopRinging() {
        handler.removeCallbacks(timeout)
        player?.run { runCatching { stop() }; release() }
        player = null
        vibrator?.cancel()
        vibrator = null
        active.clear()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(timeout)
        player?.release()
        vibrator?.cancel()
        isRunning = false
        super.onDestroy()
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
    }

    companion object {
        const val EXTRA_EVENT = "event"
        const val ACTION_DISMISS = "com.adman234.pillbox.DISMISS_ALARM"
        const val RING_LIMIT_MS = 2L * 60 * 1000

        @Volatile
        var isRunning = false
            private set

        fun start(ctx: Context, eventId: Long) {
            ctx.startForegroundService(
                Intent(ctx, AlarmService::class.java).putExtra(EXTRA_EVENT, eventId),
            )
        }

        fun dismiss(ctx: Context, eventId: Long) {
            if (!isRunning) return
            ctx.startService(
                Intent(ctx, AlarmService::class.java).setAction(ACTION_DISMISS).putExtra(EXTRA_EVENT, eventId),
            )
        }
    }
}
