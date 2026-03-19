package com.alexroux.ntsalarmclock.ui.screens.ring

import android.app.Application
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alexroux.ntsalarmclock.alarm.AlarmNotification.NOTIFICATION_ID
import com.alexroux.ntsalarmclock.data.nts.NtsApi
import com.alexroux.ntsalarmclock.data.nts.NtsRepository
import com.alexroux.ntsalarmclock.playback.PlaybackService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class RingScreenViewModel(app: Application) : AndroidViewModel(app) {

    // Holds the current NTS show title displayed in the UI.
    private val _currentShow = MutableStateFlow<String?>(null)
    val currentShow: StateFlow<String?> = _currentShow.asStateFlow()

    init {
        startFetchingCurrentShow()
    }

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

    // Periodically fetch the current show title from the NTS API.
    private fun startFetchingCurrentShow() {
        viewModelScope.launch {
            val repository = createRepository()

            while (true) {
                _currentShow.value = repository.getCurrentShow()
                delay(60_000)
            }
        }
    }

    // Create the repository used to fetch live show information.
    private fun createRepository(): NtsRepository {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://www.nts.live/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val api = retrofit.create(NtsApi::class.java)
        return NtsRepository(api)
    }
}