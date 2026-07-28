package com.alexroux.ntsalarmclock.playback

/** Volume calculations used by [PlaybackService] and [PlaybackVolumeController]. */
object PlaybackServiceLogic {

    /** Converts the saved 0..100 volume to the player's 0f..1f range. */
    fun toPlayerVolume(volumePercent: Int): Float =
        coerceVolumePercent(volumePercent) / 100f

    fun coerceVolumePercent(volumePercent: Int): Int = volumePercent.coerceIn(0, 100)

    /**
     * Returns the initial volume applied when playback starts.
     *
     * Progressive volume starts at zero and increases later.
     */
    fun initialPlayerVolume(
        targetVolumePercent: Int,
        progressiveVolumeEnabled: Boolean
    ): Float = if (progressiveVolumeEnabled) 0f else toPlayerVolume(targetVolumePercent)

    /** Calculates one progressive volume step without exceeding the target. */
    fun nextProgressiveVolumeStep(
        currentVolume: Float,
        targetVolume: Float,
        stepCount: Int
    ): Float {
        if (stepCount <= 0) return targetVolume

        val volumeStep = targetVolume / stepCount
        return (currentVolume + volumeStep).coerceAtMost(targetVolume)
    }

    /** Applies a hardware-button change without leaving the player's valid range. */
    fun applyManualVolumeDelta(
        currentVolume: Float,
        delta: Float
    ): Float = (currentVolume + delta).coerceIn(0f, 1f)
}
