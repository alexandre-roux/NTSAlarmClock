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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.alexroux.ntsalarmclock.ui.components.NTSButton
import com.alexroux.ntsalarmclock.ui.theme.NTSAlarmClockTheme

/**
 * Activity responsible for requesting the notifications permission
 * and the overlay (SYSTEM_ALERT_WINDOW) permission.
 *
 * Step 1 : POST_NOTIFICATIONS (Android 13+)
 * Step 2 : SYSTEM_ALERT_WINDOW (overlay) — required on some to display
 *           RingingActivity automatically over the lock screen.
 *
 * Once both are granted (or skipped on older API levels), the user is
 * redirected to MainActivity.
 */
class PermissionActivity : ComponentActivity() {

    // Tracks whether the notification permission is granted (observable by Compose).
    private var notificationsGranted by mutableStateOf(false)

    // Tracks whether the overlay permission is granted (observable by Compose).
    private var overlayGranted by mutableStateOf(false)

    // Number of times the user denied the POST_NOTIFICATIONS permission request.
    private var deniedCount by mutableIntStateOf(0)

    // Whether we are currently waiting for the overlay settings screen to return.
    private var waitingForOverlay = false

    /**
     * Launcher used to request the POST_NOTIFICATIONS permission.
     */
    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                notificationsGranted = true
                // Immediately proceed if overlay is already granted.
                if (overlayGranted) goToMainAndFinish()
            } else {
                deniedCount += 1
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialise observable states from the current system state.
        notificationsGranted = checkNotificationsGranted()
        overlayGranted = checkOverlayGranted()

        // Nothing to ask — go straight to MainActivity.
        if (notificationsGranted && overlayGranted) {
            goToMainAndFinish()
            return
        }

        setContent {
            NTSAlarmClockTheme {
                Surface {
                    when {
                        // Step 1 : notification permission not yet granted.
                        !notificationsGranted -> {
                            PermissionScreen(
                                deniedCount = deniedCount,
                                onAllowClick = { requestNotificationsPermission() },
                                onOpenSettingsClick = { openAppSettings() }
                            )
                        }
                        // Step 2 : overlay permission not yet granted.
                        !overlayGranted -> {
                            OverlayPermissionScreen(
                                onAllowClick = { openOverlaySettings() }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        // Refresh both states every time we come back (e.g. from system settings).
        notificationsGranted = checkNotificationsGranted()
        overlayGranted = checkOverlayGranted()

        if (waitingForOverlay) {
            waitingForOverlay = false
            // Proceed even if the user refused overlay — don't block the app.
            goToMainAndFinish()
            return
        }

        if (notificationsGranted && overlayGranted) {
            goToMainAndFinish()
        }
    }

    /**
     * Screens
     */

    @androidx.compose.runtime.Composable
    fun PermissionScreen(
        deniedCount: Int,
        onAllowClick: () -> Unit,
        onOpenSettingsClick: () -> Unit
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.notifications_permission_required),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.this_app_needs_notification_permission_to_work),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(36.dp))

            if (deniedCount >= 2) {
                NTSButton(
                    text = stringResource(R.string.open_settings),
                    textStyle = MaterialTheme.typography.headlineMedium,
                    onClick = onOpenSettingsClick
                )
            } else {
                NTSButton(
                    text = stringResource(R.string.allow_notifications),
                    textStyle = MaterialTheme.typography.headlineMedium,
                    onClick = onAllowClick
                )
            }
        }
    }

    @androidx.compose.runtime.Composable
    fun OverlayPermissionScreen(
        onAllowClick: () -> Unit
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.overlay_permission_required),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.overlay_permission_explanation),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(36.dp))

            NTSButton(
                text = stringResource(R.string.allow_overlay),
                textStyle = MaterialTheme.typography.headlineMedium,
                onClick = onAllowClick
            )
        }
    }

    /**
     *Permission helpers
     */
    private fun checkNotificationsGranted(): Boolean {
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
            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // No notification permission needed on older Android — skip to overlay step.
            notificationsGranted = true
            if (overlayGranted) goToMainAndFinish()
        }
    }

    private fun openAppSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            }
        )
    }

    private fun openOverlaySettings() {
        waitingForOverlay = true
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:$packageName".toUri()
            )
        )
    }

    private fun goToMainAndFinish() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}