package com.alexroux.ntsalarmclock

import android.app.Application
import com.alexroux.ntsalarmclock.alarm.AlarmNotification

/**
 * Custom Application class used to perform app-wide initialization.
 *
 * Responsible for creating the notification channel used by
 * alarm notifications. This ensures the channel exists before any alarm
 * attempts to post a notification or start a foreground service.
 */
class NTSAlarmClockApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Ensure the alarm notification channel exists before any alarm fires
        AlarmNotification.createNotificationChannel(this)
    }
}