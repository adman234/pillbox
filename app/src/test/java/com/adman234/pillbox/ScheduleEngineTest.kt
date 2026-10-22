package com.adman234.pillbox

import com.adman234.pillbox.data.DayRule
import com.adman234.pillbox.data.Freq
import com.adman234.pillbox.engine.ReminderSpec
import com.adman234.pillbox.engine.ScheduleEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class ScheduleEngineTest {
    private val la = ZoneId.of("America/Los_Angeles")
    private val utc = ZoneId.of("UTC")
    private val start = LocalDate.of(2026, 3, 1).toEpochDay()

    private fun spec(
        freq: Freq = Freq.TIMES,
        times: List<Int> = listOf(480),
        rule: DayRule = DayRule.EVERY_DAY,
        mask: Int = 127,
        n: Int = 2,
        duration: Int = 0,
        interval: Int = 480,
        iStart: Int = 480,
        iEnd: Int = -1,
        startDay: Long = start,
    ) = ReminderSpec(freq, times, interval, iStart, iEnd, rule, mask, n, startDay, duration)

    private fun run(s: ReminderSpec, from: LocalDateTime, to: LocalDateTime, zone: ZoneId = utc, dst: Boolean = true) =
        ScheduleEngine.occurrences(s, from.atZone(zone).toInstant(), to.atZone(zone).toInstant(), zone, dst)
            .map { it.atZone(zone).toLocalDateTime() }

    @Test fun dailyTwoTimes() {
        val r = run(spec(times = listOf(480, 1200)), LocalDateTime.of(2026, 3, 2, 0, 0), LocalDateTime.of(2026, 3, 4, 0, 0))
        assertEquals(4, r.size)
        assertEquals(LocalDateTime.of(2026, 3, 2, 8, 0), r[0])
        assertEquals(LocalDateTime.of(2026, 3, 3, 20, 0), r[3])
    }

    @Test fun fromIsInclusiveToIsExclusive() {
        val r = run(spec(), LocalDateTime.of(2026, 3, 2, 8, 0), LocalDateTime.of(2026, 3, 3, 8, 0))
        assertEquals(listOf(LocalDateTime.of(2026, 3, 2, 8, 0)), r)
    }

    @Test fun specificWeekdays() {
        // Monday and Thursday only. 2026-03-02 is a Monday.
        val mask = (1 shl 0) or (1 shl 3)
        val r = run(spec(rule = DayRule.WEEKDAYS, mask = mask), LocalDateTime.of(2026, 3, 2, 0, 0), LocalDateTime.of(2026, 3, 9, 0, 0))
        assertEquals(listOf(2, 5), r.map { it.dayOfMonth })
    }

    @Test fun everyThirdDayAnchoredToStart() {
        val r = run(spec(rule = DayRule.EVERY_N, n = 3), LocalDateTime.of(2026, 3, 1, 0, 0), LocalDateTime.of(2026, 3, 11, 0, 0))
        assertEquals(listOf(1, 4, 7, 10), r.map { it.dayOfMonth })
    }

    @Test fun durationStopsAfterNDays() {
        val r = run(spec(duration = 3), LocalDateTime.of(2026, 3, 1, 0, 0), LocalDateTime.of(2026, 3, 20, 0, 0))
        assertEquals(listOf(1, 2, 3), r.map { it.dayOfMonth })
    }

    @Test fun nothingBeforeStartDay() {
        val r = run(spec(startDay = start + 5), LocalDateTime.of(2026, 3, 1, 0, 0), LocalDateTime.of(2026, 3, 10, 0, 0))
        assertEquals(listOf(6, 7, 8, 9), r.map { it.dayOfMonth })
    }

    @Test fun intervalRoundTheClockStaysOnGrid() {
        // every 8 h from 08:00 => 08:00, 16:00, 24:00 (next day 00:00)
        val s = spec(freq = Freq.INTERVAL, interval = 480, iStart = 480)
        val r = run(s, LocalDateTime.of(2026, 3, 2, 0, 0), LocalDateTime.of(2026, 3, 3, 0, 0))
        assertEquals(
            listOf(
                LocalDateTime.of(2026, 3, 2, 0, 0), // grid from the previous day
                LocalDateTime.of(2026, 3, 2, 8, 0),
                LocalDateTime.of(2026, 3, 2, 16, 0),
            ),
            r,
        )
    }

    @Test fun intervalWithLastDoseLimit() {
        // every 4 h from 08:00 until 20:00 => 08, 12, 16, 20
        val s = spec(freq = Freq.INTERVAL, interval = 240, iStart = 480, iEnd = 1200)
        val r = run(s, LocalDateTime.of(2026, 3, 2, 0, 0), LocalDateTime.of(2026, 3, 3, 0, 0))
        assertEquals(listOf(8, 12, 16, 20), r.map { it.hour })
    }

    @Test fun dstOnKeepsClockTimeAcrossSpringForward() {
        // US DST began 2026-03-08. 08:00 on the 7th and 8th are only 23 h apart.
        val r = ScheduleEngine.occurrences(
            spec(), LocalDateTime.of(2026, 3, 7, 0, 0).atZone(la).toInstant(),
            LocalDateTime.of(2026, 3, 9, 0, 0).atZone(la).toInstant(), la, dstAdjust = true,
        )
        assertEquals(2, r.size)
        assertEquals(Duration.ofHours(23), Duration.between(r[0], r[1]))
        assertEquals(8, r[1].atZone(la).hour)
    }

    @Test fun dstOffKeepsElapsedTimeAcrossSpringForward() {
        val r = ScheduleEngine.occurrences(
            spec(), LocalDateTime.of(2026, 3, 7, 0, 0).atZone(la).toInstant(),
            LocalDateTime.of(2026, 3, 9, 0, 0).atZone(la).toInstant(), la, dstAdjust = false,
        )
        assertEquals(2, r.size)
        assertEquals(Duration.ofHours(24), Duration.between(r[0], r[1]))
        assertEquals(9, r[1].atZone(la).hour)
    }

    @Test fun timeInSpringForwardGapStillFires() {
        // 02:30 does not exist on 2026-03-08 in Los Angeles; the dose must not vanish.
        val r = ScheduleEngine.occurrences(
            spec(times = listOf(150)), LocalDateTime.of(2026, 3, 8, 0, 0).atZone(la).toInstant(),
            LocalDateTime.of(2026, 3, 9, 0, 0).atZone(la).toInstant(), la, dstAdjust = true,
        )
        assertEquals(1, r.size)
        assertTrue(r[0].atZone(la).hour >= 3)
    }

    @Test fun fallBackRepeatedHourFiresOnce() {
        // US DST ended 2026-11-01; 01:30 happens twice but the reminder should fire once.
        val s = spec(times = listOf(90), startDay = LocalDate.of(2026, 10, 1).toEpochDay())
        val r = ScheduleEngine.occurrences(
            s, LocalDateTime.of(2026, 11, 1, 0, 0).atZone(la).toInstant(),
            LocalDateTime.of(2026, 11, 2, 0, 0).atZone(la).toInstant(), la, dstAdjust = true,
        )
        assertEquals(1, r.size)
    }
}
