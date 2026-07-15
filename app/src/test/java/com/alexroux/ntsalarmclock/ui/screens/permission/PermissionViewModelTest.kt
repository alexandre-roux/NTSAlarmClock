package com.alexroux.ntsalarmclock.ui.screens.permission

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the permission-flow state machine.
 *
 * Android APIs are intentionally not used here. The Activity supplies raw
 * permission check results, and the ViewModel decides which UI state comes next.
 */
class PermissionViewModelTest {

    @Test
    fun notificationsMissing_requestsNotificationPermission() {
        val viewModel = PermissionViewModel()

        // The Activity supplies raw Android permission results; the ViewModel
        // should ask for notification permission before overlay permission.
        viewModel.onPermissionsChecked(
            notificationsGranted = false,
            overlayGranted = false
        )

        assertEquals(
            PermissionUiState.RequestNotifications(deniedCount = 0),
            viewModel.uiState.value
        )
    }

    @Test
    fun notificationDenials_incrementDeniedCount() {
        val viewModel = PermissionViewModel()

        viewModel.onPermissionsChecked(
            notificationsGranted = false,
            overlayGranted = false
        )
        viewModel.onNotificationPermissionResult(granted = false)
        viewModel.onNotificationPermissionResult(granted = false)

        // The UI switches from "allow notifications" to "open settings" when
        // this deniedCount reaches the configured threshold.
        assertEquals(
            PermissionUiState.RequestNotifications(deniedCount = 2),
            viewModel.uiState.value
        )
    }

    @Test
    fun notificationGranted_thenOverlayMissing_requestsOverlayPermission() {
        val viewModel = PermissionViewModel()

        // Once notification permission is granted, the state machine advances to
        // the overlay step instead of completing the flow too early.
        viewModel.onPermissionsChecked(
            notificationsGranted = false,
            overlayGranted = false
        )
        viewModel.onNotificationPermissionResult(granted = true)

        assertEquals(PermissionUiState.RequestOverlay, viewModel.uiState.value)
    }

    @Test
    fun allPermissionsGranted_completesFlow() {
        val viewModel = PermissionViewModel()

        // Completed is a signal for the Activity to navigate to MainActivity.
        viewModel.onPermissionsChecked(
            notificationsGranted = true,
            overlayGranted = true
        )

        assertEquals(PermissionUiState.Completed, viewModel.uiState.value)
    }

    @Test
    fun returningFromOverlaySettings_completesEvenIfOverlayStillDenied() {
        val viewModel = PermissionViewModel()

        viewModel.onPermissionsChecked(
            notificationsGranted = true,
            overlayGranted = false
        )
        viewModel.onOverlaySettingsOpened()
        viewModel.onPermissionsChecked(
            notificationsGranted = true,
            overlayGranted = false
        )

        // This preserves the existing UX: ask for overlay once, then allow the
        // user into the app even if they decline in Android settings.
        assertEquals(PermissionUiState.Completed, viewModel.uiState.value)
    }
}
