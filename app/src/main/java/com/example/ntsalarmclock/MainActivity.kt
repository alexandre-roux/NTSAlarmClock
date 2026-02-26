package com.example.ntsalarmclock

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.Surface
import com.example.ntsalarmclock.ui.screens.home.HomeScreen
import com.example.ntsalarmclock.ui.screens.home.HomeScreenViewModel
import com.example.ntsalarmclock.ui.theme.NTSAlarmClockTheme

class MainActivity : ComponentActivity() {
    private val viewModel: HomeScreenViewModel by viewModels()

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                viewModel.onHardwareVolumeKey(+10)
                true
            }

            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                viewModel.onHardwareVolumeKey(-10)
                true
            }

            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NTSAlarmClockTheme {
                Surface {
                    HomeScreen(viewModel = viewModel)
                }
            }
        }
    }
}