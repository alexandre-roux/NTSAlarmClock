package com.alexroux.ntsalarmclock.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * Name of the DataStore file stored on disk.
 */
private const val DATASTORE_NAME = "alarm_settings"

/**
 * Extension property on Context that provides the DataStore instance.
 *
 * This uses the official preferencesDataStore delegate which ensures:
 * - Only one DataStore instance per process
 * - Thread safety
 * - Lazy initialization
 */
val Context.alarmSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = DATASTORE_NAME
)