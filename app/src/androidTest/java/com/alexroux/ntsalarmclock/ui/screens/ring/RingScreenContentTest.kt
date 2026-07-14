package com.alexroux.ntsalarmclock.ui.screens.ring

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.alexroux.ntsalarmclock.ui.theme.NTSAlarmClockTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Tests RingScreenContent in isolation from RingingActivity and the ViewModel.
 *
 * Injected state and callbacks keep these tests focused on rendering, decoded
 * show text, fallback messaging, and user actions.
 */
class RingScreenContentTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun ringScreen_showsRingingMessageCurrentShowVolumeAndStopButton() {
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                NTSAlarmClockTheme {
                    RingScreenContent(
                        isFallbackAudioActive = true,
                        // NTS show titles can include HTML entities; the UI
                        // should render the decoded value.
                        currentShow = "Breakfast &amp; Show",
                        volumeLive = 70,
                        onVolumeLiveChange = {},
                        onVolumeChangeFinished = {},
                        onStopClick = {}
                    )
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("TIME TO TUNE IN!").assertIsDisplayed()
        composeRule.onNodeWithText("Currently playing: Breakfast & Show").assertIsDisplayed()
        composeRule.onNodeWithText(
            "This backup music is playing because NTS cannot be played. Maybe your internet is disabled?"
        ).assertIsDisplayed()
        composeRule.onNodeWithText("VOLUME").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Alarm volume", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("STOP").assertIsDisplayed()
    }

    @Test
    fun stopButton_invokesStopCallback() {
        var stopClicked = false

        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                NTSAlarmClockTheme {
                    RingScreenContent(
                        isFallbackAudioActive = false,
                        currentShow = null,
                        volumeLive = 70,
                        onVolumeLiveChange = {},
                        onVolumeChangeFinished = {},
                        onStopClick = { stopClicked = true }
                    )
                }
            }
        }
        composeRule.waitForIdle()

        // The callback flag proves the button is wired without needing the real
        // RingScreenViewModel or playback service.
        composeRule.onNodeWithText("STOP").performClick()

        assertTrue(stopClicked)
    }
}
