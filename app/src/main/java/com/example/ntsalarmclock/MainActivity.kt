package com.example.ntsalarmclock

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.Surface
import androidx.core.content.ContextCompat
import com.example.ntsalarmclock.ui.screens.home.HomeScreen
import com.example.ntsalarmclock.ui.screens.home.HomeScreenViewModel
import com.example.ntsalarmclock.ui.theme.NTSAlarmClockTheme

class MainActivity : ComponentActivity() {

    private val viewModel: HomeScreenViewModel by viewModels()

    private val requestNotificationPermission =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { /* No-op for now */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestNotificationPermissionIfNeeded()

        setContent {
            NTSAlarmClockTheme {
                Surface {
                    HomeScreen(viewModel = viewModel)
                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!granted) {
                requestNotificationPermission.launch(
                    Manifest.permission.POST_NOTIFICATIONS
                )
            }
        }
    }

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