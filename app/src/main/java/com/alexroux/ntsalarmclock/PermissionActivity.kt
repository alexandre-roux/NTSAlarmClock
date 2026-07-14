package com.alexroux.ntsalarmclock

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.alexroux.ntsalarmclock.logging.NtsLogger
import com.alexroux.ntsalarmclock.ui.screens.permission.PermissionRoute
import com.alexroux.ntsalarmclock.ui.screens.permission.PermissionUiState
import com.alexroux.ntsalarmclock.ui.screens.permission.PermissionViewModel
import com.alexroux.ntsalarmclock.ui.theme.NTSAlarmClockTheme

/**
 * Android shell for the permission flow.
 *
 * The ViewModel owns permission-flow state and decisions. This Activity keeps
 * the Android-only side effects: permission launcher, system settings intents,
 * current permission checks, and navigation to MainActivity.
 */
class PermissionActivity : ComponentActivity() {

    private companion object {
        const val TAG = "PermissionActivity"
    }

    private val viewModel: PermissionViewModel by viewModels()

    // Runtime permission requests must be launched by an Activity. The result is
    // immediately forwarded to the ViewModel so the flow decision stays there.
    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            viewModel.onNotificationPermissionResult(granted)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        NtsLogger.d(TAG, "onCreate: initializing permission flow")
        // Seed the ViewModel with the current system state before the first
        // composition, so the correct permission step is shown immediately.
        refreshPermissionState(source = "onCreate")

        setContent {
            val uiState by viewModel.uiState.collectAsState()

            LaunchedEffect(uiState) {
                NtsLogger.d(TAG, "uiState=$uiState")
                // Navigation is a one-off Android side effect, so the Activity
                // reacts to the terminal ViewModel state instead of the composable.
                if (uiState == PermissionUiState.Completed) {
                    goToMainAndFinish()
                }
            }

            NTSAlarmClockTheme {
                Surface {
                    PermissionRoute(
                        uiState = uiState,
                        onAllowNotificationsClick = { requestNotificationsPermission() },
                        onOpenNotificationSettingsClick = { openAppSettings() },
                        onAllowOverlayClick = { openOverlaySettings() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        // Refresh after returning from permission dialogs or system settings.
        refreshPermissionState(source = "onResume")
    }

    private fun refreshPermissionState(source: String) {
        val notificationsGranted = checkNotificationsGranted()
        val overlayGranted = checkOverlayGranted()

        NtsLogger.d(
            TAG,
            "$source: notificationsGranted=$notificationsGranted, overlayGranted=$overlayGranted"
        )

        viewModel.onPermissionsChecked(
            notificationsGranted = notificationsGranted,
            overlayGranted = overlayGranted
        )
    }

    private fun checkNotificationsGranted(): Boolean {
        // POST_NOTIFICATIONS only exists as a runtime permission on Android 13+.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun checkOverlayGranted(): Boolean {
        return Settings.canDrawOverlays(this)
    }

    private fun requestNotificationsPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            NtsLogger.d(TAG, "requestNotificationsPermission: launching POST_NOTIFICATIONS request")
            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            NtsLogger.d(
                TAG,
                "requestNotificationsPermission: skipped on API ${Build.VERSION.SDK_INT}"
            )
            viewModel.onNotificationPermissionResult(granted = true)
        }
    }

    private fun openAppSettings() {
        NtsLogger.d(TAG, "openAppSettings: opening notification permission recovery screen")
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            }
        )
    }

    private fun openOverlaySettings() {
        NtsLogger.d(TAG, "openOverlaySettings: opening SYSTEM_ALERT_WINDOW settings")
        // Android overlay settings do not return a permission result callback.
        // Mark the intent launch before leaving so onResume can complete the flow.
        viewModel.onOverlaySettingsOpened()
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:$packageName".toUri()
            )
        )
    }

    private fun goToMainAndFinish() {
        NtsLogger.d(TAG, "goToMainAndFinish: permission flow completed")
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
