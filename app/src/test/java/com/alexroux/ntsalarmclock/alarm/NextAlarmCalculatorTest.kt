package com.alexroux.ntsalarmclock.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId

class NextAlarmCalculatorTest {
    /** A one-shot alarm later today should keep today's date. */
    @Test
    fun oneShotAlarm_today() {
        val next = NextAlarmCalculator.computeNextTriggerDateTime(
            now = LocalDateTime.of(2024, 1, 1, 8, 0),
            hour = 9,
            minute = 0,
            enabledDays = emptySet()
        )
        assertEquals(LocalDateTime.of(2024, 1, 1, 9, 0), next)
    }

    /** A one-shot time that has already passed should roll forward to tomorrow. */
    @Test
    fun oneShotAlarm_tomorrow() {
        val next = NextAlarmCalculator.computeNextTriggerDateTime(
            now = LocalDateTime.of(2024, 1, 1, 10, 0),
            hour = 9,
            minute = 0,
            enabledDays = emptySet()
        )
        assertEquals(LocalDateTime.of(2024, 1, 2, 9, 0), next)
    }

    /** An alarm exactly equal to the current time is treated as elapsed, avoiding an immediate trigger. */
    @Test
    fun oneShotAlarm_exactTime_schedulesTomorrow() {
        val next = NextAlarmCalculator.computeNextTriggerDateTime(
            now = LocalDateTime.of(2024, 1, 1, 9, 0),
            hour = 9,
            minute = 0,
            enabledDays = emptySet()
        )
        assertEquals(LocalDateTime.of(2024, 1, 2, 9, 0), next)
    }

    /** Rolling a one-shot alarm past February 28 should honor the leap day in a leap year. */
    @Test
    fun oneShotAlarm_handlesLeapDayRollover() {
        val next = NextAlarmCalculator.computeNextTriggerDateTime(
            now = LocalDateTime.of(2024, 2, 28, 23, 59),
            hour = 0,
            minute = 1,
            enabledDays = emptySet()
        )
        assertEquals(LocalDateTime.of(2024, 2, 29, 0, 1), next)
    }

    /** A repeating alarm may use today when today is enabled and its time is still ahead. */
    @Test
    fun repeatingAlarm_laterToday() {
        val next = NextAlarmCalculator.computeNextTriggerDateTime(
            now = LocalDateTime.of(2024, 1, 1, 8, 0),
            hour = 9,
            minute = 0,
            enabledDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)
        )
        assertEquals(LocalDateTime.of(2024, 1, 1, 9, 0), next)
    }

    /** The nearest future enabled weekday should win regardless of the set's iteration order. */
    @Test
    fun repeatingAlarm_selectsNearestEnabledDay() {
        val next = NextAlarmCalculator.computeNextTriggerDateTime(
            now = LocalDateTime.of(2024, 1, 1, 10, 0),
            hour = 9,
            minute = 0,
            enabledDays = setOf(DayOfWeek.FRIDAY, DayOfWeek.WEDNESDAY)
        )
        assertEquals(LocalDateTime.of(2024, 1, 3, 9, 0), next)
    }

    /** When no enabled day remains this week, calculation should continue into the next week. */
    @Test
    fun repeatingAlarm_nextWeek() {
        val next = NextAlarmCalculator.computeNextTriggerDateTime(
            now = LocalDateTime.of(2024, 1, 3, 10, 0),
            hour = 9,
            minute = 0,
            enabledDays = setOf(DayOfWeek.MONDAY)
        )
        assertEquals(LocalDateTime.of(2024, 1, 8, 9, 0), next)
    }

    /** A repeating alarm at the exact current time should wait for that weekday next week. */
    @Test
    fun repeatingAlarm_exactTime_schedulesSameDayNextWeek() {
        val next = NextAlarmCalculator.computeNextTriggerDateTime(
            now = LocalDateTime.of(2024, 1, 1, 9, 0),
            hour = 9,
            minute = 0,
            enabledDays = setOf(DayOfWeek.MONDAY)
        )
        assertEquals(LocalDateTime.of(2024, 1, 8, 9, 0), next)
    }

    /** Sunday should be resolved correctly when the search crosses the end of the week. */
    @Test
    fun repeatingAlarm_handlesSundayCorrectly() {
        val next = NextAlarmCalculator.computeNextTriggerDateTime(
            now = LocalDateTime.of(2024, 1, 6, 10, 0),
            hour = 9,
            minute = 0,
            enabledDays = setOf(DayOfWeek.SUNDAY)
        )
        assertEquals(LocalDateTime.of(2024, 1, 7, 9, 0), next)
    }

    /** The next enabled weekday should retain its time while crossing into a new year. */
    @Test
    fun repeatingAlarm_handlesYearChangeCorrectly() {
        val next = NextAlarmCalculator.computeNextTriggerDateTime(
            now = LocalDateTime.of(2024, 12, 31, 23, 30),
            hour = 8,
            minute = 15,
            enabledDays = setOf(DayOfWeek.WEDNESDAY)
        )
        assertEquals(LocalDateTime.of(2025, 1, 1, 8, 15), next)
    }

    /** Millisecond calculation should convert the selected local trigger through the system time zone. */
    @Test
    fun computeNextTriggerMillis_returnsExpectedValue() {
        val expected = LocalDateTime.of(2024, 1, 1, 9, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val millis = NextAlarmCalculator.computeNextTriggerMillis(
            now = LocalDateTime.of(2024, 1, 1, 8, 0),
            hour = 9,
            minute = 0,
            enabledDays = emptySet()
        )
        assertNotNull(millis)
        assertEquals(expected, millis)
    }
}
