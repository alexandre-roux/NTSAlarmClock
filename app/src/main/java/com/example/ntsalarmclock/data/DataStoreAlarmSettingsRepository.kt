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

class DataStoreAlarmSettingsRepository(
    private val dataStore: DataStore<Preferences>
) : AlarmSettingsRepository {

    private companion object {
        val KEY_ENABLED = booleanPreferencesKey("alarm_enabled")
        val KEY_HOUR = intPreferencesKey("alarm_hour")
        val KEY_MINUTE = intPreferencesKey("alarm_minute")
        val KEY_VOLUME = intPreferencesKey("alarm_volume")
        val KEY_ENABLED_DAYS = stringSetPreferencesKey("alarm_enabled_days")
        val KEY_PROGRESSIVE_VOLUME = booleanPreferencesKey("alarm_progressive_volume")

        const val DEFAULT_ENABLED = true
        const val DEFAULT_HOUR = 7
        const val DEFAULT_MINUTE = 0
        const val DEFAULT_VOLUME = 70
        const val DEFAULT_PROGRESSIVE_VOLUME = false
    }

    private val TAG = "DataStoreAlarmSettingsRepository"

    override val settings: Flow<AlarmSettings> =
        dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { prefs ->
                Log.d(TAG, "Raw prefs = $prefs")
                val rawDays = prefs[KEY_ENABLED_DAYS].orEmpty()

                val enabledDays = rawDays.mapNotNull { raw ->
                    runCatching { DayOfWeekUi.valueOf(raw) }.getOrNull()
                }.toSet()

                AlarmSettings(
                    enabled = prefs[KEY_ENABLED] ?: DEFAULT_ENABLED,
                    hour = prefs[KEY_HOUR] ?: DEFAULT_HOUR,
                    minute = prefs[KEY_MINUTE] ?: DEFAULT_MINUTE,
                    volume = prefs[KEY_VOLUME] ?: DEFAULT_VOLUME,
                    enabledDays = enabledDays,
                    progressiveVolume = prefs[KEY_PROGRESSIVE_VOLUME] ?: DEFAULT_PROGRESSIVE_VOLUME
                )
            }

    override suspend fun setEnabled(enabled: Boolean) {
        Log.d(TAG, "setEnabled: $enabled")
        dataStore.edit { prefs ->
            prefs[KEY_ENABLED] = enabled
        }
    }

    override suspend fun setTime(hour: Int, minute: Int) {
        Log.d(TAG, "setTime: $hour:$minute")
        dataStore.edit { prefs ->
            prefs[KEY_HOUR] = hour
            prefs[KEY_MINUTE] = minute
        }
    }

    override suspend fun setVolume(volume: Int) {
        Log.d(TAG, "setVolume: $volume")
        dataStore.edit { prefs ->
            prefs[KEY_VOLUME] = volume
        }
    }

    override suspend fun setEnabledDays(days: Set<DayOfWeekUi>) {
        Log.d(TAG, "setEnabledDays: $days")
        val encoded = days.map { it.name }.toSet()
        dataStore.edit { prefs ->
            prefs[KEY_ENABLED_DAYS] = encoded
        }
    }

    override suspend fun setProgressiveVolume(progressiveVolumeEnabled: Boolean) {
        Log.d(TAG, "setProgressiveVolume: $progressiveVolumeEnabled")
        dataStore.edit { prefs ->
            prefs[KEY_PROGRESSIVE_VOLUME] = progressiveVolumeEnabled
        }
    }
}