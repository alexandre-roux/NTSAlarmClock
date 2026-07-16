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

/** Displays the alarm settings screen. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private companion object {
        const val VOLUME_STEP = 10
    }

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

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val volumeChange = when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> VOLUME_STEP
            KeyEvent.KEYCODE_VOLUME_DOWN -> -VOLUME_STEP
            else -> return super.onKeyDown(keyCode, event)
        }

        viewModel.onHardwareVolumeKey(volumeChange)
        return true
    }
}
