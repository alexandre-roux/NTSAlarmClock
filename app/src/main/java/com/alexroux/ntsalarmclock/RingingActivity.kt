package com.alexroux.ntsalarmclock

import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.alexroux.ntsalarmclock.playback.PlaybackService
import com.alexroux.ntsalarmclock.ui.screens.ring.RingScreen
import com.alexroux.ntsalarmclock.ui.screens.ring.RingScreenViewModel
import com.alexroux.ntsalarmclock.ui.theme.NTSAlarmClockTheme
import dagger.hilt.android.AndroidEntryPoint

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
@AndroidEntryPoint
class RingingActivity : ComponentActivity() {

    companion object {
        private const val TAG = "RingingActivity"
    }

    private val viewModel: RingScreenViewModel by viewModels()

    private var isFallbackAudioActive by mutableStateOf(false)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var relaunchScheduled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(
            TAG,
            "onCreate: intent=$intent, fallback=${intent?.getBooleanExtra(PlaybackService.EXTRA_FALLBACK_AUDIO_ACTIVE, false)}"
        )

        // Keep media as the controlled stream while this activity is visible.
        volumeControlStream = AudioManager.STREAM_MUSIC

        // Allow the activity to appear on top of the lock screen.
        setShowWhenLocked(true)

        // Turn the screen on when the alarm triggers.
        setTurnScreenOn(true)

        // Keep the screen awake while the alarm is ringing.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        updateFallbackStateFromIntent(intent)

        setContent {
            NTSAlarmClockTheme {
                Surface {
                    RingScreen(
                        isFallbackAudioActive = isFallbackAudioActive,
                        onDismiss = {
                            Log.d(TAG, "onDismiss")
                            finish()
                        },
                        viewModel = viewModel
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d(
            TAG,
            "onNewIntent: intent=$intent, fallback=${intent.getBooleanExtra(PlaybackService.EXTRA_FALLBACK_AUDIO_ACTIVE, false)}"
        )
        setIntent(intent)
        updateFallbackStateFromIntent(intent)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        Log.d(TAG, "onUserLeaveHint")
        scheduleBringToFront()
    }

    override fun onStop() {
        super.onStop()
        Log.d(
            TAG,
            "onStop: isFinishing=$isFinishing, isChangingConfigurations=$isChangingConfigurations"
        )

        if (!isFinishing && !isChangingConfigurations) {
            scheduleBringToFront()
        }
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        Log.d(TAG, "onKeyDown: keyCode=$keyCode")

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
     * Updates the UI state from the activity intent.
     */
    private fun updateFallbackStateFromIntent(intent: Intent?) {
        isFallbackAudioActive = intent?.getBooleanExtra(
            PlaybackService.EXTRA_FALLBACK_AUDIO_ACTIVE,
            false
        ) ?: false

        Log.d(TAG, "updateFallbackStateFromIntent: isFallbackAudioActive=$isFallbackAudioActive")
    }

    /**
     * Brings the ringing activity back to the foreground when the system
     * tries to send it behind the launcher during unlock / home-like flows.
     */
    private fun scheduleBringToFront() {
        if (relaunchScheduled) {
            return
        }

        relaunchScheduled = true

        mainHandler.post {
            relaunchScheduled = false

            if (isFinishing || isDestroyed) {
                return@post
            }

            Log.d(TAG, "scheduleBringToFront: restarting RingingActivity")

            val intent = Intent(this, RingingActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(
                    PlaybackService.EXTRA_FALLBACK_AUDIO_ACTIVE,
                    isFallbackAudioActive
                )
            }

            startActivity(intent)
        }
    }

    /**
     * Sends a volume adjustment command to the playback service.
     */
    private fun sendVolumeAction(action: String) {
        Log.d(TAG, "sendVolumeAction: action=$action")
        val intent = Intent(this, PlaybackService::class.java).apply {
            this.action = action
        }
        startService(intent)
    }
}