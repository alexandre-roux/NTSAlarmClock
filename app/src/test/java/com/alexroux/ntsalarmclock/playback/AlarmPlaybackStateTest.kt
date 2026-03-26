package com.alexroux.ntsalarmclock.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmPlaybackStateTest {

    @Test
    fun setFallbackAudioActive_updatesSharedState() {
        AlarmPlaybackState.setFallbackAudioActive(false)
        assertFalse(AlarmPlaybackState.isFallbackAudioActive.value)

        AlarmPlaybackState.setFallbackAudioActive(true)
        assertTrue(AlarmPlaybackState.isFallbackAudioActive.value)

        AlarmPlaybackState.setFallbackAudioActive(false)
        assertFalse(AlarmPlaybackState.isFallbackAudioActive.value)
    }
}
