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

/**
 * Tests the human-readable "scheduled in" text without loading a real Android resource table.
 *
 * [Resources] is mocked with simple English strings in [stubEnglishResources]. This keeps the test
 * focused on the formatter's choices: which time units are included, whether singular or plural is
 * requested, and how units are joined. It is not intended to test Android's resource framework.
 */
class ResourceAlarmScheduleTextFormatterTest {
    private val resources = mockk<Resources>()
    private lateinit var formatter: ResourceAlarmScheduleTextFormatter

    @Before
    fun setup() {
        stubEnglishResources()
        formatter = ResourceAlarmScheduleTextFormatter(resources)
    }

    /**
     * Given `enabled = false`, the time and repeat-day arguments should be irrelevant. The formatter
     * must return the dedicated disabled resource immediately, giving the UI a clear state instead of
     * a misleading countdown for an alarm that will not fire.
     */
    @Test
    fun disabledAlarm_usesDisabledResource() {
        assertEquals(
            "Alarm is disabled",
            formatter.format(false, 9, 0, emptySet())
        )
    }

    /**
     * Monday 08:00 to the enabled Wednesday 09:30 occurrence is two days, one hour, and 30 minutes.
     * The expected sentence proves the formatter includes every non-zero unit, chooses singular for
     * one hour and plural for the other quantities, and uses the localized comma and final "and".
     */
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

    /**
     * A one-shot alarm from 08:00 to 09:01 is exactly one hour and one minute away. The assertion
     * specifically protects Android plural-resource selection: quantities of one must render
     * "hour" and "minute", not their plural forms.
     */
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

    /**
     * A delay from 08:00 to 10:00 contains hours but no whole days or remaining minutes. Returning
     * only "2 hours" proves zero-valued units are omitted rather than producing noisy text such as
     * "0 days, 2 hours and 0 minutes".
     */
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

    /**
     * At 23:50, a one-shot alarm for 00:10 belongs to the next day but is only 20 minutes away.
     * This boundary test proves date rollover is used for calculation while the displayed duration
     * remains the actual short difference, not "1 day and 20 minutes".
     */
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

    /**
     * When the current time exactly matches a one-shot alarm time, that occurrence is considered
     * passed and the next one is tomorrow. The result must therefore be exactly one day and must use
     * the singular day resource.
     */
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

    /**
     * At 08:59:30, an alarm for 09:00 is 30 seconds away. Because the UI displays whole minutes,
     * formatting this as zero minutes would be confusing. The dedicated resource communicates that
     * the alarm is still in the future but less than one full minute away.
     */
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
        // Each stub mirrors one Android string/plural lookup used by the formatter. Returning simple
        // English makes the exact final sentence easy to assert while still verifying resource IDs.
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
