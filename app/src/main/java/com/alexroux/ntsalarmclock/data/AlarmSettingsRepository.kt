package com.alexroux.ntsalarmclock.data

import kotlinx.coroutines.flow.Flow
import java.time.DayOfWeek

/** All user-configurable alarm settings. */
data class AlarmSettings(
    val enabled: Boolean,
    val hour: Int,
    val minute: Int,
    /** Alarm volume as a percentage from 0 to 100. */
    val volume: Int,
    val enabledDays: Set<DayOfWeek> = emptySet(),
    val progressiveVolume: Boolean
)

/** Read and write access to the persisted alarm settings. */
interface AlarmSettingsRepository {

    /** Emits the current settings and every subsequent update. */
    val settings: Flow<AlarmSettings>

    suspend fun setEnabled(enabled: Boolean)

    suspend fun setTime(hour: Int, minute: Int)

    suspend fun setVolume(volume: Int)

    suspend fun setEnabledDays(days: Set<DayOfWeek>)

    suspend fun setProgressiveVolume(enabled: Boolean)
}
