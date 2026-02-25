package com.example.ntsalarmclock.ui.screens.home

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ntsalarmclock.alarm.AlarmScheduler
import com.example.ntsalarmclock.data.AlarmSettings
import com.example.ntsalarmclock.data.AlarmSettingsRepository
import com.example.ntsalarmclock.data.DataStoreAlarmSettingsRepository
import com.example.ntsalarmclock.data.alarmSettingsDataStore
import com.example.ntsalarmclock.ui.components.DayOfWeekUi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val NTS_STREAM_URL = "https://stream-relay-geo.ntslive.net/stream"

data class HomeScreenUiState(
    val enabled: Boolean = false,
    val hour: Int = 7,
    val minute: Int = 0,
    val volume: Int = 25,
    val streamUrl: String = NTS_STREAM_URL,
    val enabledDays: Set<DayOfWeekUi> = emptySet(),
    val progressiveVolume: Boolean = false
)

class HomeScreenViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        private const val TAG = "HomeScreenViewModel"
    }

    private val scheduler = AlarmScheduler(app.applicationContext)

    private val repository: AlarmSettingsRepository =
        DataStoreAlarmSettingsRepository(app.applicationContext.alarmSettingsDataStore)

    val uiState: StateFlow<HomeScreenUiState> =
        repository.settings
            .toUiState()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = HomeScreenUiState()
            )

    init {
        // Single source of truth: whenever alarm-related settings change, reschedule accordingly.
        viewModelScope.launch {
            repository.settings
                .distinctUntilChanged()
                .collect { settings ->
                    Log.d(
                        TAG,
                        "settings changed: enabled=${settings.enabled}, time=${settings.hour}:${settings.minute}, days=${settings.enabledDays}"
                    )

                    if (settings.enabled) {
                        scheduler.scheduleNextFromSettings(settings)
                    } else {
                        scheduler.cancel()
                    }
                }
        }
    }

    fun onEnabledChange() {
        val nextEnabled = !uiState.value.enabled
        Log.d(TAG, "onEnabledChange: $nextEnabled")
        viewModelScope.launch {
            repository.setEnabled(nextEnabled)
        }
    }

    fun onTimeChange(hour: Int, minute: Int) {
        Log.d(TAG, "onTimeChange: $hour:$minute")
        viewModelScope.launch {
            repository.setTime(hour, minute)
        }
    }

    fun onVolumeChange(volume: Int) {
        Log.d(TAG, "onVolumeChange: $volume")
        val clamped = volume.coerceIn(0, 100)
        viewModelScope.launch {
            repository.setVolume(clamped)
        }
    }

    fun onHardwareVolumeKey(delta: Int) {
        Log.d(TAG, "onHardwareVolumeKey: $delta")
        val current = uiState.value.volume
        val next = (current + delta).coerceIn(0, 100)
        onVolumeChange(next)
    }

    fun onToggleDay(day: DayOfWeekUi) {
        viewModelScope.launch {
            val current = uiState.value.enabledDays
            val updated = if (current.contains(day)) current - day else current + day
            Log.d(TAG, "onToggleDay: $updated")
            repository.setEnabledDays(updated)
        }
    }

    fun onProgressiveVolumeEnabledChange(progressiveVolumeEnabled: Boolean) {
        viewModelScope.launch {
            repository.setProgressiveVolume(progressiveVolumeEnabled)
        }
    }

    private fun Flow<AlarmSettings>.toUiState(): Flow<HomeScreenUiState> {
        return this.map { settings ->
            HomeScreenUiState(
                enabled = settings.enabled,
                hour = settings.hour,
                minute = settings.minute,
                volume = settings.volume,
                streamUrl = NTS_STREAM_URL,
                enabledDays = settings.enabledDays,
                progressiveVolume = settings.progressiveVolume
            )
        }
    }
}