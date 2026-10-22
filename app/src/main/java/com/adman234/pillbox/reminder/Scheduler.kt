package com.adman234.pillbox.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.adman234.pillbox.MainActivity
import com.adman234.pillbox.data.DoseEvent
import com.adman234.pillbox.data.DoseStatus
import com.adman234.pillbox.data.Medication
import com.adman234.pillbox.data.NEVER
import com.adman234.pillbox.data.PillboxDb
import com.adman234.pillbox.data.ReminderType
import com.adman234.pillbox.data.SettingsRepo
import com.adman234.pillbox.engine.ScheduleEngine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneId

/**
 * Owns the dose timeline. Every mutation goes through one mutex so alarms, notification
 * buttons and the UI can never fight over the same dose.
 *
 * The model: dose events are generated a few days ahead from each medication's reminder
 * rules. Each pending dose carries a nextFireAt time and has exactly one AlarmManager alarm
 * armed for it. When the alarm fires, [reconcile] runs, notices the dose is due and shows it.
 */
class Scheduler(
    private val ctx: Context,
    private val db: PillboxDb,
    private val settings: SettingsRepo,
) {
    private val mutex = Mutex()
    private val am = ctx.getSystemService(AlarmManager::class.java)

    companion object {
        const val HORIZON_MS = 72L * 60 * 60 * 1000
        const val MISSED_GRACE_MS = 4L * 60 * 60 * 1000
        const val MAX_REPEATS = 12
        const val ACTION_RECONCILE = "com.adman234.pillbox.RECONCILE"
        const val ACTION_FIRE = "com.adman234.pillbox.FIRE"
        private const val RC_REFILL = 1
    }

    /** Bring alarms and dose events up to date. [rebuildFuture] discards and regenerates future doses. */
    suspend fun reconcile(rebuildFuture: Boolean = false) = mutex.withLock {
        reconcileLocked(System.currentTimeMillis(), rebuildFuture)
    }

    suspend fun onMedicationChanged(medId: Long) = mutex.withLock {
        val now = System.currentTimeMillis()
        val ids = db.doses().futurePendingIds(medId, now)
        ids.forEach { cancelAlarm(it); Notifier.dismiss(ctx, it) }
        db.doses().deleteIds(ids)
        reconcileLocked(now, false)
    }

    suspend fun onMedicationDeleted(medId: Long) = mutex.withLock {
        db.doses().pendingIdsForMed(medId).forEach { cancelAlarm(it); Notifier.dismiss(ctx, it) }
    }

    suspend fun act(eventId: Long, action: DoseAction) = mutex.withLock {
        val e = db.doses().get(eventId) ?: return@withLock
        val now = System.currentTimeMillis()
        when (action) {
            DoseAction.TAKE -> finish(e, DoseStatus.TAKEN, now)
            DoseAction.SKIP -> finish(e, DoseStatus.SKIPPED, now)
            DoseAction.SNOOZE -> {
                if (e.status != DoseStatus.PENDING) return@withLock
                val s = settings.current()
                val upd = e.copy(nextFireAt = now + s.snoozeMinutes * 60_000L, fireCount = 0)
                db.doses().update(upd)
                Notifier.dismiss(ctx, e.id)
                db.meds().get(e.medId)?.let { arm(upd, it) }
            }
        }
    }

    /** Log a dose that was not scheduled ("take now", as-needed medication). */
    suspend fun logExtraDose(medId: Long, at: Long = System.currentTimeMillis()) = mutex.withLock {
        db.doses().insertReplace(
            DoseEvent(
                medId = medId, scheduledAt = at, status = DoseStatus.TAKEN,
                actedAt = at, nextFireAt = NEVER, extra = true,
            ),
        )
    }

    /** Edit from the history screen. */
    suspend fun setStatus(eventId: Long, status: DoseStatus) = mutex.withLock {
        val e = db.doses().get(eventId) ?: return@withLock
        val acted = e.actedAt ?: System.currentTimeMillis()
        db.doses().update(e.copy(status = status, actedAt = if (status == DoseStatus.MISSED) null else acted, nextFireAt = NEVER))
        cancelAlarm(e.id)
        Notifier.dismiss(ctx, e.id)
    }

    suspend fun deleteEvent(eventId: Long) = mutex.withLock {
        cancelAlarm(eventId)
        Notifier.dismiss(ctx, eventId)
        db.doses().delete(eventId)
    }

    // ---------------------------------------------------------------------------------------

    private suspend fun finish(e: DoseEvent, status: DoseStatus, now: Long) {
        db.doses().update(e.copy(status = status, actedAt = now, nextFireAt = NEVER))
        cancelAlarm(e.id)
        Notifier.dismiss(ctx, e.id)
    }

    private suspend fun reconcileLocked(now: Long, rebuildFuture: Boolean) {
        val s = settings.current()
        val zone = ZoneId.systemDefault()
        Notifier.ensureChannels(ctx, s)

        if (rebuildFuture) {
            val ids = db.doses().allFuturePendingIds(now)
            ids.forEach { cancelAlarm(it) }
            db.doses().deleteIds(ids)
        }

        // 1. Generate doses for the coming days.
        val meds = db.meds().getAll().associateBy { it.id }
        for (m in meds.values) {
            if (!m.reminderEnabled) continue
            val from = maxOf(m.reminderSince, now - MISSED_GRACE_MS)
            val times = ScheduleEngine.occurrences(
                m.spec(), Instant.ofEpochMilli(from), Instant.ofEpochMilli(now + HORIZON_MS), zone, s.dstAdjust,
            )
            for (t in times) {
                db.doses().insertIgnore(DoseEvent(medId = m.id, scheduledAt = t.toEpochMilli(), nextFireAt = t.toEpochMilli()))
            }
        }

        // 2. Doses that were never handled become "missed" once the next dose is due or the grace period ends.
        val pending = db.doses().pending()
        val scheduledByMed = pending.groupBy({ it.medId }, { it.scheduledAt })
        for (e in pending) {
            val base = if (e.nextFireAt == NEVER) e.scheduledAt else maxOf(e.scheduledAt, e.nextFireAt)
            val next = scheduledByMed[e.medId].orEmpty().filter { it > e.scheduledAt }.minOrNull() ?: NEVER
            if (now >= minOf(base + MISSED_GRACE_MS, next)) {
                db.doses().update(e.copy(status = DoseStatus.MISSED, nextFireAt = NEVER))
                cancelAlarm(e.id)
                Notifier.dismiss(ctx, e.id)
            }
        }

        // 3. Fire what is due, arm what is coming.
        for (e in db.doses().pending()) {
            val med = meds[e.medId] ?: continue
            if (!med.reminderEnabled || e.nextFireAt == NEVER) {
                cancelAlarm(e.id)
                continue
            }
            if (e.nextFireAt <= now) fire(e, med, s, now) else arm(e, med)
        }

        // 4. Come back later to extend the horizon, even if nothing else wakes us.
        scheduleRefill(now)
    }

    private suspend fun fire(e: DoseEvent, med: Medication, s: com.adman234.pillbox.data.Settings, now: Long) {
        Notifier.show(ctx, med, e, db.doses().lastTaken(med.id), s)
        val count = e.fireCount + 1
        val next = if (s.autoRepeat && count <= MAX_REPEATS) now + s.repeatMinutes * 60_000L else NEVER
        val upd = e.copy(fireCount = count, nextFireAt = next)
        db.doses().update(upd)
        if (next != NEVER) arm(upd, med)
    }

    private fun firePi(eventId: Long): PendingIntent = PendingIntent.getBroadcast(
        ctx,
        1000 + eventId.toInt(),
        Intent(ctx, AlarmReceiver::class.java).setAction(ACTION_FIRE).putExtra("event", eventId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun arm(e: DoseEvent, med: Medication) {
        val pi = firePi(e.id)
        try {
            when {
                med.reminderType == ReminderType.ALARM -> {
                    val show = PendingIntent.getActivity(
                        ctx, 0, Intent(ctx, MainActivity::class.java),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    )
                    am.setAlarmClock(AlarmManager.AlarmClockInfo(e.nextFireAt, show), pi)
                }
                canExact() -> am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, e.nextFireAt, pi)
                else -> am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, e.nextFireAt, pi)
            }
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, e.nextFireAt, pi)
        }
    }

    private fun cancelAlarm(eventId: Long) = am.cancel(firePi(eventId))

    private fun canExact() = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()

    private fun scheduleRefill(now: Long) {
        val pi = PendingIntent.getBroadcast(
            ctx, RC_REFILL,
            Intent(ctx, SystemEventReceiver::class.java).setAction(ACTION_RECONCILE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, now + 12L * 60 * 60 * 1000, pi)
    }
}
