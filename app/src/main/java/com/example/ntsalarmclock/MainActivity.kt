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

/**
 * Main entry point of the application.
 *
 * This activity hosts the Compose UI and connects the Home screen with
 * its ViewModel. It is also responsible for:
 *
 * - requesting the notification permission required on Android 13+
 * - forwarding hardware volume button presses to the ViewModel
 *
 * The UI itself is entirely implemented using Jetpack Compose.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: HomeScreenViewModel by viewModels()

    /**
     * Activity result launcher used to request the POST_NOTIFICATIONS permission
     * on Android 13 and above.
     */
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

    /**
     * Requests the notification permission on Android 13+ if it has not yet been granted.
     *
     * This permission is required for alarm notifications and full-screen intents.
     */
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

    /**
     * Intercepts hardware volume button presses and forwards them to the ViewModel.
     *
     * This allows the user to adjust the alarm volume directly using the device
     * volume buttons while the app is open.
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