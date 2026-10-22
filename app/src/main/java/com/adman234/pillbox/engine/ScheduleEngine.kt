package com.adman234.pillbox.engine

import com.adman234.pillbox.data.DayRule
import com.adman234.pillbox.data.Freq
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/** Everything the engine needs to know about a reminder. No Android types on purpose. */
data class ReminderSpec(
    val freq: Freq,
    val timesMinutes: List<Int>,
    val intervalMinutes: Int,
    val intervalStartMin: Int,
    val intervalEndMin: Int,
    val dayRule: DayRule,
    val weekdayMask: Int,
    val everyN: Int,
    val startEpochDay: Long,
    val durationDays: Int,
)

object ScheduleEngine {

    /**
     * All dose instants in [from, to), in the order they happen.
     *
     * Times are wall-clock times in [zone]. With [dstAdjust] on, 08:00 stays 08:00 when
     * daylight saving starts or ends. With it off, the reminder keeps its standard-time
     * offset, so the clock time shifts by an hour instead.
     */
    fun occurrences(
        spec: ReminderSpec,
        from: Instant,
        to: Instant,
        zone: ZoneId,
        dstAdjust: Boolean,
    ): List<Instant> {
        if (!to.isAfter(from)) return emptyList()
        val out = ArrayList<Instant>()
        var day = from.atZone(zone).toLocalDate().minusDays(1)
        val last = to.atZone(zone).toLocalDate().plusDays(1)
        val minutes = minutesOfDay(spec)
        while (!day.isAfter(last)) {
            if (dayActive(spec, day)) {
                for (m in minutes) {
                    val t = resolve(day.atStartOfDay().plusMinutes(m.toLong()), zone, dstAdjust)
                    if (!t.isBefore(from) && t.isBefore(to)) out += t
                }
            }
            day = day.plusDays(1)
        }
        return out.distinct().sorted()
    }

    /** Minutes after the start of a day on which a dose happens. May exceed 1439 for interval grids. */
    fun minutesOfDay(spec: ReminderSpec): List<Int> = when (spec.freq) {
        Freq.TIMES -> spec.timesMinutes.distinct().sorted()
        Freq.INTERVAL -> {
            val step = spec.intervalMinutes.coerceAtLeast(5)
            val start = spec.intervalStartMin
            val limit = if (spec.intervalEndMin > start) spec.intervalEndMin else start + 24 * 60 - 1
            generateSequence(start) { it + step }.takeWhile { it <= limit }.toList()
        }
    }

    fun dayActive(spec: ReminderSpec, day: LocalDate): Boolean {
        val epoch = day.toEpochDay()
        if (epoch < spec.startEpochDay) return false
        if (spec.durationDays > 0 && epoch >= spec.startEpochDay + spec.durationDays) return false
        return when (spec.dayRule) {
            DayRule.EVERY_DAY -> true
            DayRule.WEEKDAYS -> spec.weekdayMask and (1 shl (day.dayOfWeek.value - 1)) != 0
            DayRule.EVERY_N -> (epoch - spec.startEpochDay) % spec.everyN.coerceAtLeast(1) == 0L
        }
    }

    fun resolve(local: LocalDateTime, zone: ZoneId, dstAdjust: Boolean): Instant {
        val zoned = local.atZone(zone)
        if (dstAdjust) return zoned.toInstant()
        val standard: ZoneOffset = zone.rules.getStandardOffset(zoned.toInstant())
        return local.toInstant(standard)
    }

    fun weekdayBit(d: DayOfWeek) = 1 shl (d.value - 1)
}
