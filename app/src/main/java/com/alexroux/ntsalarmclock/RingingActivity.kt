package com.alexroux.ntsalarmclock

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import com.alexroux.ntsalarmclock.playback.AlarmPlaybackState
import com.alexroux.ntsalarmclock.playback.PlaybackService
import com.alexroux.ntsalarmclock.ui.screens.ring.RingScreen
import com.alexroux.ntsalarmclock.ui.theme.NTSAlarmClockTheme

/**
 * Activity displayed when the alarm fires.
 *
 * This activity is responsible for:
 * - waking the device screen if necessary
 * - showing the alarm UI on top of the lock screen
 * - starting the alarm playback service
 *
 * The UI itself is implemented in [RingScreen].
 */
class RingingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Allow the activity to appear on top of the lock screen
        setShowWhenLocked(true)

        // Turn the screen on when the alarm triggers
        setTurnScreenOn(true)

        // Keep the screen awake while the alarm is ringing
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Start the foreground service responsible for playing the alarm sound
        startAlarmService()

        setContent {
            val isFallbackAudioActive by AlarmPlaybackState.isFallbackAudioActive.collectAsState()

            NTSAlarmClockTheme {
                Surface {
                    RingScreen(
                        isFallbackAudioActive = isFallbackAudioActive,
                        onDismiss = {
                            // Stop playback and close the ringing screen
                            stopAlarmService()
                            finish()
                        }
                    )
                }
            }
        }
    }

    /**
     * Starts the foreground service that plays the alarm stream.
     *
     * The service runs in the foreground to ensure playback continues
     * even if the app process is under memory pressure.
     */
    private fun startAlarmService() {
        val intent = Intent(this, PlaybackService::class.java).apply {
            action = PlaybackService.ACTION_START_ALARM
        }
        ContextCompat.startForegroundService(this, intent)
    }

    /**
     * Sends a command to the playback service to stop the alarm sound.
     */
    private fun stopAlarmService() {
        val intent = Intent(this, PlaybackService::class.java).apply {
            action = PlaybackService.ACTION_STOP_ALARM
        }
        startService(intent)
    }
}