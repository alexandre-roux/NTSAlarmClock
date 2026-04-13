package com.alexroux.ntsalarmclock

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.Surface
import com.alexroux.ntsalarmclock.ui.screens.home.HomeScreen
import com.alexroux.ntsalarmclock.ui.screens.home.HomeScreenViewModel
import com.alexroux.ntsalarmclock.ui.theme.NTSAlarmClockTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Main entry point of the application.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: HomeScreenViewModel by viewModels()

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

    /**
     * Intercepts hardware volume button presses and forwards them to the ViewModel.
     */
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
}