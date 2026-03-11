package com.alexroux.ntsalarmclock.ui.screens.ring

import android.app.Application
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.AndroidViewModel
import com.alexroux.ntsalarmclock.alarm.AlarmNotification.NOTIFICATION_ID
import com.alexroux.ntsalarmclock.playback.PlaybackService

class RingScreenViewModel(app: Application) : AndroidViewModel(app) {

    /**
     * Stops the currently ringing alarm.
     *
     * Responsibilities:
     * - stop the PlaybackService responsible for audio playback
     * - remove the fullscreen alarm notification
     * - update the UI state so the screen can close
     */
    fun stopAlarm() {
        val context = getApplication<Application>()

        // Stop the playback service responsible for alarm audio
        val stopIntent = Intent(context, PlaybackService::class.java).apply {
            action = PlaybackService.ACTION_STOP_ALARM
        }
        context.startService(stopIntent)

        // Cancel the fullscreen alarm notification shown when the alarm fired
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }
}