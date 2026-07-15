package com.alexroux.ntsalarmclock.data

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.DayOfWeek

/**
 * Persists [AlarmSettings] in a Preferences [DataStore].
 *
 * Missing preferences are replaced with application defaults. Read failures caused by I/O are
 * also exposed as default settings, while unexpected failures are propagated to the collector.
 */
class DataStoreAlarmSettingsRepository(
    private val dataStore: DataStore<Preferences>
) : AlarmSettingsRepository {

    private companion object {
        // Keys are kept stable because changing their names would orphan existing user settings.
        val KEY_ENABLED = booleanPreferencesKey("alarm_enabled")
        val KEY_HOUR = intPreferencesKey("alarm_hour")
        val KEY_MINUTE = intPreferencesKey("alarm_minute")
        val KEY_VOLUME = intPreferencesKey("alarm_volume")
        val KEY_ENABLED_DAYS = stringSetPreferencesKey("alarm_enabled_days")
        val KEY_PROGRESSIVE_VOLUME =
            booleanPreferencesKey("alarm_progressive_volume")

        // Defaults used for a fresh install or when an I/O read falls back to empty preferences.
        const val DEFAULT_ENABLED = false
        const val DEFAULT_HOUR = 7
        const val DEFAULT_MINUTE = 0
        const val DEFAULT_VOLUME = 70
        const val DEFAULT_PROGRESSIVE_VOLUME = false
    }

    private val TAG = "DataStoreAlarmSettingsRepository"

    /** Emits a complete domain model whenever the underlying preferences change. */
    override val settings: Flow<AlarmSettings> =
        dataStore.data
            .catch { exception ->
                // DataStore recommends recovering from storage I/O errors with empty preferences.
                // Programming errors and cancellation-related failures must still reach the caller.
                if (exception is IOException) {
                    Log.e(TAG, "Unable to read alarm settings; using defaults", exception)
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { prefs ->
                Log.d(TAG, "Raw prefs = $prefs")

                // Ignore unknown day values so one malformed entry does not invalidate all settings.
                val rawDays = prefs[KEY_ENABLED_DAYS].orEmpty()
                val enabledDays = rawDays.mapNotNull { value ->
                    runCatching { DayOfWeek.valueOf(value) }.getOrNull()
                }.toSet()

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

    /** Persists whether the alarm is enabled while preserving every other preference. */
    override suspend fun setEnabled(enabled: Boolean) {
        Log.d(TAG, "setEnabled: $enabled")
        dataStore.edit { prefs ->
            prefs[KEY_ENABLED] = enabled
        }
    }

    /** Persists the hour and minute together in one atomic DataStore transaction. */
    override suspend fun setTime(hour: Int, minute: Int) {
        Log.d(TAG, "setTime: $hour:$minute")
        dataStore.edit { prefs ->
            prefs[KEY_HOUR] = hour
            prefs[KEY_MINUTE] = minute
        }
    }

    /** Persists the selected alarm volume percentage. */
    override suspend fun setVolume(volume: Int) {
        Log.d(TAG, "setVolume: $volume")
        dataStore.edit { prefs ->
            prefs[KEY_VOLUME] = volume
        }
    }

    /** Persists the weekdays on which the alarm should repeat using their enum names. */
    override suspend fun setEnabledDays(days: Set<DayOfWeek>) {
        Log.d(TAG, "setEnabledDays: $days")
        val dayNames = days.map { day -> day.name }.toSet()

        dataStore.edit { preferences ->
            preferences[KEY_ENABLED_DAYS] = dayNames
        }
    }

    /** Persists whether playback should gradually increase to the selected volume. */
    override suspend fun setProgressiveVolume(progressiveVolumeEnabled: Boolean) {
        Log.d(TAG, "setProgressiveVolume: $progressiveVolumeEnabled")
        dataStore.edit { prefs ->
            prefs[KEY_PROGRESSIVE_VOLUME] = progressiveVolumeEnabled
        }
    }

}
