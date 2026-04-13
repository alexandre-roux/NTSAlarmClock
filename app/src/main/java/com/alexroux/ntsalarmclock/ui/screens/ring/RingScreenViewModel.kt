package com.alexroux.ntsalarmclock.ui.screens.ring

import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alexroux.ntsalarmclock.alarm.AlarmNotification.NOTIFICATION_ID
import com.alexroux.ntsalarmclock.data.AlarmSettingsRepository
import com.alexroux.ntsalarmclock.data.nts.NtsRepository
import com.alexroux.ntsalarmclock.playback.PlaybackService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RingScreenViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: AlarmSettingsRepository,
    private val ntsRepository: NtsRepository
) : ViewModel() {

    private val _currentShow = MutableStateFlow<String?>(null)
    val currentShow: StateFlow<String?> = _currentShow.asStateFlow()

    private val _volumeLive = MutableStateFlow(70)
    val volumeLive: StateFlow<Int> = _volumeLive.asStateFlow()

    init {
        startFetchingCurrentShow()
        observeVolume()
    }

    /**
     * Stops the currently ringing alarm.
     */
    fun stopAlarm() {
        // Stop the playback service responsible for alarm audio
        val stopIntent = Intent(context, PlaybackService::class.java).apply {
            action = PlaybackService.ACTION_STOP_ALARM
        }
        context.startService(stopIntent)

        // Cancel the fullscreen alarm notification shown when the alarm fired
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    /**
     * Live change → update UI + player ONLY (no persistence)
     */
    fun onVolumeLiveChange(volume: Int) {
        val sanitized = volume.coerceIn(0, 100)
        _volumeLive.value = sanitized

        val intent = Intent(context, PlaybackService::class.java).apply {
            action = PlaybackService.ACTION_SET_VOLUME
            putExtra(PlaybackService.EXTRA_VOLUME, sanitized)
        }
        context.startService(intent)
    }

    /**
     * Final change → persist only
     */
    fun onVolumeChangeFinished(volume: Int) {
        val sanitized = volume.coerceIn(0, 100)
        _volumeLive.value = sanitized

        viewModelScope.launch {
            repository.setVolume(sanitized)
        }
    }

    /**
     * Keep UI in sync with DataStore (hardware buttons, etc.)
     */
    private fun observeVolume() {
        viewModelScope.launch {
            repository.settings.collect { settings ->
                _volumeLive.value = settings.volume
            }
        }
    }

    private fun startFetchingCurrentShow() {
        viewModelScope.launch {
            while (true) {
                _currentShow.value = ntsRepository.getCurrentShow()
                delay(60_000)
            }
        }
    }
}