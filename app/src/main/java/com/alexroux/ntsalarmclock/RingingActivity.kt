package com.alexroux.ntsalarmclock

import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
 * - controlling an alarm playback service that is already running
 *
 * The UI itself is implemented in [RingScreen].
 */
class RingingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep media as the controlled stream while this activity is visible.
        volumeControlStream = AudioManager.STREAM_MUSIC

        // Allow the activity to appear on top of the lock screen
        setShowWhenLocked(true)

        // Turn the screen on when the alarm triggers
        setTurnScreenOn(true)

        // Keep the screen awake while the alarm is ringing
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

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

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                sendVolumeAction(PlaybackService.ACTION_VOLUME_UP)
                true
            }

            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                sendVolumeAction(PlaybackService.ACTION_VOLUME_DOWN)
                true
            }

            else -> super.onKeyDown(keyCode, event)
        }
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

    /**
     * Sends a volume adjustment command to the playback service.
     */
    private fun sendVolumeAction(action: String) {
        val intent = Intent(this, PlaybackService::class.java).apply {
            this.action = action
        }
        startService(intent)
    }
}