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
        const val TAG = "DataStoreAlarmSettingsRepository"

        // These names are part of the persisted format and must remain stable.
        val KEY_ENABLED = booleanPreferencesKey("alarm_enabled")
        val KEY_HOUR = intPreferencesKey("alarm_hour")
        val KEY_MINUTE = intPreferencesKey("alarm_minute")
        val KEY_VOLUME = intPreferencesKey("alarm_volume")
        val KEY_ENABLED_DAYS = stringSetPreferencesKey("alarm_enabled_days")
        val KEY_PROGRESSIVE_VOLUME = booleanPreferencesKey("alarm_progressive_volume")

        const val DEFAULT_ENABLED = false
        const val DEFAULT_HOUR = 7
        const val DEFAULT_MINUTE = 0
        const val DEFAULT_VOLUME = 70
        const val DEFAULT_PROGRESSIVE_VOLUME = false
    }

    override val settings: Flow<AlarmSettings> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    Log.e(TAG, "Unable to read alarm settings; using defaults", exception)
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                preferences.toAlarmSettings()
            }

    override suspend fun setEnabled(enabled: Boolean) {
        Log.d(TAG, "setEnabled: $enabled")
        dataStore.edit { preferences ->
            preferences[KEY_ENABLED] = enabled
        }
    }

    override suspend fun setTime(hour: Int, minute: Int) {
        Log.d(TAG, "setTime: $hour:$minute")
        dataStore.edit { preferences ->
            preferences[KEY_HOUR] = hour
            preferences[KEY_MINUTE] = minute
        }
    }

    override suspend fun setVolume(volume: Int) {
        Log.d(TAG, "setVolume: $volume")
        dataStore.edit { preferences ->
            preferences[KEY_VOLUME] = volume
        }
    }

    override suspend fun setEnabledDays(days: Set<DayOfWeek>) {
        Log.d(TAG, "setEnabledDays: $days")
        val dayNames = days.map { day -> day.name }.toSet()

        dataStore.edit { preferences ->
            preferences[KEY_ENABLED_DAYS] = dayNames
        }
    }

    override suspend fun setProgressiveVolume(enabled: Boolean) {
        Log.d(TAG, "setProgressiveVolume: $enabled")
        dataStore.edit { preferences ->
            preferences[KEY_PROGRESSIVE_VOLUME] = enabled
        }
    }

    private fun Preferences.toAlarmSettings(): AlarmSettings {
        return AlarmSettings(
            enabled = this[KEY_ENABLED] ?: DEFAULT_ENABLED,
            hour = this[KEY_HOUR] ?: DEFAULT_HOUR,
            minute = this[KEY_MINUTE] ?: DEFAULT_MINUTE,
            volume = this[KEY_VOLUME] ?: DEFAULT_VOLUME,
            enabledDays = parseEnabledDays(this[KEY_ENABLED_DAYS].orEmpty()),
            progressiveVolume = this[KEY_PROGRESSIVE_VOLUME] ?: DEFAULT_PROGRESSIVE_VOLUME
        )
    }

    /** Unknown values are ignored so one invalid entry does not hide the valid days. */
    private fun parseEnabledDays(dayNames: Set<String>): Set<DayOfWeek> {
        return dayNames.mapNotNull { dayName ->
            DayOfWeek.entries.firstOrNull { day -> day.name == dayName }
        }.toSet()
    }
}
