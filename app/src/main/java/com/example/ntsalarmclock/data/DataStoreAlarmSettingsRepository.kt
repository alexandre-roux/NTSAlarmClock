package com.example.ntsalarmclock.data

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DataStoreAlarmSettingsRepository(
    private val dataStore: DataStore<Preferences>
) : AlarmSettingsRepository {
    val TAG = "DataStoreAlarmSettingsRepository"

    private companion object {
        val KEY_ENABLED = booleanPreferencesKey("alarm_enabled")
        val KEY_HOUR = intPreferencesKey("alarm_hour")
        val KEY_MINUTE = intPreferencesKey("alarm_minute")

        const val DEFAULT_HOUR = 7
        const val DEFAULT_MINUTE = 0
        const val DEFAULT_ENABLED = true
    }

    override val settings: Flow<AlarmSettings> =
        dataStore.data.map { prefs ->
            AlarmSettings(
                enabled = prefs[KEY_ENABLED] ?: DEFAULT_ENABLED,
                hour = prefs[KEY_HOUR] ?: DEFAULT_HOUR,
                minute = prefs[KEY_MINUTE] ?: DEFAULT_MINUTE
            )
        }

    override suspend fun setEnabled(enabled: Boolean) {
        Log.d(TAG, "setEnabled: $enabled")
        dataStore.edit { prefs ->
            prefs[KEY_ENABLED] = enabled
        }
    }

    override suspend fun setTime(hour: Int, minute: Int) {
        dataStore.edit { prefs ->
            prefs[KEY_HOUR] = hour
            prefs[KEY_MINUTE] = minute
        }
    }
}
