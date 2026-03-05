package com.example.ntsalarmclock

import android.app.Application
import com.example.ntsalarmclock.alarm.AlarmScheduler
import com.example.ntsalarmclock.data.AlarmSettingsRepository
import com.example.ntsalarmclock.data.DataStoreAlarmSettingsRepository
import com.example.ntsalarmclock.data.alarmSettingsDataStore

class NTSAlarmClockApplication : Application() {

    val repository: AlarmSettingsRepository by lazy {
        DataStoreAlarmSettingsRepository(alarmSettingsDataStore)
    }

    val alarmScheduler: AlarmScheduler by lazy {
        AlarmScheduler(applicationContext)
    }
}