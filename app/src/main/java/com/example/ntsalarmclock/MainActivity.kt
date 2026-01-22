package com.example.ntsalarmclock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import com.example.ntsalarmclock.ui.screens.home.HomeScreen
import com.example.ntsalarmclock.ui.theme.NTSAlarmClockTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NTSAlarmClockTheme {
                Surface {
                    HomeScreen()
                }
            }
        }
    }
}
