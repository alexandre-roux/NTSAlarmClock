package com.alexroux.ntsalarmclock.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackServiceLogicTest {

    @Test
    fun toPlayerVolume_convertsPercentToFloat() {
        assertEquals(0.5f, PlaybackServiceLogic.toPlayerVolume(50))
    }

    @Test
    fun toPlayerVolume_clampsLowValue() {
        assertEquals(0f, PlaybackServiceLogic.toPlayerVolume(-10))
    }

    @Test
    fun toPlayerVolume_clampsHighValue() {
        assertEquals(1f, PlaybackServiceLogic.toPlayerVolume(150))
    }

    @Test
    fun initialPlayerVolume_returnsZero_whenProgressiveVolumeEnabled() {
        val initialVolume = PlaybackServiceLogic.initialPlayerVolume(
            targetVolumePercent = 80,
            progressiveVolumeEnabled = true
        )

        assertEquals(0f, initialVolume)
    }

    @Test
    fun initialPlayerVolume_returnsTargetVolume_whenProgressiveVolumeDisabled() {
        val initialVolume = PlaybackServiceLogic.initialPlayerVolume(
            targetVolumePercent = 80,
            progressiveVolumeEnabled = false
        )

        assertEquals(0.8f, initialVolume)
    }

    @Test
    fun nextProgressiveVolumeStep_increasesVolumeWithoutExceedingTarget() {
        val nextVolume = PlaybackServiceLogic.nextProgressiveVolumeStep(
            currentVolume = 0.2f,
            targetVolume = 0.5f,
            stepCount = 10
        )

        assertEquals(0.25f, nextVolume)
    }

    @Test
    fun nextProgressiveVolumeStep_stopsAtTargetVolume() {
        val nextVolume = PlaybackServiceLogic.nextProgressiveVolumeStep(
            currentVolume = 0.49f,
            targetVolume = 0.5f,
            stepCount = 10
        )

        assertEquals(0.5f, nextVolume)
    }

    @Test
    fun nextProgressiveVolumeStep_returnsTarget_whenStepCountIsZero() {
        val nextVolume = PlaybackServiceLogic.nextProgressiveVolumeStep(
            currentVolume = 0.1f,
            targetVolume = 0.8f,
            stepCount = 0
        )

        assertEquals(0.8f, nextVolume)
    }

    @Test
    fun nextProgressiveVolumeStep_returnsTarget_whenStepCountIsNegative() {
        val nextVolume = PlaybackServiceLogic.nextProgressiveVolumeStep(
            currentVolume = 0.1f,
            targetVolume = 0.8f,
            stepCount = -5
        )

        assertEquals(0.8f, nextVolume)
    }
}
