package com.alexroux.ntsalarmclock.playback

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure JVM coverage for calculations used by PlaybackService.
 *
 * Keeping this logic outside the Android Service lets these edge cases run fast
 * without Media3, Notification, or foreground-service infrastructure.
 *
 * Playback code uses two volume representations: user settings are integer percentages from
 * 0 through 100, while Media3's player expects floating-point values from 0f through 1f. These
 * tests document every conversion, boundary, and progressive-volume edge case between them.
 */
class PlaybackServiceLogicTest {

    /**
     * Given a normal saved volume of 50 percent, converting it for Media3 should produce 0.5f.
     * This is the basic scale conversion: divide the integer percentage by 100 without changing
     * its relative loudness.
     */
    @Test
    fun toPlayerVolume_convertsPercentToFloat() {
        assertEquals(0.5f, PlaybackServiceLogic.toPlayerVolume(50))
    }

    /**
     * Given a corrupt or accidental percentage below zero, the conversion should return 0f.
     * Media3 does not accept negative volume, so this assertion proves the conversion also acts as
     * a safety boundary rather than blindly dividing -10 into -0.1f.
     */
    @Test
    fun toPlayerVolume_clampsLowValue() {
        assertEquals(0f, PlaybackServiceLogic.toPlayerVolume(-10))
    }

    /**
     * Given a percentage above the supported maximum, the conversion should return exactly 1f.
     * This prevents an invalid stored value such as 150 from reaching the player as 1.5f.
     * Checking the exact maximum also proves the value is capped rather than rejected or muted.
     */
    @Test
    fun toPlayerVolume_clampsHighValue() {
        assertEquals(1f, PlaybackServiceLogic.toPlayerVolume(150))
    }

    /**
     * This test covers all three branches of percentage normalization: a value below the range
     * becomes 0, an in-range value remains unchanged, and a value above the range becomes 100.
     * Testing all branches distinguishes clamping from a function that always returns one boundary.
     */
    @Test
    fun coerceVolumePercent_clampsToPersistedRange() {
        assertEquals(0, PlaybackServiceLogic.coerceVolumePercent(-1))
        assertEquals(42, PlaybackServiceLogic.coerceVolumePercent(42))
        assertEquals(100, PlaybackServiceLogic.coerceVolumePercent(101))
    }

    /**
     * Given an 80 percent target with progressive volume enabled, playback must start at 0f.
     * The scheduled ramp can then increase volume gradually; starting at 0.8f would make the
     * progressive preference ineffective.
     */
    @Test
    fun initialPlayerVolume_returnsZero_whenProgressiveVolumeEnabled() {
        val initialVolume = PlaybackServiceLogic.initialPlayerVolume(
            targetVolumePercent = 80,
            progressiveVolumeEnabled = true
        )

        assertEquals(0f, initialVolume)
    }

    /**
     * Given the same 80 percent target with progressive volume disabled, the initial player volume
     * should be 0.8f immediately. This proves the progressive flag, rather than the target itself,
     * selects between muted startup and direct startup.
     */
    @Test
    fun initialPlayerVolume_returnsTargetVolume_whenProgressiveVolumeDisabled() {
        val initialVolume = PlaybackServiceLogic.initialPlayerVolume(
            targetVolumePercent = 80,
            progressiveVolumeEnabled = false
        )

        assertEquals(0.8f, initialVolume)
    }

    /**
     * Given an invalid 150 percent target and no progressive ramp, initial volume must still be
     * clamped to Media3's maximum of 1f. The test protects the direct-start path, which could
     * otherwise bypass the normal percentage conversion safeguards.
     */
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

    /**
     * Given current volume 0.2f, target 0.5f, and ten total ramp steps, one step adds 0.05f
     * (`target / stepCount`). The expected 0.25f proves the function advances by a stable fraction
     * of the target rather than a fraction of the remaining distance.
     */
    @Test
    fun nextProgressiveVolumeStep_increasesVolumeWithoutExceedingTarget() {
        val nextVolume = PlaybackServiceLogic.nextProgressiveVolumeStep(
            currentVolume = 0.2f,
            targetVolume = 0.5f,
            stepCount = 10
        )

        assertEquals(0.25f, nextVolume)
    }

    /**
     * Given a current volume already close to the target, the next calculated increment would pass
     * 0.5f. Returning exactly 0.5f proves the ramp stops at the user's requested volume and cannot
     * become louder because of its fixed step size.
     */
    @Test
    fun nextProgressiveVolumeStep_stopsAtTargetVolume() {
        val nextVolume = PlaybackServiceLogic.nextProgressiveVolumeStep(
            currentVolume = 0.49f,
            targetVolume = 0.5f,
            stepCount = 10
        )

        assertEquals(0.5f, nextVolume)
    }

    /**
     * A zero step count would require division by zero and cannot describe a useful ramp. The safe
     * fallback is the requested target volume, so the alarm remains audible and the calculation
     * produces a valid number.
     */
    @Test
    fun nextProgressiveVolumeStep_returnsTarget_whenStepCountIsZero() {
        val nextVolume = PlaybackServiceLogic.nextProgressiveVolumeStep(
            currentVolume = 0.1f,
            targetVolume = 0.8f,
            stepCount = 0
        )

        assertEquals(0.8f, nextVolume)
    }

    /**
     * A negative step count is invalid configuration just like zero. Returning the target directly
     * proves the defensive branch handles every non-positive count instead of attempting a negative
     * ramp that would move away from the target.
     */
    @Test
    fun nextProgressiveVolumeStep_returnsTarget_whenStepCountIsNegative() {
        val nextVolume = PlaybackServiceLogic.nextProgressiveVolumeStep(
            currentVolume = 0.1f,
            targetVolume = 0.8f,
            stepCount = -5
        )

        assertEquals(0.8f, nextVolume)
    }

    /**
     * Given a target of 0f, progressive calculation must keep the player muted. This guards against
     * special-case arithmetic accidentally introducing audible volume when the requested target is
     * silence.
     */
    @Test
    fun nextProgressiveVolumeStep_keepsZeroTargetMuted() {
        val nextVolume = PlaybackServiceLogic.nextProgressiveVolumeStep(
            currentVolume = 0f,
            targetVolume = 0f,
            stepCount = 10
        )

        assertEquals(0f, nextVolume)
    }

    /**
     * Given a current player volume of 0.5f and a manual increase of 0.1f, the result should be 0.6f.
     * This establishes normal addition behavior before the following tests focus on clamping.
     * Because the result remains in range, no boundary correction should alter the requested delta.
     */
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

    /**
     * This test exercises both boundaries for manual volume controls. Increasing 0.95f by 0.1f must
     * stop at 1f, while decreasing 0.05f by 0.1f must stop at 0f. Together they prove a hardware or
     * UI adjustment can never push Media3 outside its accepted range.
     */
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

    /**
     * The current value itself may already be invalid because of stale state or an external caller.
     * Starting above 1f and below 0f, then moving farther outward, must still normalize to 1f and 0f.
     * This proves clamping applies to the final result regardless of how it became invalid.
     */
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
