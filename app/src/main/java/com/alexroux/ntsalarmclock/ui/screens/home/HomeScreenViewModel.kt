package com.alexroux.ntsalarmclock.ui.screens.home

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alexroux.ntsalarmclock.alarm.AlarmScheduler
import com.alexroux.ntsalarmclock.alarm.NextAlarmCalculator
import com.alexroux.ntsalarmclock.data.AlarmSettings
import com.alexroux.ntsalarmclock.data.AlarmSettingsRepository
import com.alexroux.ntsalarmclock.data.DataStoreAlarmSettingsRepository
import com.alexroux.ntsalarmclock.data.alarmSettingsDataStore
import com.alexroux.ntsalarmclock.playback.NTS_STREAM_URL
import com.alexroux.ntsalarmclock.ui.components.DayOfWeekUi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "HomeScreenViewModel"

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
        val streamUrl: String,
        val scheduledInText: String
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
    application: Application,
    private val repository: AlarmSettingsRepository =
        DataStoreAlarmSettingsRepository(application.alarmSettingsDataStore),
    private val alarmScheduler: AlarmScheduler = AlarmScheduler(application)
) : AndroidViewModel(application) {

    /**
     * Full UI state used by the screen.
     *
     * The screen stays in Loading until the first DataStore value is received.
     * Once preferences are available, the UI switches to Success.
     */
    val uiState: StateFlow<HomeScreenUiState> =
        repository.settings
            .map { settings -> settings.toUiState() }
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
        updateSettings { repository.setTime(hour, minute) }
    }

    /**
     * Toggle the alarm enabled state.
     */
    fun onEnabledChange() {
        withSuccessState { state ->
            updateSettings {
                repository.setEnabled(!state.enabled)
            }
        }
    }

    /**
     * Toggle a day in the selected recurring days set.
     */
    fun onToggleDay(day: DayOfWeekUi) {
        withSuccessState { state ->
            val updatedDays = state.enabledDays.toMutableSet().apply {
                if (contains(day)) {
                    remove(day)
                } else {
                    add(day)
                }
            }

            updateSettings {
                repository.setEnabledDays(updatedDays)
            }
        }
    }

    /**
     * Update the selected volume.
     *
     * This does not trigger re-scheduling because volume is not part
     * of AlarmScheduleConfig.
     */
    fun onVolumeChange(volume: Int) {
        updateSettings {
            repository.setVolume(volume.coerceIn(0, 100))
        }
    }

    /**
     * Apply a relative volume change when the user presses a hardware
     * volume key inside the activity.
     */
    fun onHardwareVolumeKey(delta: Int) {
        withSuccessState { state ->
            val updatedVolume = (state.volume + delta).coerceIn(0, 100)

            updateSettings {
                repository.setVolume(updatedVolume)
            }
        }
    }

    /**
     * Update the progressive volume preference.
     *
     * This does not trigger re-scheduling because it does not change
     * when the next alarm should ring.
     */
    fun onProgressiveVolumeEnabledChange(enabled: Boolean) {
        updateSettings {
            repository.setProgressiveVolume(enabled)
        }
    }

    /**
     * Run a block only when the current UI state is Success.
     *
     * This avoids repeating the same cast-and-return pattern in every action.
     */
    private inline fun withSuccessState(block: (HomeScreenUiState.Success) -> Unit) {
        val state = uiState.value as? HomeScreenUiState.Success ?: return
        block(state)
    }

    /**
     * Launch a repository update from the ViewModel scope.
     */
    private fun updateSettings(block: suspend () -> Unit) {
        viewModelScope.launch {
            block()
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
            streamUrl = NTS_STREAM_URL,
            scheduledInText = NextAlarmCalculator.buildScheduledInText(
                enabled = enabled,
                hour = hour,
                minute = minute,
                enabledDays = enabledDays
            )
        )
    }
}