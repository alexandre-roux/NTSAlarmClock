package com.alexroux.ntsalarmclock.alarm

import com.alexroux.ntsalarmclock.ui.components.DayOfWeekUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.LocalDateTime

class NextAlarmCalculatorTest {

    @Test
    fun `one-shot alarm today`() {
        val now = LocalDateTime.of(2024, 1, 1, 8, 0) // Monday 8:00
        val nextTrigger = NextAlarmCalculator.computeNextTriggerDateTime(
            now = now,
            hour = 9,
            minute = 0,
            enabledDays = emptySet()
        )
        assertEquals(LocalDateTime.of(2024, 1, 1, 9, 0), nextTrigger)
    }

    @Test
    fun `one-shot alarm tomorrow`() {
        val now = LocalDateTime.of(2024, 1, 1, 10, 0) // Monday 10:00
        val nextTrigger = NextAlarmCalculator.computeNextTriggerDateTime(
            now = now,
            hour = 9,
            minute = 0,
            enabledDays = emptySet()
        )
        assertEquals(LocalDateTime.of(2024, 1, 2, 9, 0), nextTrigger)
    }

    @Test
    fun `repeating alarm later today`() {
        val now = LocalDateTime.of(2024, 1, 1, 8, 0) // Monday 8:00
        val nextTrigger = NextAlarmCalculator.computeNextTriggerDateTime(
            now = now,
            hour = 9,
            minute = 0,
            enabledDays = setOf(DayOfWeekUi.MO, DayOfWeekUi.WE)
        )
        assertEquals(LocalDateTime.of(2024, 1, 1, 9, 0), nextTrigger)
    }

    @Test
    fun `repeating alarm on a later day`() {
        val now = LocalDateTime.of(2024, 1, 1, 10, 0) // Monday 10:00
        val nextTrigger = NextAlarmCalculator.computeNextTriggerDateTime(
            now = now,
            hour = 9,
            minute = 0,
            enabledDays = setOf(DayOfWeekUi.MO, DayOfWeekUi.WE)
        )
        assertEquals(LocalDateTime.of(2024, 1, 3, 9, 0), nextTrigger)
    }

    @Test
    fun `repeating alarm next week`() {
        val now = LocalDateTime.of(2024, 1, 3, 10, 0) // Wednesday 10:00
        val nextTrigger = NextAlarmCalculator.computeNextTriggerDateTime(
            now = now,
            hour = 9,
            minute = 0,
            enabledDays = setOf(DayOfWeekUi.MO)
        )
        assertEquals(LocalDateTime.of(2024, 1, 8, 9, 0), nextTrigger)
    }

    @Test
    fun `buildScheduledInText for disabled alarm`() {
        val text = NextAlarmCalculator.buildScheduledInText(
            enabled = false,
            hour = 9,
            minute = 0,
            enabledDays = emptySet()
        )
        assertEquals("Alarm is disabled", text)
    }

    @Test
    fun `buildScheduledInText for alarm in few hours`() {
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
    fun `computeNextTriggerDateTime with exact time now schedules for tomorrow`() {
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
    fun `computeNextTriggerMillis returns non-null for valid input`() {
        val now = LocalDateTime.of(2024, 1, 1, 8, 0)
        val millis = NextAlarmCalculator.computeNextTriggerMillis(
            now = now,
            hour = 9,
            minute = 0,
            enabledDays = emptySet()
        )
        assertNotNull(millis)
    }

    @Test
    fun `buildScheduledInText for alarm in less than a minute`() {
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
    fun `buildScheduledInText for alarm in exactly one hour`() {
        val now = LocalDateTime.of(2024, 1, 1, 8, 0)
        val text = NextAlarmCalculator.buildScheduledInText(
            enabled = true,
            hour = 9,
            minute = 0,
            enabledDays = emptySet(),
            now = now
        )
        assertEquals("This alarm is scheduled in 1 hours", text)
    }
}