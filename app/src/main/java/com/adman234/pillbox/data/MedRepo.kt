package com.adman234.pillbox.data

import com.adman234.pillbox.reminder.Scheduler
import java.time.LocalDate

/** Medication writes that must also keep the reminder timeline in step. */
class MedRepo(private val db: PillboxDb, private val scheduler: Scheduler) {

    /** Insert or update, and rebuild this medication's upcoming doses. Returns the id. */
    suspend fun save(m: Medication): Long {
        val toSave = m.copy(reminderSince = System.currentTimeMillis())
        val id = if (m.id == 0L) db.meds().insert(toSave) else { db.meds().update(toSave); m.id }
        scheduler.onMedicationChanged(id)
        return id
    }

    suspend fun setReminderEnabled(m: Medication, enabled: Boolean) {
        val restart = enabled && m.durationDays > 0 &&
            LocalDate.now().toEpochDay() >= m.startEpochDay + m.durationDays
        save(
            m.copy(
                reminderEnabled = enabled,
                // Turning a finished course back on starts a new one today.
                startEpochDay = if (restart) LocalDate.now().toEpochDay() else m.startEpochDay,
            ),
        )
    }

    suspend fun delete(m: Medication) {
        scheduler.onMedicationDeleted(m.id)
        db.meds().delete(m)
    }
}
