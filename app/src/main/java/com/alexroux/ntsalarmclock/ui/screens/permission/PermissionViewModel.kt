package com.alexroux.ntsalarmclock.ui.screens.permission

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * UI states for the permission flow.
 *
 * `Completed` is not rendered as a screen; the Activity observes it and
 * navigates to MainActivity.
 */
sealed interface PermissionUiState {
    data object Loading : PermissionUiState
    data class RequestNotifications(val deniedCount: Int) : PermissionUiState
    data object RequestOverlay : PermissionUiState
    data object Completed : PermissionUiState
}

/**
 * Owns the permission-flow state machine.
 *
 * Android permission checks and system-intent launches stay in PermissionActivity;
 * this ViewModel decides which screen should be shown next.
 */
class PermissionViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<PermissionUiState>(PermissionUiState.Loading)
    val uiState: StateFlow<PermissionUiState> = _uiState.asStateFlow()

    private var notificationsGranted = false
    private var overlayGranted = false
    private var deniedCount = 0
    private var waitingForOverlayReturn = false

    /**
     * Called whenever the Activity has a fresh snapshot of Android permission
     * state, typically from onCreate() and onResume().
     */
    fun onPermissionsChecked(
        notificationsGranted: Boolean,
        overlayGranted: Boolean
    ) {
        this.notificationsGranted = notificationsGranted
        this.overlayGranted = overlayGranted

        if (waitingForOverlayReturn) {
            waitingForOverlayReturn = false
            // Overlay is helpful for downloaded APK behavior, but the old flow
            // intentionally did not block the user forever after returning.
            _uiState.value = PermissionUiState.Completed
            return
        }

        updateUiState()
    }

    /**
     * Called by the ActivityResult launcher after POST_NOTIFICATIONS returns.
     */
    fun onNotificationPermissionResult(granted: Boolean) {
        notificationsGranted = granted
        if (!granted) {
            deniedCount += 1
        }
        updateUiState()
    }

    /**
     * Overlay settings have no direct result callback. This flag lets the next
     * onResume() preserve the existing UX: continue after one settings visit.
     */
    fun onOverlaySettingsOpened() {
        waitingForOverlayReturn = true
    }

    // Keep the state transition table in one place so tests can exercise the
    // permission flow without launching Android UI or system settings.
    private fun updateUiState() {
        _uiState.value = when {
            !notificationsGranted -> PermissionUiState.RequestNotifications(deniedCount)
            !overlayGranted -> PermissionUiState.RequestOverlay
            else -> PermissionUiState.Completed
        }
    }

    companion object {
        const val NOTIFICATION_SETTINGS_DENIAL_THRESHOLD = 2
    }
}
