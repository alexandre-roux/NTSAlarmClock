package com.alexroux.ntsalarmclock.ui.screens.home

import android.content.res.Resources
import com.alexroux.ntsalarmclock.R
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime

class ResourceAlarmScheduleTextFormatterTest {
    private val resources = mockk<Resources>()
    private lateinit var formatter: ResourceAlarmScheduleTextFormatter

    @Before
    fun setup() {
        stubEnglishResources()
        formatter = ResourceAlarmScheduleTextFormatter(resources)
    }

    @Test
    fun disabledAlarm_usesDisabledResource() {
        assertEquals(
            "Alarm is disabled",
            formatter.format(false, 9, 0, emptySet())
        )
    }

    @Test
    fun duration_usesPluralResourcesAndLocalizedSeparators() {
        val text = formatter.format(
            enabled = true,
            hour = 9,
            minute = 30,
            enabledDays = setOf(DayOfWeek.WEDNESDAY),
            now = LocalDateTime.of(2024, 1, 1, 8, 0)
        )
        assertEquals("This alarm is scheduled in 2 days, 1 hour and 30 minutes", text)
    }

    @Test
    fun duration_usesSingularQuantities() {
        val text = formatter.format(
            enabled = true,
            hour = 9,
            minute = 1,
            enabledDays = emptySet(),
            now = LocalDateTime.of(2024, 1, 1, 8, 0)
        )
        assertEquals("This alarm is scheduled in 1 hour and 1 minute", text)
    }

    @Test
    fun duration_withOnlyHours_omitsEmptyUnits() {
        val text = formatter.format(
            enabled = true,
            hour = 10,
            minute = 0,
            enabledDays = emptySet(),
            now = LocalDateTime.of(2024, 1, 1, 8, 0)
        )
        assertEquals("This alarm is scheduled in 2 hours", text)
    }

    @Test
    fun duration_acrossMidnight_usesActualMinuteDifference() {
        val text = formatter.format(
            enabled = true,
            hour = 0,
            minute = 10,
            enabledDays = emptySet(),
            now = LocalDateTime.of(2024, 1, 1, 23, 50)
        )
        assertEquals("This alarm is scheduled in 20 minutes", text)
    }

    @Test
    fun duration_exactlyOneDay_usesSingularDay() {
        val text = formatter.format(
            enabled = true,
            hour = 8,
            minute = 0,
            enabledDays = emptySet(),
            now = LocalDateTime.of(2024, 1, 1, 8, 0)
        )
        assertEquals("This alarm is scheduled in 1 day", text)
    }

    @Test
    fun duration_lessThanMinute_usesDedicatedResource() {
        val text = formatter.format(
            enabled = true,
            hour = 9,
            minute = 0,
            enabledDays = emptySet(),
            now = LocalDateTime.of(2024, 1, 1, 8, 59, 30)
        )
        assertEquals("This alarm is scheduled in less than a minute", text)
    }

    private fun stubEnglishResources() {
        every { resources.getString(R.string.alarm_disabled) } returns "Alarm is disabled"
        every { resources.getString(R.string.no_alarm_scheduled) } returns "No alarm scheduled"
        every {
            resources.getString(R.string.alarm_scheduled_in, any())
        } answers {
            val duration = secondArg<Array<Any>>().single()
            "This alarm is scheduled in $duration"
        }
        every {
            resources.getString(R.string.alarm_scheduled_in_less_than_minute)
        } returns "This alarm is scheduled in less than a minute"
        every { resources.getString(R.string.duration_separator) } returns ","
        every { resources.getString(R.string.duration_final_separator) } returns "and"
        every {
            resources.getQuantityString(R.plurals.duration_days, match { it == 1 }, any())
        } answers { "${secondArg<Int>()} day" }
        every {
            resources.getQuantityString(R.plurals.duration_days, match { it != 1 }, any())
        } answers { "${secondArg<Int>()} days" }
        every {
            resources.getQuantityString(R.plurals.duration_hours, match { it == 1 }, any())
        } answers { "${secondArg<Int>()} hour" }
        every {
            resources.getQuantityString(R.plurals.duration_hours, match { it != 1 }, any())
        } answers { "${secondArg<Int>()} hours" }
        every {
            resources.getQuantityString(R.plurals.duration_minutes, match { it == 1 }, any())
        } answers { "${secondArg<Int>()} minute" }
        every {
            resources.getQuantityString(R.plurals.duration_minutes, match { it != 1 }, any())
        } answers { "${secondArg<Int>()} minutes" }
    }
}
