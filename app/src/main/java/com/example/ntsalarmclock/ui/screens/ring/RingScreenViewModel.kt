package com.example.ntsalarmclock.ui.screens.ring

import android.app.Application
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.AndroidViewModel
import com.example.ntsalarmclock.alarm.AlarmNotification.NOTIFICATION_ID
import com.example.ntsalarmclock.playback.PlaybackService
import kotlinx.coroutines.flow.MutableStateFlow

data class RingUiState(
    val isRinging: Boolean = true
)

class RingScreenViewModel(app: Application) : AndroidViewModel(app) {

    private val _uiState = MutableStateFlow(RingUiState())

    fun stopAlarm() {
        val context = getApplication<Application>()

        // Stop the playback service
        val stopIntent = Intent(context, PlaybackService::class.java).apply {
            action = PlaybackService.ACTION_STOP_ALARM
        }
        context.startService(stopIntent)

        // Cancel the fullscreen alarm notification
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)

        _uiState.value = RingUiState(isRinging = false)
    }
}