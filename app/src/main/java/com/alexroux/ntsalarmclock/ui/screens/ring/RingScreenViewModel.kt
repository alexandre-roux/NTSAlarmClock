package com.alexroux.ntsalarmclock.ui.screens.ring

import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alexroux.ntsalarmclock.alarm.AlarmNotification.NOTIFICATION_ID
import com.alexroux.ntsalarmclock.data.AlarmSettingsRepository
import com.alexroux.ntsalarmclock.data.nts.NtsRepository
import com.alexroux.ntsalarmclock.logging.NtsLogger
import com.alexroux.ntsalarmclock.playback.PlaybackService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

private const val CURRENT_SHOW_REFRESH_INTERVAL_MS = 60_000L

@HiltViewModel
class RingScreenViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: AlarmSettingsRepository,
    private val ntsRepository: NtsRepository
) : ViewModel() {

    private val _currentShow = MutableStateFlow<String?>(null)
    val currentShow: StateFlow<String?> = _currentShow.asStateFlow()

    private val _currentVolume = MutableStateFlow(70)
    val currentVolume: StateFlow<Int> = _currentVolume.asStateFlow()

    init {
        refreshCurrentShowPeriodically()
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

    /** Updates the UI and player immediately while the user moves the slider. */
    fun onVolumeChange(volume: Int) {
        val safeVolume = volume.coerceIn(0, 100)
        _currentVolume.value = safeVolume

        val intent = Intent(context, PlaybackService::class.java).apply {
            action = PlaybackService.ACTION_SET_VOLUME
            putExtra(PlaybackService.EXTRA_VOLUME, safeVolume)
        }
        context.startService(intent)
    }

    /** Persists the final slider value when the user stops dragging. */
    fun onVolumeChangeFinished(volume: Int) {
        val safeVolume = volume.coerceIn(0, 100)
        _currentVolume.value = safeVolume

        viewModelScope.launch {
            repository.setVolume(safeVolume)
        }
    }

    /** Keeps the slider synchronized with persisted and hardware-button changes. */
    private fun observeVolume() {
        viewModelScope.launch {
            repository.settings.collect { settings ->
                _currentVolume.value = settings.volume
            }
        }
    }

    private fun refreshCurrentShowPeriodically() {
        viewModelScope.launch {
            while (true) {
                ntsRepository.getCurrentShow()
                    .onSuccess { showTitle -> _currentShow.value = showTitle }
                    .onFailure { error ->
                        NtsLogger.w(TAG, "Unable to refresh the current NTS show: ${error.message}")
                    }
                delay(CURRENT_SHOW_REFRESH_INTERVAL_MS.milliseconds)
            }
        }
    }

    private companion object {
        const val TAG = "RingScreenViewModel"
    }
}
