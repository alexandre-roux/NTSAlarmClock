package com.alexroux.ntsalarmclock

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
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

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            viewModel.onNotificationPermissionResult(granted)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        NtsLogger.d(TAG, "onCreate: initializing permission flow")
        refreshPermissions(source = "onCreate")

        setContent {
            val uiState by viewModel.uiState.collectAsState()

            LaunchedEffect(uiState) {
                NtsLogger.d(TAG, "uiState=$uiState")
                if (uiState == PermissionUiState.Completed) {
                    continueToMainActivity()
                }
            }

            NTSAlarmClockTheme {
                Surface {
                    PermissionRoute(
                        uiState = uiState,
                        onAllowNotificationsClick = ::requestNotificationPermission,
                        onOpenNotificationSettingsClick = ::openNotificationSettings,
                        onAllowOverlayClick = ::openOverlaySettings
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        refreshPermissions(source = "onResume")
    }

    private fun refreshPermissions(source: String) {
        val notificationsGranted = hasNotificationPermission()
        val overlayGranted = hasOverlayPermission()

        NtsLogger.d(
            TAG,
            "$source: notificationsGranted=$notificationsGranted, overlayGranted=$overlayGranted"
        )

        viewModel.onPermissionsChecked(
            notificationsGranted = notificationsGranted,
            overlayGranted = overlayGranted
        )
    }

    private fun hasNotificationPermission(): Boolean {
        // POST_NOTIFICATIONS only exists as a runtime permission on Android 13+.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true

        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasOverlayPermission(): Boolean = Settings.canDrawOverlays(this)

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            NtsLogger.d(TAG, "requestNotificationPermission: launching POST_NOTIFICATIONS request")
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            NtsLogger.d(
                TAG,
                "requestNotificationPermission: skipped on API ${Build.VERSION.SDK_INT}"
            )
            viewModel.onNotificationPermissionResult(granted = true)
        }
    }

    private fun openNotificationSettings() {
        NtsLogger.d(TAG, "openNotificationSettings: opening app settings")
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                "package:$packageName".toUri()
            )
        )
    }

    private fun openOverlaySettings() {
        NtsLogger.d(TAG, "openOverlaySettings: opening SYSTEM_ALERT_WINDOW settings")
        // Overlay settings have no result callback, so onResume checks the permission again.
        viewModel.onOverlaySettingsOpened()
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:$packageName".toUri()
            )
        )
    }

    private fun continueToMainActivity() {
        NtsLogger.d(TAG, "continueToMainActivity: permission flow completed")
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
