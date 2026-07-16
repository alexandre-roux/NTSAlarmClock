package com.alexroux.ntsalarmclock

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.alexroux.ntsalarmclock.ui.screens.permission.OverlayPermissionScreen
import com.alexroux.ntsalarmclock.ui.screens.permission.PermissionScreen
import com.alexroux.ntsalarmclock.ui.theme.NTSAlarmClockTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Instrumented Compose tests for the permission UI states.
 *
 * The screens are mounted in a lightweight ComponentActivity so assertions use
 * the real Android Compose semantics tree and click handling.
 */
class PermissionScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    /**
     * Verifies the initial notification-permission state: the rationale is visible and
     * the allow button delegates the permission request to its callback.
     */
    @Test
    fun permissionScreen_showsNotificationPermissionCopyAndAllowAction() {
        var allowClicked = false

        // deniedCount = 0 represents the first permission request, before any rejection.
        // setContent must run on the Activity thread; waitForIdle below lets
        // composition settle before querying the semantics tree.
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                NTSAlarmClockTheme {
                    PermissionScreen(
                        deniedCount = 0,
                        onAllowClick = { allowClicked = true },
                        onOpenSettingsClick = {}
                    )
                }
            }
        }
        composeRule.waitForIdle()

        // Check the user sees both the reason for the request and the action that can grant it.
        composeRule.onNodeWithText("NOTIFICATIONS PERMISSION REQUIRED").assertIsDisplayed()
        composeRule.onNodeWithText("This app needs notification permission to be able to run the alarm")
            .assertIsDisplayed()

        // Exercise the primary action exactly as a user would from the permission screen.
        composeRule.onNodeWithText("ALLOW NOTIFICATIONS").performClick()

        // The screen does not request Android permissions itself; it must notify its caller.
        assertTrue(allowClicked)
    }

    /**
     * Verifies that repeated notification-permission denials replace the direct allow action
     * with an app-settings action, where the user can enable the permission manually.
     */
    @Test
    fun permissionScreen_showsSettingsActionAfterRepeatedDenials() {
        var settingsClicked = false

        // deniedCount >= 2 models Android's repeated denial path, where asking
        // again is less useful than sending the user to app settings.
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                NTSAlarmClockTheme {
                    PermissionScreen(
                        deniedCount = 2,
                        onAllowClick = {},
                        onOpenSettingsClick = { settingsClicked = true }
                    )
                }
            }
        }
        composeRule.waitForIdle()

        // Only the settings route should be available after the repeated-denial threshold.
        assertTrue(
            composeRule.onAllNodesWithText("ALLOW NOTIFICATIONS").fetchSemanticsNodes().isEmpty()
        )

        // The remaining action should ask the Activity to open the application's settings.
        composeRule.onNodeWithText("OPEN SETTINGS").performClick()

        // Confirm the settings button delegates navigation to the owning Activity.
        assertTrue(settingsClicked)
    }

    /**
     * Verifies that one denial is still treated as retryable, keeping the runtime-permission
     * action available instead of directing the user to app settings too early.
     */
    @Test
    fun permissionScreen_keepsAllowActionAfterSingleDenial() {
        var allowClicked = false

        // deniedCount = 1 models the first denial, when requesting the permission again is valid.
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                NTSAlarmClockTheme {
                    PermissionScreen(
                        deniedCount = 1,
                        onAllowClick = { allowClicked = true },
                        onOpenSettingsClick = {}
                    )
                }
            }
        }
        composeRule.waitForIdle()

        // After one denial, the app still presents the direct permission request
        // instead of jumping to system settings.
        assertTrue(composeRule.onAllNodesWithText("OPEN SETTINGS").fetchSemanticsNodes().isEmpty())

        // Retry the permission request through the action that remains visible.
        composeRule.onNodeWithText("ALLOW NOTIFICATIONS").performClick()

        // Confirm that the retry action reaches the permission-request callback.
        assertTrue(allowClicked)
    }

    /**
     * Verifies the overlay-permission rationale and confirms that its allow button delegates
     * launching the system overlay settings to the caller.
     */
    @Test
    fun overlayPermissionScreen_showsOverlayCopyAndAllowAction() {
        var allowOverlayClicked = false

        // The overlay screen is pure UI; the Activity owns the actual settings
        // intent, so the test only verifies the callback wiring.
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                NTSAlarmClockTheme {
                    OverlayPermissionScreen(
                        onAllowClick = { allowOverlayClicked = true }
                    )
                }
            }
        }
        composeRule.waitForIdle()

        // Validate the overlay-specific explanation before exercising its primary action.
        composeRule.onNodeWithText("OVERLAY PERMISSION REQUIRED").assertIsDisplayed()
        composeRule.onNodeWithText("This app needs the overlay permission to show the alarm on the screen")
            .assertIsDisplayed()

        // Simulate choosing the action that opens Android's overlay-permission settings.
        composeRule.onNodeWithText("ALLOW OVERLAY").performClick()

        // A callback invocation proves the button is wired to the external settings flow.
        assertTrue(allowOverlayClicked)
    }
}
