package com.alexroux.ntsalarmclock

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

    @Test
    fun permissionScreen_showsNotificationPermissionCopyAndAllowAction() {
        var allowClicked = false

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

        composeRule.onNodeWithText("NOTIFICATIONS PERMISSION REQUIRED").assertIsDisplayed()
        composeRule.onNodeWithText("This app needs notification permission to be able to run the alarm")
            .assertIsDisplayed()
        composeRule.onNodeWithText("ALLOW NOTIFICATIONS").performClick()

        assertTrue(allowClicked)
    }

    @Test
    fun permissionScreen_showsSettingsActionAfterRepeatedDenials() {
        var settingsClicked = false

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

        assertTrue(
            composeRule.onAllNodesWithText("ALLOW NOTIFICATIONS").fetchSemanticsNodes().isEmpty()
        )
        composeRule.onNodeWithText("OPEN SETTINGS").performClick()

        assertTrue(settingsClicked)
    }

    @Test
    fun overlayPermissionScreen_showsOverlayCopyAndAllowAction() {
        var allowOverlayClicked = false

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

        composeRule.onNodeWithText("OVERLAY PERMISSION REQUIRED").assertIsDisplayed()
        composeRule.onNodeWithText("This app needs the overlay permission to show the alarm on the screen")
            .assertIsDisplayed()
        composeRule.onNodeWithText("ALLOW OVERLAY").performClick()

        assertTrue(allowOverlayClicked)
    }
}
