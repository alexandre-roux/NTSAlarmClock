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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeScreenUiState(
    val enabled: Boolean = true,
    val hour: Int = 7,
    val minute: Int = 0
)

class HomeScreenViewModel(app: Application) : AndroidViewModel(app) {
    val TAG = "HomeScreenViewModel"

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

    fun onEnabledChange(enabled: Boolean) {
        Log.d(TAG, "onEnabledChange: $enabled")
        viewModelScope.launch {
            repository.setEnabled(enabled)

            val current = uiState.value
            if (!enabled) {
                scheduler.cancel()
            } else {
                scheduler.scheduleNext(current.hour, current.minute)
            }
        }
    }

    fun onTimeChange(hour: Int, minute: Int) {
        Log.d(TAG, "onTimeChange: $hour:$minute")
        viewModelScope.launch {
            repository.setTime(hour, minute)

            if (uiState.value.enabled) {
                scheduler.scheduleNext(hour, minute)
            }
        }
    }

    private fun Flow<AlarmSettings>.toUiState() =
        mapToUiState(this)
}

private fun mapToUiState(
    flow: Flow<AlarmSettings>
): Flow<HomeScreenUiState> {
    return flow.map { settings ->
        HomeScreenUiState(
            enabled = settings.enabled,
            hour = settings.hour,
            minute = settings.minute
        )
    }
}
