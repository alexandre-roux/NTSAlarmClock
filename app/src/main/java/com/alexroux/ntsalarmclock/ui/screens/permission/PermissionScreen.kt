package com.alexroux.ntsalarmclock.ui.screens.permission

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.alexroux.ntsalarmclock.R
import com.alexroux.ntsalarmclock.ui.components.NTSButton
import com.alexroux.ntsalarmclock.ui.theme.NTSAlarmClockTheme

/**
 * Renders the current permission step from ViewModel state.
 *
 * The route stays stateless: it receives state and callbacks from the Activity
 * and never performs Android permission or navigation side effects itself.
 */
@Composable
fun PermissionRoute(
    uiState: PermissionUiState,
    onAllowNotificationsClick: () -> Unit,
    onOpenNotificationSettingsClick: () -> Unit,
    onAllowOverlayClick: () -> Unit
) {
    when (uiState) {
        // Loading is transient before the first permission snapshot. Completed
        // is handled by PermissionActivity, which performs navigation.
        PermissionUiState.Loading,
        PermissionUiState.Completed -> Unit

        is PermissionUiState.RequestNotifications -> {
            PermissionScreen(
                deniedCount = uiState.deniedCount,
                onAllowClick = onAllowNotificationsClick,
                onOpenSettingsClick = onOpenNotificationSettingsClick
            )
        }

        PermissionUiState.RequestOverlay -> {
            OverlayPermissionScreen(
                onAllowClick = onAllowOverlayClick
            )
        }
    }
}

@Composable
fun PermissionScreen(
    deniedCount: Int,
    onAllowClick: () -> Unit,
    onOpenSettingsClick: () -> Unit
) {
    val shouldOpenSettings =
        deniedCount >= PermissionViewModel.NOTIFICATION_SETTINGS_DENIAL_THRESHOLD

    PermissionPrompt(
        title = stringResource(R.string.notifications_permission_required),
        description = stringResource(R.string.this_app_needs_notification_permission_to_work),
        actionText = stringResource(
            if (shouldOpenSettings) R.string.open_settings else R.string.allow_notifications
        ),
        onActionClick = if (shouldOpenSettings) onOpenSettingsClick else onAllowClick
    )
}

@Composable
fun OverlayPermissionScreen(
    onAllowClick: () -> Unit
) {
    // Overlay permission cannot be requested with ActivityResultContracts, so
    // the button opens the Android settings screen.
    PermissionPrompt(
        title = stringResource(R.string.overlay_permission_required),
        description = stringResource(R.string.overlay_permission_explanation),
        actionText = stringResource(R.string.allow_overlay),
        onActionClick = onAllowClick
    )
}

@Composable
private fun PermissionPrompt(
    title: String,
    description: String,
    actionText: String,
    onActionClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(36.dp))

        NTSButton(
            text = actionText,
            textStyle = MaterialTheme.typography.headlineMedium,
            onClick = onActionClick
        )
    }
}

@Preview(showBackground = true, name = "Notifications Permission - English", locale = "en")
@Preview(showBackground = true, name = "Notifications Permission - French", locale = "fr")
@Preview(showBackground = true, name = "Notifications Permission - German", locale = "de")
@Composable
private fun PermissionScreenPreview() {
    NTSAlarmClockTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            PermissionScreen(
                deniedCount = 0,
                onAllowClick = {},
                onOpenSettingsClick = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "Notifications Repeatedly Denied - English", locale = "en")
@Preview(showBackground = true, name = "Notifications Repeatedly Denied - French", locale = "fr")
@Preview(showBackground = true, name = "Notifications Repeatedly Denied - German", locale = "de")
@Composable
private fun PermissionScreenRepeatedlyDeniedPreview() {
    NTSAlarmClockTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            PermissionScreen(
                deniedCount = PermissionViewModel.NOTIFICATION_SETTINGS_DENIAL_THRESHOLD,
                onAllowClick = {},
                onOpenSettingsClick = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "Overlay Permission - English", locale = "en")
@Preview(showBackground = true, name = "Overlay Permission - French", locale = "fr")
@Preview(showBackground = true, name = "Overlay Permission - German", locale = "de")
@Composable
private fun OverlayPermissionScreenPreview() {
    NTSAlarmClockTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            OverlayPermissionScreen(
                onAllowClick = {}
            )
        }
    }
}
