package com.example.ntsalarmclock.data

import kotlinx.coroutines.flow.Flow

data class AlarmSettings(
    val enabled: Boolean,
    val hour: Int,
    val minute: Int,
    val volume: Int
)

interface AlarmSettingsRepository {
    val settings: Flow<AlarmSettings>

    suspend fun setEnabled(enabled: Boolean)
    suspend fun setTime(hour: Int, minute: Int)
    suspend fun setVolume(volume: Int)
}
