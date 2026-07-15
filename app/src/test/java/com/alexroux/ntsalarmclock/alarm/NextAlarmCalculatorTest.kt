package com.alexroux.ntsalarmclock.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId

class NextAlarmCalculatorTest {
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
