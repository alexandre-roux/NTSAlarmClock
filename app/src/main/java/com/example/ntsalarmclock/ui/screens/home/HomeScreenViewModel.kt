package com.example.ntsalarmclock.ui.screens.home

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ntsalarmclock.NTSAlarmClockApplication
import com.example.ntsalarmclock.data.AlarmSettings
import com.example.ntsalarmclock.data.AlarmSettingsRepository
import com.example.ntsalarmclock.ui.components.DayOfWeekUi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Stream URL used for the NTS radio playback.
 */
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
        val streamUrl: String,
        val enabledDays: Set<DayOfWeekUi>,
        val progressiveVolume: Boolean,
        val scheduledInText: String
    ) : HomeScreenUiState
}

/**
 * ViewModel responsible for managing the Home screen state.
 */
class HomeScreenViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        private const val TAG = "HomeScreenViewModel"
    }

    private val appContext = getApplication<NTSAlarmClockApplication>()
    private val scheduler = appContext.alarmScheduler
    private val repository: AlarmSettingsRepository = appContext.repository

    /**
     * Emits the current time immediately, then again every minute.
     */
    private val nowMillisFlow: Flow<Long> = flow {
        emit(System.currentTimeMillis())
        while (true) {
            delay(60_000)
            emit(System.currentTimeMillis())
        }
    }

    /**
     * UI state observed by the Compose screen.
     */
    val uiState: StateFlow<HomeScreenUiState> =
        combine(repository.settings, nowMillisFlow) { settings, nowMillis ->
            settings.toUiState(nowMillis)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeScreenUiState.Loading
        )

    init {
        viewModelScope.launch {
            repository.settings
                .distinctUntilChangedBySchedulingFields()
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

    /**
     * Converts repository settings into a UI state object.
     */
    private fun AlarmSettings.toUiState(nowMillis: Long): HomeScreenUiState.Success {
        return HomeScreenUiState.Success(
            enabled = enabled,
            hour = hour,
            minute = minute,
            volume = volume,
            streamUrl = NTS_STREAM_URL,
            enabledDays = enabledDays,
            progressiveVolume = progressiveVolume,
            scheduledInText = buildScheduledInText(this, nowMillis)
        )
    }

    /**
     * Builds a user friendly string indicating how long before the next alarm triggers.
     */
    private fun buildScheduledInText(settings: AlarmSettings, nowMillis: Long): String {
        if (!settings.enabled) return ""

        val triggerAtMillis = scheduler.computeNextTriggerMillis(
            hour = settings.hour,
            minute = settings.minute,
            enabledDays = settings.enabledDays
        )

        val deltaMillis = triggerAtMillis - nowMillis
        if (deltaMillis <= 0L) return ""

        val totalMinutes = deltaMillis / 60_000
        if (totalMinutes <= 0L) return "This alarm is scheduled in less than 1 minute"

        val hours = (totalMinutes / 60).toInt()
        val minutes = (totalMinutes % 60).toInt()

        return when {
            hours > 0 && minutes > 0 ->
                "This alarm is scheduled in ${hours} ${
                    pluralize(
                        hours,
                        "hour"
                    )
                } and ${minutes} ${pluralize(minutes, "minute")}"

            hours > 0 ->
                "This alarm is scheduled in ${hours} ${pluralize(hours, "hour")}"

            else ->
                "This alarm is scheduled in ${minutes} ${pluralize(minutes, "minute")}"
        }
    }

    /**
     * Returns the pluralized form of a word.
     */
    private fun pluralize(value: Int, word: String): String {
        return if (value == 1) word else "${word}s"
    }

    /**
     * Toggles the alarm enabled state.
     */
    fun onEnabledChange() {
        updateCurrentState { current ->
            val nextEnabled = !current.enabled
            Log.d(TAG, "onEnabledChange: $nextEnabled")
            repository.setEnabled(nextEnabled)
        }
    }

    /**
     * Updates the alarm time.
     */
    fun onTimeChange(hour: Int, minute: Int) {
        Log.d(TAG, "onTimeChange: $hour:$minute")
        viewModelScope.launch {
            repository.setTime(hour, minute)
        }
    }

    /**
     * Updates the alarm volume.
     */
    fun onVolumeChange(volume: Int) {
        val clamped = volume.coerceIn(0, 100)
        Log.d(TAG, "onVolumeChange: $clamped")
        viewModelScope.launch {
            repository.setVolume(clamped)
        }
    }

    /**
     * Updates the volume using hardware key delta.
     */
    fun onHardwareVolumeKey(delta: Int) {
        updateCurrentState { current ->
            val next = (current.volume + delta).coerceIn(0, 100)
            Log.d(TAG, "onHardwareVolumeKey: $delta")
            repository.setVolume(next)
        }
    }

    /**
     * Toggles a selected day.
     */
    fun onToggleDay(day: DayOfWeekUi) {
        updateCurrentState { current ->
            val updated =
                if (day in current.enabledDays) current.enabledDays - day
                else current.enabledDays + day

            Log.d(TAG, "onToggleDay: $updated")
            repository.setEnabledDays(updated)
        }
    }

    /**
     * Enables or disables progressive volume.
     */
    fun onProgressiveVolumeEnabledChange(progressiveVolumeEnabled: Boolean) {
        viewModelScope.launch {
            repository.setProgressiveVolume(progressiveVolumeEnabled)
        }
    }

    /**
     * Helper that safely reads the current Success state before updating repository values.
     */
    private fun updateCurrentState(block: suspend (HomeScreenUiState.Success) -> Unit) {
        val current = uiState.value as? HomeScreenUiState.Success ?: return
        viewModelScope.launch {
            block(current)
        }
    }
}

/**
 * Filters AlarmSettings emissions so scheduling only reacts when
 * scheduling related fields actually change.
 */
private fun Flow<AlarmSettings>.distinctUntilChangedBySchedulingFields(): Flow<AlarmSettings> {
    return distinctUntilChanged { old, new ->
        old.enabled == new.enabled &&
                old.hour == new.hour &&
                old.minute == new.minute &&
                old.enabledDays == new.enabledDays
    }
}