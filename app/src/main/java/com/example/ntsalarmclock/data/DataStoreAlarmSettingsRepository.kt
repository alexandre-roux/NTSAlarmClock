package com.example.ntsalarmclock.data

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.example.ntsalarmclock.ui.components.DayOfWeekUi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * Repository implementation backed by Jetpack DataStore.
 *
 * This class is responsible for:
 * - Reading alarm settings from DataStore
 * - Exposing them as a Flow
 * - Updating individual settings in a safe and persistent way
 */
class DataStoreAlarmSettingsRepository(
    private val dataStore: DataStore<Preferences>
) : AlarmSettingsRepository {

    /**
     * Keys used to store values inside the Preferences DataStore.
     * These are the identifiers used to persist each setting.
     */
    private companion object {

        val KEY_ENABLED = booleanPreferencesKey("alarm_enabled")

        val KEY_HOUR = intPreferencesKey("alarm_hour")
        val KEY_MINUTE = intPreferencesKey("alarm_minute")

        val KEY_VOLUME = intPreferencesKey("alarm_volume")

        val KEY_ENABLED_DAYS = stringSetPreferencesKey("alarm_enabled_days")

        val KEY_PROGRESSIVE_VOLUME =
            booleanPreferencesKey("alarm_progressive_volume")

        /**
         * Default values used when no preference exists yet.
         */
        const val DEFAULT_ENABLED = true
        const val DEFAULT_HOUR = 7
        const val DEFAULT_MINUTE = 0
        const val DEFAULT_VOLUME = 70
        const val DEFAULT_PROGRESSIVE_VOLUME = false
    }

    private val TAG = "DataStoreAlarmSettingsRepository"

    /**
     * Flow exposing the current alarm settings.
     *
     * Each time DataStore changes, a new AlarmSettings instance
     * is emitted to observers.
     */
    override val settings: Flow<AlarmSettings> =
        dataStore.data

            /**
             * If an exception happens while reading preferences,
             * we recover by emitting empty preferences instead of crashing.
             */
            .catch { emit(emptyPreferences()) }

            /**
             * Transform raw Preferences into our domain model (AlarmSettings).
             */
            .map { prefs ->

                Log.d(TAG, "Raw prefs = $prefs")

                /**
                 * Read enabled days from DataStore.
                 * Stored as a Set<String> that we convert back to DayOfWeekUi.
                 */
                val rawDays = prefs[KEY_ENABLED_DAYS].orEmpty()

                val enabledDays = rawDays.mapNotNull { raw ->
                    runCatching { DayOfWeekUi.valueOf(raw) }.getOrNull()
                }.toSet()

                /**
                 * Build the AlarmSettings object with stored values
                 * or fallback to defaults if nothing is stored yet.
                 */
                AlarmSettings(
                    enabled = prefs[KEY_ENABLED] ?: DEFAULT_ENABLED,
                    hour = prefs[KEY_HOUR] ?: DEFAULT_HOUR,
                    minute = prefs[KEY_MINUTE] ?: DEFAULT_MINUTE,
                    volume = prefs[KEY_VOLUME] ?: DEFAULT_VOLUME,
                    enabledDays = enabledDays,
                    progressiveVolume = prefs[KEY_PROGRESSIVE_VOLUME]
                        ?: DEFAULT_PROGRESSIVE_VOLUME
                )
            }

    /**
     * Enable or disable the alarm.
     */
    override suspend fun setEnabled(enabled: Boolean) {

        Log.d(TAG, "setEnabled: $enabled")

        dataStore.edit { prefs ->
            prefs[KEY_ENABLED] = enabled
        }
    }

    /**
     * Update the alarm time.
     */
    override suspend fun setTime(hour: Int, minute: Int) {

        Log.d(TAG, "setTime: $hour:$minute")

        dataStore.edit { prefs ->
            prefs[KEY_HOUR] = hour
            prefs[KEY_MINUTE] = minute
        }
    }

    /**
     * Update the alarm volume.
     */
    override suspend fun setVolume(volume: Int) {

        Log.d(TAG, "setVolume: $volume")

        dataStore.edit { prefs ->
            prefs[KEY_VOLUME] = volume
        }
    }

    /**
     * Update the enabled days for the alarm.
     *
     * Days are stored as their enum names (String)
     * because Preferences DataStore cannot store enums directly.
     */
    override suspend fun setEnabledDays(days: Set<DayOfWeekUi>) {

        Log.d(TAG, "setEnabledDays: $days")

        val encoded = days.map { it.name }.toSet()

        dataStore.edit { prefs ->
            prefs[KEY_ENABLED_DAYS] = encoded
        }
    }

    /**
     * Enable or disable progressive volume.
     */
    override suspend fun setProgressiveVolume(progressiveVolumeEnabled: Boolean) {

        Log.d(TAG, "setProgressiveVolume: $progressiveVolumeEnabled")

        dataStore.edit { prefs ->
            prefs[KEY_PROGRESSIVE_VOLUME] = progressiveVolumeEnabled
        }
    }
}