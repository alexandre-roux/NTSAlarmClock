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

private const val TAG = "HomeScreenViewModel"
private const val NTS_STREAM_URL = "https://stream-relay-geo.ntslive.net/stream"

/**
 * UI state exposed to the Home screen.
 */
sealed interface HomeScreenUiState {
    data object Loading : HomeScreenUiState

    data class Success(
        val enabled: Boolean,
        val hour: Int,
        val minute: Int,
        val volume: Int,
        val enabledDays: Set<DayOfWeekUi>,
        val progressiveVolume: Boolean,
        val streamUrl: String
    ) : HomeScreenUiState
}

/**
 * Minimal scheduling model containing only the fields that impact
 * alarm planning.
 *
 * Volume and progressive volume are intentionally excluded because
 * they do not affect when the next alarm should ring.
 */
data class AlarmScheduleConfig(
    val enabled: Boolean,
    val hour: Int,
    val minute: Int,
    val enabledDays: Set<DayOfWeekUi>
)

class HomeScreenViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository: AlarmSettingsRepository =
        DataStoreAlarmSettingsRepository(application.alarmSettingsDataStore)

    private val alarmScheduler = AlarmScheduler(application)

    /**
     * Full UI state used by the screen.
     *
     * The screen stays in Loading until the first DataStore value is received.
     * Once preferences are available, the UI switches to Success.
     */
    val uiState: StateFlow<HomeScreenUiState> =
        repository.settings
            .map { settings ->
                settings.toUiState()
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = HomeScreenUiState.Loading
            )

    /**
     * Dedicated flow for scheduling logic.
     *
     * Only values that matter for alarm planning are observed here.
     * distinctUntilChanged prevents unnecessary re-scheduling when
     * unrelated settings change, such as volume.
     */
    private val scheduleConfigFlow: Flow<AlarmScheduleConfig> =
        repository.settings
            .map { settings ->
                AlarmScheduleConfig(
                    enabled = settings.enabled,
                    hour = settings.hour,
                    minute = settings.minute,
                    enabledDays = settings.enabledDays
                )
            }
            .distinctUntilChanged()

    init {
        observeAlarmScheduling()
    }

    /**
     * Observe schedule-related changes and update the alarm planner only
     * when necessary.
     */
    private fun observeAlarmScheduling() {
        viewModelScope.launch {
            scheduleConfigFlow.collect { config ->
                Log.d(
                    TAG,
                    "schedule config changed: enabled=${config.enabled}, " +
                            "time=${config.hour}:${config.minute}, " +
                            "days=${config.enabledDays}"
                )

                if (config.enabled) {
                    alarmScheduler.scheduleNextAlarm(
                        hour = config.hour,
                        minute = config.minute,
                        enabledDays = config.enabledDays
                    )
                } else {
                    alarmScheduler.cancelAlarm()
                }
            }
        }
    }

    /**
     * Update the selected alarm time.
     */
    fun onTimeChange(hour: Int, minute: Int) {
        Log.d(TAG, "onTimeChange: $hour:$minute")

        viewModelScope.launch {
            repository.setTime(hour, minute)
        }
    }

    /**
     * Update the selected volume.
     *
     * This does not trigger re-scheduling because volume is not part
     * of AlarmScheduleConfig.
     */
    fun onVolumeChange(volume: Int) {
        viewModelScope.launch {
            repository.setVolume(volume)
        }
    }

    /**
     * Convert repository data into screen state.
     */
    private fun AlarmSettings.toUiState(): HomeScreenUiState.Success {
        return HomeScreenUiState.Success(
            enabled = enabled,
            hour = hour,
            minute = minute,
            volume = volume,
            enabledDays = enabledDays,
            progressiveVolume = progressiveVolume,
            streamUrl = NTS_STREAM_URL
        )
    }
}