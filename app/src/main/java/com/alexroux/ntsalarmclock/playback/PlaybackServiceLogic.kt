package com.alexroux.ntsalarmclock.playback

/**
 * Pure helper functions extracted from PlaybackService so they can be tested
 * without Android Service or ExoPlayer runtime dependencies.
 */
object PlaybackServiceLogic {

    /**
     * Convert persisted app volume from 0..100 into ExoPlayer volume 0f..1f.
     */
    fun toPlayerVolume(volumePercent: Int): Float {
        return volumePercent.coerceIn(0, 100) / 100f
    }

    /**
     * Return the initial volume applied when playback starts.
     *
     * Progressive volume starts at zero and increases later.
     */
    fun initialPlayerVolume(
        targetVolumePercent: Int,
        progressiveVolumeEnabled: Boolean
    ): Float {
        return if (progressiveVolumeEnabled) 0f else toPlayerVolume(targetVolumePercent)
    }

    /**
     * Compute one progressive step.
     */
    fun nextProgressiveVolumeStep(
        currentVolume: Float,
        targetVolume: Float,
        stepCount: Int
    ): Float {
        if (stepCount <= 0) return targetVolume

        val volumeStep = targetVolume / stepCount
        return (currentVolume + volumeStep).coerceAtMost(targetVolume)
    }
}