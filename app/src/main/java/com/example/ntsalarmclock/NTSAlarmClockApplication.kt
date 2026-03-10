package com.example.ntsalarmclock

import android.app.Application
import com.example.ntsalarmclock.alarm.AlarmNotification

class NTSAlarmClockApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Ensure the alarm notification channel exists before any alarm fires
        AlarmNotification.createNotificationChannel(this)
    }
}