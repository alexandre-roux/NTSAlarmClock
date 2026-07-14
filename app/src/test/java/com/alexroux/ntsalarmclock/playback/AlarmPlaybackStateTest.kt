package com.alexroux.ntsalarmclock.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the shared fallback-audio state holder used by UI and playback code.
 */
class AlarmPlaybackStateTest {

    @Test
    fun setFallbackAudioActive_updatesSharedState() {
        // Reset first because this object is process-global and can retain state
        // if another test toggled it earlier in the same JVM.
        AlarmPlaybackState.setFallbackAudioActive(false)
        assertFalse(AlarmPlaybackState.isFallbackAudioActive.value)

        AlarmPlaybackState.setFallbackAudioActive(true)
        assertTrue(AlarmPlaybackState.isFallbackAudioActive.value)

        AlarmPlaybackState.setFallbackAudioActive(false)
        assertFalse(AlarmPlaybackState.isFallbackAudioActive.value)
    }
}
