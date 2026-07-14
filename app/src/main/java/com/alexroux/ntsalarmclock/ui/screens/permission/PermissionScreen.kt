package com.alexroux.ntsalarmclock.ui.screens.permission

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alexroux.ntsalarmclock.R
import com.alexroux.ntsalarmclock.ui.components.NTSButton

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

        // After repeated denial, Android may stop showing a useful permission
        // dialog. Sending the user to app settings is the clearer recovery path.
        if (deniedCount >= PermissionViewModel.NOTIFICATION_SETTINGS_DENIAL_THRESHOLD) {
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

@Composable
fun OverlayPermissionScreen(
    onAllowClick: () -> Unit
) {
    // Overlay permission cannot be requested with ActivityResultContracts, so
    // the button opens the Android settings screen.
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
