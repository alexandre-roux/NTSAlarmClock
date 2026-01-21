package com.example.ntsalarmclock.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface

class RingingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show activity on lock screen and turn screen on
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        setContent {
            MaterialTheme {
                Surface {
                    RingScreen()
                }
            }
        }
    }
}
