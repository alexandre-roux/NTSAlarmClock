package com.example.ntsalarmclock

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.core.content.ContextCompat
import com.example.ntsalarmclock.playback.PlaybackService
import com.example.ntsalarmclock.ui.screens.ring.RingScreen

class RingingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        startAlarmService()

        setContent {
            MaterialTheme {
                Surface {
                    RingScreen(
                        onDismiss = {
                            stopAlarmService()
                            finish()
                        }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        // Safety net: ensure the service is stopped when leaving the screen.
        stopAlarmService()
        super.onDestroy()
    }

    private fun startAlarmService() {
        val intent = Intent(this, PlaybackService::class.java).apply {
            action = PlaybackService.ACTION_START_ALARM
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopAlarmService() {
        val intent = Intent(this, PlaybackService::class.java).apply {
            action = PlaybackService.ACTION_STOP_ALARM
        }
        startService(intent)
    }
}