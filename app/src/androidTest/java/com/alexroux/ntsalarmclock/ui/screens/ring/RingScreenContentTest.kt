package com.alexroux.ntsalarmclock.ui.screens.ring

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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
 *
 * A real [ComponentActivity] hosts Compose on a device/emulator. After `setContent`, `waitForIdle()`
 * waits until composition and pending recomposition complete. Text and content-description lookups then
 * inspect Compose's semantics tree, which is also what accessibility services use. No playback service is
 * launched; Boolean flags stand in for callbacks owned by the surrounding screen.
 */
class RingScreenContentTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    /**
     * Verifies the complete ringing state, including decoded show text, the fallback-audio
     * warning, volume controls, and the stop action.
     *
     * The injected show contains the HTML entity `&amp;`, matching titles that can arrive from NTS. The
     * expected visible title proves the UI decodes it to `&`. Setting fallback audio active should reveal
     * the explanatory warning. The remaining assertions document the stable ringing heading, visible
     * volume control/accessibility label, and stop button required in the full alarm state.
     */
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
                        currentVolume = 70,
                        onVolumeChange = {},
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

    /**
     * Verifies that tapping the stop button delegates alarm shutdown to the callback supplied
     * by the owning screen or ViewModel.
     *
     * The test does not need a real alarm or service: `stopClicked` starts false and the injected callback
     * changes it to true. Performing a semantics click on "STOP" and checking the flag proves the button
     * is actionable and wired to its owner rather than merely drawn on screen.
     */
    @Test
    fun stopButton_invokesStopCallback() {
        var stopClicked = false

        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                NTSAlarmClockTheme {
                    RingScreenContent(
                        isFallbackAudioActive = false,
                        currentShow = null,
                        currentVolume = 70,
                        onVolumeChange = {},
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

    /**
     * Verifies the normal online state omits current-show and fallback-audio messages when the
     * corresponding data and fallback flag are absent.
     *
     * `currentShow = null` means no title is available, and `isFallbackAudioActive = false` means the NTS
     * path has not reported backup playback. Empty node lists prove conditional content is truly absent,
     * not just hidden visually. The stop button must remain available regardless of optional status text.
     */
    @Test
    fun ringScreen_hidesCurrentShowAndFallbackMessageWhenUnavailable() {
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                NTSAlarmClockTheme {
                    RingScreenContent(
                        isFallbackAudioActive = false,
                        currentShow = null,
                        currentVolume = 70,
                        onVolumeChange = {},
                        onVolumeChangeFinished = {},
                        onStopClick = {}
                    )
                }
            }
        }
        composeRule.waitForIdle()

        // This verifies the normal online state: no stale show label and no
        // fallback warning should be displayed unless the ViewModel says so.
        assertTrue(
            composeRule.onAllNodesWithText("Currently playing:").fetchSemanticsNodes().isEmpty()
        )
        assertTrue(
            composeRule.onAllNodesWithText(
                "This backup music is playing because NTS cannot be played. Maybe your internet is disabled?"
            ).fetchSemanticsNodes().isEmpty()
        )
        composeRule.onNodeWithText("STOP").assertIsDisplayed()
    }
}
