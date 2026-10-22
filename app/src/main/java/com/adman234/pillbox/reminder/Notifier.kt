package com.adman234.pillbox.reminder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.core.app.NotificationCompat
import com.adman234.pillbox.MainActivity
import com.adman234.pillbox.R
import com.adman234.pillbox.data.DoseEvent
import com.adman234.pillbox.data.Medication
import com.adman234.pillbox.data.ReminderType
import com.adman234.pillbox.data.SOUND_DEFAULT
import com.adman234.pillbox.data.SOUND_SILENT
import com.adman234.pillbox.data.Settings
import com.adman234.pillbox.util.Fmt
import kotlin.math.abs

enum class DoseAction { TAKE, SKIP, SNOOZE }

object Notifier {
    const val CH_ALARM = "alarm_service"
    const val CH_FEEDBACK = "feedback"
    private const val CH_PREFIX = "rem_"
    private const val FEEDBACK_MS = 3_000L
    private const val FEEDBACK_ID_OFFSET = 1_000_000
    const val TEST_ID = 9_999

    /** Fixed accent: a pill color such as white or cream would vanish against the shade. */
    private const val ACCENT = 0xFFFF7A59.toInt()

    fun notifId(eventId: Long): Int = (10_000 + eventId).toInt()

    fun soundUri(value: String, alarm: Boolean): Uri? = when (value) {
        SOUND_SILENT -> null
        SOUND_DEFAULT -> defaultUri(alarm)
        else -> Uri.parse(value)
    }

    fun defaultUri(alarm: Boolean): Uri =
        if (alarm) RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        else AndroidSettings.System.DEFAULT_NOTIFICATION_URI

    /** Channel ids carry a hash of sound and vibration, because Android freezes those once a channel exists. */
    fun reminderChannelId(s: Settings) = CH_PREFIX + abs((s.notifSound + "|" + s.notifVibrate).hashCode())

    fun ensureChannels(ctx: Context, s: Settings) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val id = reminderChannelId(s)
        nm.notificationChannels.filter { it.id.startsWith(CH_PREFIX) && it.id != id }
            .forEach { nm.deleteNotificationChannel(it.id) }

        val reminders = NotificationChannel(id, "Medication reminders", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Reminders to take your medication"
            val uri = soundUri(s.notifSound, alarm = false)
            if (uri == null) {
                setSound(null, null)
            } else {
                setSound(
                    uri,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
            }
            enableVibration(s.notifVibrate)
            if (s.notifVibrate) vibrationPattern = longArrayOf(0, 400, 200, 400)
            setBypassDnd(false)
        }
        nm.createNotificationChannel(reminders)

        // The alarm sound is played by AlarmService itself, so the channel stays silent.
        val alarm = NotificationChannel(CH_ALARM, "Medication alarms", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Full screen alarms for medication"
            setSound(null, null)
            enableVibration(false)
        }
        nm.createNotificationChannel(alarm)

        val feedback = NotificationChannel(CH_FEEDBACK, "Confirmations", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Brief confirmation after you tap Take, Skip or Snooze"
            setSound(null, null)
            enableVibration(false)
        }
        nm.createNotificationChannel(feedback)
    }

    /** A quiet confirmation that replaces the reminder for a few seconds, then disappears. */
    fun feedback(ctx: Context, eventId: Long, message: String, medName: String, color: Int) {
        val n = NotificationCompat.Builder(ctx, CH_FEEDBACK)
            .setSmallIcon(R.drawable.ic_stat_pill)
            .setColor(ACCENT)
            .setContentTitle(message)
            .setContentText(medName)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setTimeoutAfter(FEEDBACK_MS)
            .build()
        try {
            ctx.getSystemService(NotificationManager::class.java).notify(notifId(eventId) + FEEDBACK_ID_OFFSET, n)
        } catch (_: SecurityException) {
        }
    }

