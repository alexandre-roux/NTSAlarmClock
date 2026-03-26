package com.alexroux.ntsalarmclock.ui.screens.ring

import android.app.Application
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alexroux.ntsalarmclock.alarm.AlarmNotification.NOTIFICATION_ID
import com.alexroux.ntsalarmclock.data.AlarmSettingsRepository
import com.alexroux.ntsalarmclock.data.DataStoreAlarmSettingsRepository
import com.alexroux.ntsalarmclock.data.alarmSettingsDataStore
import com.alexroux.ntsalarmclock.data.nts.NtsNetwork
import com.alexroux.ntsalarmclock.data.nts.NtsRepository
import com.alexroux.ntsalarmclock.playback.PlaybackService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RingScreenViewModel @JvmOverloads constructor(
    app: Application,
    private val repository: AlarmSettingsRepository =
        DataStoreAlarmSettingsRepository(app.alarmSettingsDataStore)
) : AndroidViewModel(app) {

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
        val context = getApplication<Application>()

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

        val context = getApplication<Application>()

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
            val ntsRepository = createNtsRepository()

            while (true) {
                _currentShow.value = ntsRepository.getCurrentShow()
                delay(60_000)
            }
        }
    }

    /**
     * Create repository using singleton API instead of recreating Retrofit.
     */
    private fun createNtsRepository(): NtsRepository {
        return NtsRepository(NtsNetwork.api)
    }
}