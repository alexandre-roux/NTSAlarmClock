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

    private companion object {
        const val TAG = "RingingActivity"
    }

    private val viewModel: RingScreenViewModel by viewModels()

    private var isFallbackAudioActive by mutableStateOf(false)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var relaunchScheduled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "onCreate: intent=$intent")

        configureAlarmWindow()
        updateFallbackState(intent)

        setContent {
            NTSAlarmClockTheme {
                Surface {
                    RingScreen(
                        isFallbackAudioActive = isFallbackAudioActive,
                        onDismiss = ::dismissAlarm,
                        viewModel = viewModel
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d(TAG, "onNewIntent: intent=$intent")
        setIntent(intent)
        updateFallbackState(intent)
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

        val volumeAction = when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> PlaybackService.ACTION_VOLUME_UP
            KeyEvent.KEYCODE_VOLUME_DOWN -> PlaybackService.ACTION_VOLUME_DOWN
            else -> return super.onKeyDown(keyCode, event)
        }

        sendVolumeAction(volumeAction)
        return true
    }

    private fun configureAlarmWindow() {
        volumeControlStream = AudioManager.STREAM_MUSIC
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun updateFallbackState(intent: Intent?) {
        isFallbackAudioActive = intent?.getBooleanExtra(
            PlaybackService.EXTRA_FALLBACK_AUDIO_ACTIVE,
            false
        ) ?: false

        Log.d(TAG, "updateFallbackState: isFallbackAudioActive=$isFallbackAudioActive")
    }

    private fun dismissAlarm() {
        Log.d(TAG, "dismissAlarm")
        finish()
    }

    /** Schedules at most one attempt to return the ringing screen to the foreground. */
    private fun scheduleBringToFront() {
        if (relaunchScheduled) return

        relaunchScheduled = true

        mainHandler.post {
            relaunchScheduled = false
            bringToFrontIfPossible()
        }
    }

    private fun bringToFrontIfPossible() {
        if (isFinishing || isDestroyed) return

        Log.d(TAG, "bringToFrontIfPossible: restarting RingingActivity")
        startActivity(createRelaunchIntent())
    }

    private fun createRelaunchIntent(): Intent {
        return Intent(this, RingingActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(
                PlaybackService.EXTRA_FALLBACK_AUDIO_ACTIVE,
                isFallbackAudioActive
            )
        }
    }

    private fun sendVolumeAction(action: String) {
        Log.d(TAG, "sendVolumeAction: action=$action")
        val intent = Intent(this, PlaybackService::class.java).apply {
            this.action = action
        }
        startService(intent)
    }
}
