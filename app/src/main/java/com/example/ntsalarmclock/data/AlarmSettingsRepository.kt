package com.example.ntsalarmclock.data

import com.example.ntsalarmclock.ui.components.DayOfWeekUi
import kotlinx.coroutines.flow.Flow

/**
 * Data model representing all alarm settings.
 */
data class AlarmSettings(

    /** Whether the alarm is enabled */
    val enabled: Boolean,

    /** Hour of the alarm */
    val hour: Int,

    /** Minute of the alarm */
    val minute: Int,

    /** Alarm volume (0-100) */
    val volume: Int,

    /** Days of the week when the alarm should trigger */
    val enabledDays: Set<DayOfWeekUi> = emptySet(),

    /** Whether progressive volume is enabled */
    val progressiveVolume: Boolean
)

/**
 * Repository contract for accessing and modifying alarm settings.
 *
 * This abstraction allows:
 * - Swapping implementations (DataStore, database, etc.)
 * - Easier testing
 * - Separation of concerns between UI and storage
 */
interface AlarmSettingsRepository {

    /**
     * Flow emitting the current alarm settings.
     * Any change in storage automatically updates observers.
     */
    val settings: Flow<AlarmSettings>

    /**
     * Enable or disable the alarm.
     */
    suspend fun setEnabled(enabled: Boolean)

    /**
     * Update the alarm time.
     */
    suspend fun setTime(hour: Int, minute: Int)

    /**
     * Update the alarm volume.
     */
    suspend fun setVolume(volume: Int)

    /**
     * Update the enabled days for the alarm.
     */
    suspend fun setEnabledDays(days: Set<DayOfWeekUi>)

    /**
     * Enable or disable progressive volume.
     */
    suspend fun setProgressiveVolume(progressiveVolumeEnabled: Boolean)
}