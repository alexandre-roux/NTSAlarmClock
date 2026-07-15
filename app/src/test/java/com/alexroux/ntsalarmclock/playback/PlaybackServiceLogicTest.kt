package com.alexroux.ntsalarmclock.playback

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure JVM coverage for calculations used by PlaybackService.
 *
 * Keeping this logic outside the Android Service lets these edge cases run fast
 * without Media3, Notification, or foreground-service infrastructure.
 */
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
    fun coerceVolumePercent_clampsToPersistedRange() {
        assertEquals(0, PlaybackServiceLogic.coerceVolumePercent(-1))
        assertEquals(42, PlaybackServiceLogic.coerceVolumePercent(42))
        assertEquals(100, PlaybackServiceLogic.coerceVolumePercent(101))
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
    fun initialPlayerVolume_clampsTargetVolume_whenProgressiveVolumeDisabled() {
        val initialVolume = PlaybackServiceLogic.initialPlayerVolume(
            targetVolumePercent = 150,
            progressiveVolumeEnabled = false
        )

        // Even if corrupt settings contain an out-of-range value, the player
        // must never receive a volume above 1f.
        assertEquals(1f, initialVolume)
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

    @Test
    fun nextProgressiveVolumeStep_keepsZeroTargetMuted() {
        val nextVolume = PlaybackServiceLogic.nextProgressiveVolumeStep(
            currentVolume = 0f,
            targetVolume = 0f,
            stepCount = 10
        )

        assertEquals(0f, nextVolume)
    }

    @Test
    fun applyManualVolumeDelta_changesVolumeWithinBounds() {
        assertEquals(
            0.6f,
            PlaybackServiceLogic.applyManualVolumeDelta(
                currentVolume = 0.5f,
                delta = 0.1f
            )
        )
    }

    @Test
    fun applyManualVolumeDelta_clampsVolumeToBounds() {
        assertEquals(
            1f,
            PlaybackServiceLogic.applyManualVolumeDelta(
                currentVolume = 0.95f,
                delta = 0.1f
            )
        )
        assertEquals(
            0f,
            PlaybackServiceLogic.applyManualVolumeDelta(
                currentVolume = 0.05f,
                delta = -0.1f
            )
        )
    }

    @Test
    fun applyManualVolumeDelta_clampsAlreadyOutOfRangeCurrentVolume() {
        assertEquals(
            1f,
            PlaybackServiceLogic.applyManualVolumeDelta(
                currentVolume = 1.2f,
                delta = 0.1f
            )
        )
        assertEquals(
            0f,
            PlaybackServiceLogic.applyManualVolumeDelta(
                currentVolume = -0.2f,
                delta = -0.1f
            )
        )
    }
}
