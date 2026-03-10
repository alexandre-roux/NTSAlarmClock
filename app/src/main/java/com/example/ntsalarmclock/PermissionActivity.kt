package com.example.ntsalarmclock

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.ntsalarmclock.ui.components.NTSButton
import com.example.ntsalarmclock.ui.theme.NTSAlarmClockTheme

/**
 * Activity responsible for requesting the notifications permission.
 *
 * On Android 13+ the POST_NOTIFICATIONS permission is required for
 * alarm notifications and full screen intents to work properly.
 *
 * This activity is shown only if the permission has not yet been granted.
 * Once granted, the user is redirected to MainActivity.
 */
class PermissionActivity : ComponentActivity() {

    // Number of times the user denied the permission request
    private var deniedCount by mutableIntStateOf(0)

    /**
     * Launcher used to request the POST_NOTIFICATIONS permission.
     * If granted we immediately continue to the main screen.
     */
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                goToMainAndFinish()
            } else {
                deniedCount += 1
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // If permission is already granted, skip this screen entirely
        if (isNotificationsPermissionGranted()) {
            goToMainAndFinish()
            return
        }

        setContent {
            NTSAlarmClockTheme {
                Surface {
                    PermissionScreen(
                        deniedCount = deniedCount,
                        onAllowClick = { requestNotificationsPermission() },
                        onOpenSettingsClick = { openAppSettings() }
                    )
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
     * Preview used by Android Studio to visualize the permission screen.
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
     * When returning from the system settings screen we re-check the permission
     * and continue to MainActivity if it has been granted.
     */
    override fun onResume() {
        super.onResume()

        if (isNotificationsPermissionGranted()) {
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
            goToMainAndFinish()
        }
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
     * Navigates to the main screen and removes this activity
     * from the back stack.
     */
    private fun goToMainAndFinish() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}