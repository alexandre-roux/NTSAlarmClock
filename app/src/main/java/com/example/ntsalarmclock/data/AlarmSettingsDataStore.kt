package com.example.ntsalarmclock.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

private const val DATASTORE_NAME = "alarm_settings"

val Context.alarmSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = DATASTORE_NAME
)
