package com.example.ntsalarmclock.data

import com.example.ntsalarmclock.ui.components.DayOfWeekUi
import kotlinx.coroutines.flow.Flow

data class AlarmSettings(
    val enabled: Boolean,
    val hour: Int,
    val minute: Int,
    val volume: Int,
    val enabledDays: Set<DayOfWeekUi> = emptySet(),
    val progressiveVolume: Boolean
)

interface AlarmSettingsRepository {
    val settings: Flow<AlarmSettings>

    suspend fun setEnabled(enabled: Boolean)
    suspend fun setTime(hour: Int, minute: Int)
    suspend fun setVolume(volume: Int)
    suspend fun setEnabledDays(days: Set<DayOfWeekUi>)
    suspend fun setProgressiveVolume(progressiveVolumeEnabled: Boolean)
}
