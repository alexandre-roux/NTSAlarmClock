package com.alexroux.ntsalarmclock.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

private const val ALARM_SETTINGS_FILE_NAME = "alarm_settings"

/**
 * The app-wide DataStore used to persist alarm settings.
 * The delegate creates one lazy, thread-safe instance per process.
 */
val Context.alarmSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = ALARM_SETTINGS_FILE_NAME
)
