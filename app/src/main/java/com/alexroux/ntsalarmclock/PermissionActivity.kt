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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.alexroux.ntsalarmclock.ui.components.NTSButton
import com.alexroux.ntsalarmclock.ui.theme.NTSAlarmClockTheme

/**
 * Activity responsible for requesting the notifications permission
 * and the overlay (SYSTEM_ALERT_WINDOW) permission.
 *
 * On Android 13+ the POST_NOTIFICATIONS permission is required for
 * alarm notifications and full screen intents to work properly.
 *
 * The overlay permission is required on some devices to allow
 * RingingActivity to appear automatically over the lock screen when
 * the alarm fires, without any user interaction.
 *
 * This activity is shown only if at least one permission has not yet
 * been granted. Once all permissions are granted, the user is
 * redirected to MainActivity.
 */
class PermissionActivity : ComponentActivity() {

    // Number of times the user denied the POST_NOTIFICATIONS permission request
    private var deniedCount by mutableIntStateOf(0)

    // Whether we are currently waiting for the overlay permission screen to return
    private var waitingForOverlayPermission by mutableStateOf(false)

    /**
     * Launcher used to request the POST_NOTIFICATIONS permission.
     * If granted we move on to check the overlay permission.
     */
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                checkOverlayPermissionOrProceed()
            } else {
                deniedCount += 1
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // If all permissions are already granted, skip this screen entirely
        if (isNotificationsPermissionGranted() && isOverlayPermissionGranted()) {
            goToMainAndFinish()
            return
        }

        setContent {
            NTSAlarmClockTheme {
                Surface {
                    if (!isNotificationsPermissionGranted()) {
                        // Step 1 : ask for notification permission
                        PermissionScreen(
                            deniedCount = deniedCount,
                            onAllowClick = { requestNotificationsPermission() },
                            onOpenSettingsClick = { openAppSettings() }
                        )
                    } else {
                        // Step 2 : ask for overlay permission
                        OverlayPermissionScreen(
                            onAllowClick = { openOverlaySettings() }
                        )
                    }
                }
            }
        }
    }

    /**
     * Compose UI explaining why the notification permission is required.
     *
     * If the user denied the permission twice, the system dialog will no
     * longer appear, so we redirect the user to the app settings instead.
     */
    @Composable
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

            Text(
                text = stringResource(R.string.this_app_needs_notification_permission_to_work),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(36.dp))

            if (deniedCount >= 2) {
                // After multiple denials the permission dialog may stop appearing,
                // so we direct the user to the app settings screen.
                NTSButton(
                    text = stringResource(R.string.open_settings),
                    textStyle = MaterialTheme.typography.headlineMedium,
                    onClick = onOpenSettingsClick
                )
            } else {
                // Show the standard permission request button
                NTSButton(
                    text = stringResource(R.string.allow_notifications),
                    textStyle = MaterialTheme.typography.headlineMedium,
                    onClick = onAllowClick
                )
            }
        }
    }

    /**
     * Compose UI explaining why the overlay permission is required.
     *
     * This permission allows RingingActivity to appear automatically
     * over the lock screen on some devices when the alarm fires.
     */
    @Composable
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
     * Preview used by Android Studio to visualize the notification permission screen.
     */
    @Preview(showBackground = true)
    @Composable
    private fun PermissionScreenPreview() {
        NTSAlarmClockTheme {
            Surface(color = MaterialTheme.colorScheme.background) {
                PermissionScreen(
                    deniedCount = 2,
                    onAllowClick = {},
                    onOpenSettingsClick = {}
                )
            }
        }
    }

    /**
     * When returning from the system settings screen we re-check permissions
     * and continue to MainActivity once everything is granted.
     */
    override fun onResume() {
        super.onResume()

        if (waitingForOverlayPermission) {
            waitingForOverlayPermission = false
            // User came back from overlay settings — proceed regardless of the result
            // so the app is not blocked if the user refuses.
            goToMainAndFinish()
            return
        }

        if (isNotificationsPermissionGranted() && isOverlayPermissionGranted()) {
            goToMainAndFinish()
        }
    }

    /**
     * Requests the notification permission on Android 13+.
     * On older versions this permission does not exist.
     */
    private fun requestNotificationsPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            checkOverlayPermissionOrProceed()
        }
    }

    /**
     * If the overlay permission is already granted, goes directly to
     * MainActivity. Otherwise stays on this activity to show the
     * overlay permission screen (Compose recomposition handles this).
     */
    private fun checkOverlayPermissionOrProceed() {
        if (isOverlayPermissionGranted()) {
            goToMainAndFinish()
        }
        // If not granted, Compose will recompose and show OverlayPermissionScreen
    }

    /**
     * Returns true if the POST_NOTIFICATIONS permission is granted
     * or if the Android version does not require it.
     */
    private fun isNotificationsPermissionGranted(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true

        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Returns true if the SYSTEM_ALERT_WINDOW (overlay) permission is granted.
     * Always returns true below Android M since the permission did not exist.
     */
    private fun isOverlayPermissionGranted(): Boolean {
        return Settings.canDrawOverlays(this)
    }

    /**
     * Opens the system screen where the user can manually enable
     * the notification permission for this application.
     */
    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }

    /**
     * Opens the system screen where the user can grant the overlay permission.
     */
    private fun openOverlaySettings() {
        waitingForOverlayPermission = true
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            "package:$packageName".toUri()
        )
        startActivity(intent)
    }

    /**
     * Navigates to the main screen and removes this activity
     * from the back stack.
     */
    private fun goToMainAndFinish() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}