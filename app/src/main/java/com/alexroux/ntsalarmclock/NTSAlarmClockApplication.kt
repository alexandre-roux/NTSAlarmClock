package com.alexroux.ntsalarmclock

import android.app.Application
import com.alexroux.ntsalarmclock.alarm.AlarmNotification
import dagger.hilt.android.HiltAndroidApp

/** Creates the alarm notification channel when the app process starts. */
@HiltAndroidApp
class NTSAlarmClockApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        AlarmNotification.createNotificationChannel(this)
    }
}
