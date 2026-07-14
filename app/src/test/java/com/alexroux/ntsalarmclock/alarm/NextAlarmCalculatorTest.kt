package com.alexroux.ntsalarmclock.alarm

import com.alexroux.ntsalarmclock.ui.components.DayOfWeekUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Pure calculation tests for choosing the next alarm time and display text.
 *
 * Every test passes a fixed `now` value so date rollover, exact-time behavior,
 * and weekday selection stay deterministic.
 */
class NextAlarmCalculatorTest {

    @Test
    fun oneShotAlarm_today() {
        // An empty day set represents a one-shot alarm rather than a weekly one.
        val now = LocalDateTime.of(2024, 1, 1, 8, 0)
        val nextTrigger = NextAlarmCalculator.computeNextTriggerDateTime(
            now = now,
            hour = 9,
            minute = 0,
            enabledDays = emptySet()
        )

        assertEquals(LocalDateTime.of(2024, 1, 1, 9, 0), nextTrigger)
    }

    @Test
    fun oneShotAlarm_tomorrow() {
        val now = LocalDateTime.of(2024, 1, 1, 10, 0)
        val nextTrigger = NextAlarmCalculator.computeNextTriggerDateTime(
            now = now,
            hour = 9,
            minute = 0,
            enabledDays = emptySet()
        )

        assertEquals(LocalDateTime.of(2024, 1, 2, 9, 0), nextTrigger)
    }

    @Test
    fun oneShotAlarm_exactTime_schedulesTomorrow() {
        val now = LocalDateTime.of(2024, 1, 1, 9, 0)
        val nextTrigger = NextAlarmCalculator.computeNextTriggerDateTime(
            now = now,
            hour = 9,
            minute = 0,
            enabledDays = emptySet()
        )

        assertEquals(LocalDateTime.of(2024, 1, 2, 9, 0), nextTrigger)
    }

    @Test
    fun repeatingAlarm_laterToday() {
        val now = LocalDateTime.of(2024, 1, 1, 8, 0)
        val nextTrigger = NextAlarmCalculator.computeNextTriggerDateTime(
            now = now,
            hour = 9,
            minute = 0,
            enabledDays = setOf(DayOfWeekUi.MO, DayOfWeekUi.WE)
        )

        assertEquals(LocalDateTime.of(2024, 1, 1, 9, 0), nextTrigger)
    }

    @Test
    fun repeatingAlarm_selectsNearestEnabledDay() {
        // Monday has already passed at the requested time, so Wednesday should
        // win over Friday even though both are enabled.
        val now = LocalDateTime.of(2024, 1, 1, 10, 0)
        val nextTrigger = NextAlarmCalculator.computeNextTriggerDateTime(
            now = now,
            hour = 9,
            minute = 0,
            enabledDays = setOf(DayOfWeekUi.FR, DayOfWeekUi.WE)
        )

        assertEquals(LocalDateTime.of(2024, 1, 3, 9, 0), nextTrigger)
    }

    @Test
    fun repeatingAlarm_nextWeek() {
        val now = LocalDateTime.of(2024, 1, 3, 10, 0)
        val nextTrigger = NextAlarmCalculator.computeNextTriggerDateTime(
            now = now,
            hour = 9,
            minute = 0,
            enabledDays = setOf(DayOfWeekUi.MO)
        )

        assertEquals(LocalDateTime.of(2024, 1, 8, 9, 0), nextTrigger)
    }

    @Test
    fun repeatingAlarm_handlesSundayCorrectly() {
        val now = LocalDateTime.of(2024, 1, 6, 10, 0)
        val nextTrigger = NextAlarmCalculator.computeNextTriggerDateTime(
            now = now,
            hour = 9,
            minute = 0,
            enabledDays = setOf(DayOfWeekUi.SU)
        )

        assertEquals(LocalDateTime.of(2024, 1, 7, 9, 0), nextTrigger)
    }

    @Test
    fun repeatingAlarm_handlesYearChangeCorrectly() {
        val now = LocalDateTime.of(2024, 12, 31, 23, 30)
        val nextTrigger = NextAlarmCalculator.computeNextTriggerDateTime(
            now = now,
            hour = 8,
            minute = 15,
            enabledDays = setOf(DayOfWeekUi.WE)
        )

        assertEquals(LocalDateTime.of(2025, 1, 1, 8, 15), nextTrigger)
    }

    @Test
    fun computeNextTriggerMillis_returnsExpectedValue() {
        // The millis API wraps the same date-time calculation and converts it
        // through the device timezone used by AlarmManager.
        val now = LocalDateTime.of(2024, 1, 1, 8, 0)
        val expectedDateTime = LocalDateTime.of(2024, 1, 1, 9, 0)

        val millis = NextAlarmCalculator.computeNextTriggerMillis(
            now = now,
            hour = 9,
            minute = 0,
            enabledDays = emptySet()
        )

        val expectedMillis = expectedDateTime
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        assertNotNull(millis)
        assertEquals(expectedMillis, millis)
    }

    @Test
    fun buildScheduledInText_disabledAlarm() {
        val text = NextAlarmCalculator.buildScheduledInText(
            enabled = false,
            hour = 9,
            minute = 0,
            enabledDays = emptySet()
        )

        assertEquals("Alarm is disabled", text)
    }

    @Test
    fun buildScheduledInText_fewHours() {
        val now = LocalDateTime.of(2024, 1, 1, 8, 0)
        val text = NextAlarmCalculator.buildScheduledInText(
            enabled = true,
            hour = 10,
            minute = 30,
            enabledDays = emptySet(),
            now = now
        )

        assertEquals("This alarm is scheduled in 2 hours and 30 minutes", text)
    }

    @Test
    fun buildScheduledInText_lessThanOneMinute() {
        // Rounding down would otherwise produce "0 minutes", which is not
        // useful copy for the home screen.
        val now = LocalDateTime.of(2024, 1, 1, 8, 59, 30)
        val text = NextAlarmCalculator.buildScheduledInText(
            enabled = true,
            hour = 9,
            minute = 0,
            enabledDays = emptySet(),
            now = now
        )

        assertEquals("This alarm is scheduled in less than a minute", text)
    }

    @Test
    fun buildScheduledInText_singularHour() {
        val now = LocalDateTime.of(2024, 1, 1, 8, 0)
        val text = NextAlarmCalculator.buildScheduledInText(
            enabled = true,
            hour = 9,
            minute = 0,
            enabledDays = emptySet(),
            now = now
        )

        assertEquals("This alarm is scheduled in 1 hour", text)
    }

    @Test
    fun buildScheduledInText_singularMinute() {
        val now = LocalDateTime.of(2024, 1, 1, 8, 59)
        val text = NextAlarmCalculator.buildScheduledInText(
            enabled = true,
            hour = 9,
            minute = 0,
            enabledDays = emptySet(),
            now = now
        )

        assertEquals("This alarm is scheduled in 1 minute", text)
    }

    @Test
    fun buildScheduledInText_mixedUnits() {
        val now = LocalDateTime.of(2024, 1, 1, 8, 59)
        val text = NextAlarmCalculator.buildScheduledInText(
            enabled = true,
            hour = 10,
            minute = 0,
            enabledDays = emptySet(),
            now = now
        )

        assertEquals("This alarm is scheduled in 1 hour and 1 minute", text)
    }
}
