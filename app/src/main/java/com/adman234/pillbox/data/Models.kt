package com.adman234.pillbox.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.adman234.pillbox.engine.ReminderSpec
import java.time.LocalDate

enum class PillShape { ROUND, OVAL, CAPSULE, SQUARE, DIAMOND, HEXAGON, DROP, BOTTLE }
enum class Freq { TIMES, INTERVAL }
enum class DayRule { EVERY_DAY, WEEKDAYS, EVERY_N }
enum class ReminderType { NOTIFICATION, ALARM }
enum class DoseStatus { PENDING, TAKEN, SKIPPED, MISSED }

const val NEVER = Long.MAX_VALUE

@Entity(tableName = "medication")
data class Medication(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    /** Optional free text such as "2 tablets" or "500 mg". */
    val amount: String = "",
    val shape: PillShape = PillShape.ROUND,
    val color: Int = 0xFF1F6F78.toInt(),
    val reminderEnabled: Boolean = true,
    val freq: Freq = Freq.TIMES,
    /** Comma separated minutes after midnight, e.g. "480,1200". */
    val timesMinutes: String = "480",
    val intervalMinutes: Int = 480,
    val intervalStartMin: Int = 480,
    /** Minutes after midnight of the last dose of the day, or -1 for a round-the-clock grid. */
    val intervalEndMin: Int = -1,
    val dayRule: DayRule = DayRule.EVERY_DAY,
    /** Bit 0 = Monday ... bit 6 = Sunday. */
    val weekdayMask: Int = 127,
    val everyN: Int = 2,
    val reminderType: ReminderType = ReminderType.NOTIFICATION,
    /** 0 = continuous. */
    val durationDays: Int = 0,
    val startEpochDay: Long = LocalDate.now().toEpochDay(),
    /** No reminders are generated for times before this instant. */
    val reminderSince: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun times(): List<Int> =
        timesMinutes.split(',').mapNotNull { it.trim().toIntOrNull() }.sorted().distinct()

    fun spec() = ReminderSpec(
        freq = freq,
        timesMinutes = times(),
        intervalMinutes = intervalMinutes,
        intervalStartMin = intervalStartMin,
        intervalEndMin = intervalEndMin,
        dayRule = dayRule,
        weekdayMask = weekdayMask,
        everyN = everyN,
        startEpochDay = startEpochDay,
        durationDays = durationDays,
    )
}

@Entity(
    tableName = "dose_event",
    foreignKeys = [ForeignKey(
        entity = Medication::class,
        parentColumns = ["id"],
        childColumns = ["medId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["medId", "scheduledAt"], unique = true), Index("scheduledAt")],
)
data class DoseEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medId: Long,
    val scheduledAt: Long,
    val status: DoseStatus = DoseStatus.PENDING,
    val actedAt: Long? = null,
    /** When the next alert for this pending dose should fire, or [NEVER]. */
    val nextFireAt: Long = scheduledAt,
    val fireCount: Int = 0,
    /** True for doses logged on the spot ("take now") rather than scheduled. */
    val extra: Boolean = false,
)

data class LastTaken(val medId: Long, val at: Long?)
