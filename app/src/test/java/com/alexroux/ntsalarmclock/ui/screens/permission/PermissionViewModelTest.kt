package com.alexroux.ntsalarmclock.ui.screens.permission

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the permission-flow state machine.
 *
 * Android APIs are intentionally not used here. The Activity supplies raw
 * permission check results, and the ViewModel decides which UI state comes next.
 *
 * Each test creates a fresh ViewModel because permission progress and denial count are stateful.
 * Calls such as `onPermissionsChecked` simulate facts reported by Android; calls ending in `Result`
 * or `Opened` simulate a user returning from a permission interaction. Assertions compare the entire
 * [PermissionUiState], making both the current screen and any associated data visible.
 */
class PermissionViewModelTest {

    /**
     * Verifies that missing notification permission takes priority over the later overlay step
     * when neither permission has been granted.
     *
     * The Activity reports both checks as false. The expected state requests notifications with a
     * zero denial count, proving the flow starts with the permission required for foreground alarm
     * notifications and has not incorrectly counted a denial before asking the user.
     */
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

    /**
     * Verifies that each rejected notification request is counted so the UI can eventually
     * replace the retry action with a route to application settings.
     *
     * The initial permission check enters the notification step, then two `granted = false` results
     * simulate pressing Deny twice. The final state retains the same screen but carries count 2,
     * which is the information the Compose UI uses to show "Open settings" instead of asking again.
     */
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

    /**
     * Verifies that granting notification permission advances the flow to the required overlay
     * permission instead of completing setup immediately.
     *
     * Starting with both permissions absent establishes the normal sequence. A successful notification
     * result should move to [PermissionUiState.RequestOverlay], proving the ViewModel does not repeat
     * the first step or skip the second permission.
     */
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

    /**
     * Verifies that the flow reports completion when both Android permissions are already
     * available, allowing the Activity to continue into the app.
     *
     * No permission result callbacks are needed because the initial check reports both values as true.
     * The `Completed` assertion documents the short path used by returning users who have already
     * granted everything.
     */
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

    /**
     * Verifies the intentional one-time overlay prompt: after returning from system settings,
     * the user may continue even when overlay permission was not granted.
     *
     * The first check requests overlay permission. `onOverlaySettingsOpened()` records that the app has
     * already sent the user to Android settings, and the second unchanged check simulates returning
     * without granting it. Completion preserves the chosen UX: explain overlay once, but do not trap
     * the user in a loop if they decline.
     */
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
