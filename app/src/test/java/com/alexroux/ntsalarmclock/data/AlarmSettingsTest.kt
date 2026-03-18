package com.alexroux.ntsalarmclock.data

import com.alexroux.ntsalarmclock.ui.components.DayOfWeekUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmSettingsTest {

    @Test
    fun `default values are correct`() {
        val settings = AlarmSettings(
            enabled = true,
            hour = 12,
            minute = 30,
            volume = 80,
            progressiveVolume = true
        )

        assertEquals(true, settings.enabled)
        assertEquals(12, settings.hour)
        assertEquals(30, settings.minute)
        assertEquals(80, settings.volume)
        assertEquals(emptySet<DayOfWeekUi>(), settings.enabledDays)
        assertEquals(true, settings.progressiveVolume)
    }

    @Test
    fun `copy creates a new instance with updated values`() {
        val original = AlarmSettings(
            enabled = false,
            hour = 8,
            minute = 0,
            volume = 50,
            enabledDays = setOf(DayOfWeekUi.MO),
            progressiveVolume = false
        )

        val updated = original.copy(enabled = true, volume = 90)

        assertTrue(updated.enabled)
        assertEquals(90, updated.volume)
        assertEquals(8, updated.hour) // unchanged
        assertEquals(setOf(DayOfWeekUi.MO), updated.enabledDays) // unchanged
    }
}