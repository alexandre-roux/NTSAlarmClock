package com.alexroux.ntsalarmclock.ui.screens.ring

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.viewModelScope
import com.alexroux.ntsalarmclock.alarm.AlarmNotification
import com.alexroux.ntsalarmclock.data.AlarmSettings
import com.alexroux.ntsalarmclock.data.AlarmSettingsRepository
import com.alexroux.ntsalarmclock.data.nts.NtsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * JVM tests for RingScreenViewModel without launching RingingActivity.
 *
 * Android collaborators are mocked, and Dispatchers.Main is replaced with a
 * test dispatcher so viewModelScope work is deterministic. Each test cancels
 * viewModelScope because the ViewModel keeps a show-polling coroutine alive.
 *
 * [settingsFlow] simulates live alarm preferences from DataStore. MockK verifies commands sent to
 * Android-facing collaborators without starting a real service or notification manager. `runCurrent()`
 * executes work already queued on the test dispatcher; it is used after construction for `init` work
 * and after suspend actions before their results are asserted.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RingScreenViewModelTest {

    private val context = mockk<Context>(relaxed = true)
    private val repository = mockk<AlarmSettingsRepository>(relaxed = true)
    private val ntsRepository = mockk<NtsRepository>()
    private val notificationManager = mockk<NotificationManagerCompat>(relaxed = true)
    private val settingsFlow = MutableStateFlow(
        AlarmSettings(
            enabled = true,
            hour = 7,
            minute = 0,
            volume = 70,
            enabledDays = emptySet(),
            progressiveVolume = false
        )
    )

    @Before
    fun setup() {
        // NotificationManagerCompat.from(...) is static, so MockK intercepts it
        // to avoid touching the real notification service in JVM tests.
        mockkStatic(NotificationManagerCompat::class)

        // Reset the shared flow before each test so repository updates from one
        // test do not leak into the next one.
        settingsFlow.value = AlarmSettings(
            enabled = true,
            hour = 7,
            minute = 0,
            volume = 70,
            enabledDays = emptySet(),
            progressiveVolume = false
        )
        every { NotificationManagerCompat.from(context) } returns notificationManager
        every { context.startService(any()) } returns null
        every { notificationManager.cancel(any()) } just runs
        every { repository.settings } returns settingsFlow
        coEvery { ntsRepository.getCurrentShow() } returns Result.success(null)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    /**
     * Verifies that stopping an alarm tells the playback service to stop and removes the
     * ongoing alarm notification so no ringing UI remains active.
     *
     * The ViewModel is created only after replacing `Dispatchers.Main`, then its initialization work is
     * drained. Calling `stopAlarm()` should issue one service command and cancel the specific alarm
     * notification ID exactly once. The `finally` block always cancels the polling scope, even if an
     * assertion fails.
     */
    @Test
    fun stopAlarm_stopsPlaybackServiceAndCancelsNotification() = runTest {
        // Main must be replaced before creating the ViewModel because init
        // starts collectors in viewModelScope.
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        val viewModel = createViewModel()
        try {
            // Run pending init coroutines before exercising the behavior under test.
            runCurrent()

            viewModel.stopAlarm()

            verify(exactly = 1) { context.startService(any()) }
            verify(exactly = 1) {
                notificationManager.cancel(AlarmNotification.NOTIFICATION_ID)
            }
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    /**
     * Verifies that live volume changes are capped at the supported maximum before the
     * ViewModel updates its state and forwards the value to the playback service.
     *
     * Passing 150 simulates a UI or external input above the valid percentage range. The state assertion
     * proves the displayed value becomes 100 immediately, while the service-call verification proves
     * the normalized change is also sent to active playback.
     */
    @Test
    fun onVolumeChange_clampsAndSendsVolumeToPlaybackService() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        val viewModel = createViewModel()
        try {
            runCurrent()

            viewModel.onVolumeChange(150)

            assertEquals(100, viewModel.currentVolume.value)
            verify(exactly = 1) { context.startService(any()) }
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    /**
     * Verifies that finishing a volume adjustment clamps values below zero and persists the
     * normalized value in the alarm settings repository.
     *
     * Passing -5 exercises the lower boundary. Unlike a live drag update, the "finished" callback must
     * save the final value, so the test checks both observable state 0 and one suspend call to
     * `repository.setVolume(0)` after queued coroutine work runs.
     */
    @Test
    fun onVolumeChangeFinished_clampsAndPersistsVolume() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = createViewModel()
        try {
            runCurrent()

            viewModel.onVolumeChangeFinished(-5)
            runCurrent()

            assertEquals(0, viewModel.currentVolume.value)
            coVerify(exactly = 1) { repository.setVolume(0) }
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    /**
     * Verifies that volume changes emitted by the settings repository are collected into the
     * ViewModel's observable volume state.
     *
     * Assigning a copy with volume 35 simulates persistence changing outside the immediate callback.
     * Running queued collectors and observing 35 proves the ViewModel treats the repository flow as the
     * source of truth rather than keeping a disconnected local value.
     */
    @Test
    fun repositoryVolume_updatesCurrentVolume() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = createViewModel()
        try {
            runCurrent()

            // Updating the fake repository flow verifies the ViewModel collector
            // reacts to persisted setting changes.
            settingsFlow.value = settingsFlow.value.copy(volume = 35)
            runCurrent()

            assertEquals(35, viewModel.currentVolume.value)
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    /**
     * Verifies that the currently airing NTS show is fetched during ViewModel initialization
     * and exposed to the ringing screen.
     *
     * The NTS repository is stubbed to return a successful title before construction. Draining init
     * coroutines and observing "Breakfast Show" proves startup performs the fetch and publishes its
     * payload, allowing the UI to label what is playing.
     */
    @Test
    fun currentShow_isLoadedOnStart() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        coEvery { ntsRepository.getCurrentShow() } returns Result.success("Breakfast Show")

        val viewModel = createViewModel()
        try {
            runCurrent()

            assertEquals("Breakfast Show", viewModel.currentShow.value)
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    private fun createViewModel(): RingScreenViewModel {
        return RingScreenViewModel(
            context = context,
            repository = repository,
            ntsRepository = ntsRepository
        )
    }
}