    /** Sent from Settings to check sound, vibration and permissions without waiting for a real dose. */
    fun postTest(ctx: Context, s: Settings) {
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, reminderChannelId(s))
            .setSmallIcon(R.drawable.ic_stat_pill)
            .setContentTitle("Test reminder")
            .setContentText("If you can see and hear this, notification reminders work.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
            .setTimeoutAfter(30_000)
            .build()
        try {
            ctx.getSystemService(NotificationManager::class.java).notify(TEST_ID, n)
        } catch (_: SecurityException) {
        }
    }

    fun build(
        ctx: Context,
        med: Medication,
        e: DoseEvent,
        lastTaken: Long?,
        s: Settings,
        alarm: Boolean = med.reminderType == ReminderType.ALARM,
    ): Notification {
        val channel = if (alarm) CH_ALARM else reminderChannelId(s)
        val text = buildString {
            if (med.amount.isNotBlank()) append(med.amount).append('\n')
            append("Due at ").append(Fmt.time(e.scheduledAt))
            if (s.showLastDose) {
                append('\n').append("Last taken: ")
                append(if (lastTaken != null) Fmt.dateTime(lastTaken) else "never")
            }
        }
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_stat_pill)
            .setColor(ACCENT)
            .setContentTitle("Time to take ${med.name}")
            .setContentText(text.lineSequence().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setWhen(e.scheduledAt)
            .setShowWhen(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(if (alarm) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(open)
            // Swiping the notification away counts as a snooze, without the confirmation.
            .setDeleteIntent(swipePi(ctx, e.id))
            .addAction(0, "Take", actionPi(ctx, e.id, DoseAction.TAKE))
            .addAction(0, "Skip", actionPi(ctx, e.id, DoseAction.SKIP))
            .addAction(0, "Snooze ${s.snoozeMinutes}m", actionPi(ctx, e.id, DoseAction.SNOOZE))
        if (alarm) {
            val full = PendingIntent.getActivity(
                ctx, notifId(e.id),
                AlarmActivity.intent(ctx, e.id),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            b.setFullScreenIntent(full, true).setOngoing(true)
        }
        return b.build()
    }

    private fun swipePi(ctx: Context, eventId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            ctx,
            (eventId * 4 + 3).toInt(),
            Intent(ctx, ActionReceiver::class.java)
                .setAction("com.adman234.pillbox.SWIPED")
                .putExtra(ActionReceiver.EXTRA_EVENT, eventId)
                .putExtra(ActionReceiver.EXTRA_ACTION, DoseAction.SNOOZE.name)
                .putExtra(ActionReceiver.EXTRA_FEEDBACK, false),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    fun actionPi(ctx: Context, eventId: Long, action: DoseAction): PendingIntent =
        PendingIntent.getBroadcast(
            ctx,
            (eventId * 4 + action.ordinal).toInt(),
            Intent(ctx, ActionReceiver::class.java)
                .setAction("com.adman234.pillbox.${action.name}")
                .putExtra(ActionReceiver.EXTRA_EVENT, eventId)
                .putExtra(ActionReceiver.EXTRA_ACTION, action.name),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    fun show(ctx: Context, med: Medication, e: DoseEvent, lastTaken: Long?, s: Settings) {
        if (med.reminderType == ReminderType.ALARM) {
            try {
                AlarmService.start(ctx, e.id)
                return
            } catch (_: Exception) {
                // Not allowed to start a foreground service right now; fall back to a loud notification.
            }
        }
        post(ctx, med, e, lastTaken, s)
    }

    fun post(
        ctx: Context, med: Medication, e: DoseEvent, lastTaken: Long?, s: Settings,
        alarm: Boolean = med.reminderType == ReminderType.ALARM,
    ) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        try {
            nm.notify(notifId(e.id), build(ctx, med, e, lastTaken, s, alarm))
        } catch (_: SecurityException) {
            // Notification permission missing; the health check in Settings tells the user.
        }
    }

    fun dismiss(ctx: Context, eventId: Long) {
        ctx.getSystemService(NotificationManager::class.java).cancel(notifId(eventId))
        AlarmService.dismiss(ctx, eventId)
    }
}
