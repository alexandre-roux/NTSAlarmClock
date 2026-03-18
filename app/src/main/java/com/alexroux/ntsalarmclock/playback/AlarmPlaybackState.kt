package com.alexroux.ntsalarmclock.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared in-process playback state exposed to the ringing UI.
 */
object AlarmPlaybackState {

    private val _isFallbackAudioActive = MutableStateFlow(false)
    val isFallbackAudioActive: StateFlow<Boolean> = _isFallbackAudioActive.asStateFlow()

    fun setFallbackAudioActive(isActive: Boolean) {
        _isFallbackAudioActive.value = isActive
    }
}