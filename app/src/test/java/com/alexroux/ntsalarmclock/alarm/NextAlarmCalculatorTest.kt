package com.alexroux.ntsalarmclock.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Tests date selection independently from Android's AlarmManager.
 *
 * An empty [DayOfWeek] set represents a one-shot alarm. A non-empty set represents a recurring
 * alarm that may fire only on those weekdays. Fixed [LocalDateTime] values make every expectation
 * deterministic and expose boundary behavior around equal times, week rollover, and calendar dates.
 */
class NextAlarmCalculatorTest {
    /**
     * Given 08:00 on January 1 and a one-shot alarm for 09:00, today's candidate is still in the
     * future. The expected result proves the calculator keeps the same date rather than always
     * moving one-shot alarms to tomorrow.
     */
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

    /**
     * Given 10:00 and a requested one-shot time of 09:00, today's candidate is already one hour in
     * the past. The calculator should preserve 09:00 but advance the date by one day.
     * The empty repeat-day set confirms this is daily one-shot rollover, not weekday selection.
     */
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

    /**
     * Given a current time exactly equal to the requested time, scheduling "now" risks an immediate
     * or missed trigger. Treating equality as elapsed and selecting tomorrow establishes that only a
     * strictly future candidate is valid.
     */
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

    /**
     * Given 23:59 on February 28, 2024 and a requested 00:01, the next date is February 29 because
     * 2024 is a leap year. This verifies rollover uses calendar arithmetic instead of manually
     * assuming February always has 28 days.
     */
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

    /**
     * January 1, 2024 is Monday, Monday is enabled, and 09:00 is later than the current 08:00.
     * The result should be today even though Wednesday is also enabled, proving the closest valid
     * occurrence wins.
     */
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

    /**
     * From Monday after the 09:00 time has passed, Wednesday is the nearest enabled day and Friday is
     * farther away. The input set lists Friday first, so selecting Wednesday proves calculation is
     * based on chronological distance rather than set iteration order.
     */
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

    /**
     * From Wednesday with only Monday enabled, the next valid occurrence is five days later on
     * January 8. This proves the weekday search wraps through the end of the current week instead of
     * returning no result when the enabled day has already passed.
     */
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

    /**
     * It is Monday at exactly 09:00 and Monday is the only enabled repeat day. As with one-shot
     * alarms, equality is not strictly future, so the next valid Monday is January 8—seven days later.
     * This protects against an immediate duplicate firing at the same clock instant.
     */
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

    /**
     * From Saturday morning, the next enabled Sunday is tomorrow. This small boundary protects the
     * mapping between Java's Monday-through-Sunday enum values and the calculator's day offsets.
     * The expected January 7 date makes the one-day rollover explicit.
     */
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

    /**
     * From late Tuesday on December 31, 2024, the next enabled Wednesday is January 1, 2025 at 08:15.
     * The assertion proves year rollover is handled by date arithmetic and does not lose the requested
     * hour or minute.
     */
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

    /**
     * AlarmManager consumes epoch milliseconds rather than [LocalDateTime]. The expected value is
     * independently built by attaching the system time zone to 09:00 and converting it to an instant.
     * Equality proves the convenience function selects the same date/time and performs the zone-aware
     * conversion correctly; the non-null assertion also documents that a valid schedule yields a value.
     */
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
