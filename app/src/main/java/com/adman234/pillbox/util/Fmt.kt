package com.adman234.pillbox.util

import com.adman234.pillbox.data.DayRule
import com.adman234.pillbox.data.Freq
import com.adman234.pillbox.data.Medication
import com.adman234.pillbox.data.ReminderType
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

object Fmt {
    private val timeFmt: DateTimeFormatter get() = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    private val dateFmt: DateTimeFormatter get() = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

    fun minuteOfDay(min: Int): String =
        LocalTime.of((min / 60) % 24, min % 60).format(timeFmt)

    fun time(ms: Long): String =
        Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalTime().format(timeFmt)

    fun date(day: LocalDate): String = day.format(dateFmt)

    fun dayLabel(day: LocalDate, today: LocalDate = LocalDate.now()): String = when (day) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        today.plusDays(1) -> "Tomorrow"
        else -> day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()) + ", " + date(day)
    }

    /** "Today, 8:02 AM" */
    fun dateTime(ms: Long): String {
        val z = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
        return dayLabel(z.toLocalDate()) + ", " + z.toLocalTime().format(timeFmt)
    }

    fun duration(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0 -> "$m min"
            m == 0 -> "$h h"
            else -> "$h h $m min"
        }
    }

    fun weekdays(mask: Int): String =
        DayOfWeek.values().filter { mask and (1 shl (it.value - 1)) != 0 }
            .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }

    fun daysText(m: Medication): String = when (m.dayRule) {
        DayRule.EVERY_DAY -> "Every day"
        DayRule.WEEKDAYS -> weekdays(m.weekdayMask)
        DayRule.EVERY_N -> "Every ${m.everyN} days"
    }

    fun summary(m: Medication): String {
        if (!m.reminderEnabled) return "Reminder off"
        val what = when (m.freq) {
            Freq.TIMES -> m.times().joinToString(", ") { minuteOfDay(it) }
            Freq.INTERVAL -> buildString {
                append("Every ").append(duration(m.intervalMinutes)).append(" from ").append(minuteOfDay(m.intervalStartMin))
                if (m.intervalEndMin > m.intervalStartMin) append(" to ").append(minuteOfDay(m.intervalEndMin))
            }
        }
        val parts = mutableListOf(daysText(m), what)
        parts += if (m.reminderType == ReminderType.ALARM) "Alarm" else "Notification"
        if (m.durationDays > 0) parts += "${m.durationDays} days"
        return parts.joinToString(" · ")
    }
}
