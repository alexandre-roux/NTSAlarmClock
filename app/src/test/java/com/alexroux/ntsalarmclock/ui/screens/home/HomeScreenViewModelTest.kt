package com.alexroux.ntsalarmclock.ui.screens.home

import android.util.Log
import com.alexroux.ntsalarmclock.alarm.AlarmScheduler
import com.alexroux.ntsalarmclock.data.AlarmSettings
import com.alexroux.ntsalarmclock.data.AlarmSettingsRepository
import io.mockk.clearMocks
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek

/**
 * JVM tests for HomeScreenViewModel state, persistence commands, and scheduling.
 *
 * A MutableStateFlow stands in for the repository, while a test dispatcher keeps
 * StateFlow collection and scheduler side effects deterministic.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeScreenViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val repository = mockk<AlarmSettingsRepository>(relaxed = true)
    private val alarmScheduler = mockk<AlarmScheduler>(relaxed = true)
    private val scheduleTextFormatter = mockk<AlarmScheduleTextFormatter>()

    private val settingsFlow = MutableStateFlow(
        AlarmSettings(
            enabled = false,
            hour = 8,
            minute = 0,
            volume = 50,
            enabledDays = emptySet(),
            progressiveVolume = false
        )
    )

    @Before
    fun setup() {
        // The ViewModel launches work on Dispatchers.Main during collection, so
        // Main is replaced before each test constructs the ViewModel.
        Dispatchers.setMain(testDispatcher)

        // Mock Log because android.util.Log is a framework stub in local unit tests.
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { repository.settings } returns settingsFlow
        every {
            scheduleTextFormatter.format(any(), any(), any(), any(), any())
        } returns "Alarm is disabled"
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** A new ViewModel stays in Loading until its lazy state flow starts collecting repository data. */
    @Test
    fun initialState_isLoading() = runTest {
        val viewModel = createViewModel()

        assertTrue(viewModel.uiState.value is HomeScreenUiState.Loading)
    }

    /** Collecting the state maps every repository setting and the formatted schedule into Success. */
    @Test
    fun state_becomesSuccess_afterRepositoryEmits() = runTest {
        val viewModel = createViewModel()

        // StateFlow is lazy here; collecting it starts repository observation.
        val job = backgroundScope.launch { viewModel.uiState.collect() }

        advanceUntilIdle()

        val state = viewModel.uiState.value as HomeScreenUiState.Success
        assertEquals(8, state.hour)
        assertEquals(0, state.minute)
        assertEquals(false, state.enabled)
        assertEquals(50, state.volume)
        assertEquals(emptySet<DayOfWeek>(), state.enabledDays)
        assertEquals(false, state.progressiveVolume)
        assertEquals("Alarm is disabled", state.scheduledInText)

        job.cancel()
    }

    /** A time edit is persisted once with the exact hour and minute selected by the user. */
    @Test
    fun onTimeChange_updatesRepository() = runTest {
        val viewModel = createViewModel()
        val job = backgroundScope.launch { viewModel.uiState.collect() }

        advanceUntilIdle()

        // Ignore interactions caused by initial collection so the verification
        // only covers the user action under test.
        clearMocks(repository, alarmScheduler)

        viewModel.onTimeChange(9, 30)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.setTime(9, 30) }
        job.cancel()
    }

    /** Toggling an initialized, disabled alarm persists the inverse enabled state. */
    @Test
    fun onEnabledChange_togglesEnabledState() = runTest {
        val viewModel = createViewModel()
        val job = backgroundScope.launch { viewModel.uiState.collect() }

        advanceUntilIdle()
        clearMocks(repository, alarmScheduler)

        viewModel.onEnabledChange()
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.setEnabled(true) }
        job.cancel()
    }

    /** Enable toggles are ignored while settings are still loading because no current value exists. */
    @Test
    fun onEnabledChange_doesNothing_whileLoading() = runTest {
        val viewModel = createViewModel()

        viewModel.onEnabledChange()
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.setEnabled(any()) }
    }

    /** Toggling an unselected weekday adds it to the persisted enabled-day set. */
    @Test
    fun onToggleDay_addsDay_ifNotPresent() = runTest {
        val viewModel = createViewModel()
        val job = backgroundScope.launch { viewModel.uiState.collect() }

        advanceUntilIdle()
        clearMocks(repository, alarmScheduler)

        viewModel.onToggleDay(DayOfWeek.MONDAY)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.setEnabledDays(setOf(DayOfWeek.MONDAY)) }
        job.cancel()
    }

    /** Toggling an already selected weekday removes it from the persisted enabled-day set. */
    @Test
    fun onToggleDay_removesDay_ifPresent() = runTest {
        settingsFlow.value = settingsFlow.value.copy(enabledDays = setOf(DayOfWeek.MONDAY))

        val viewModel = createViewModel()
        val job = backgroundScope.launch { viewModel.uiState.collect() }

        advanceUntilIdle()
        clearMocks(repository, alarmScheduler)

        viewModel.onToggleDay(DayOfWeek.MONDAY)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.setEnabledDays(emptySet()) }
        job.cancel()
    }

    /** Direct volume edits are constrained to the supported 0..100 range before persistence. */
    @Test
    fun onVolumeChange_clampsVolume() = runTest {
        val viewModel = createViewModel()
        val job = backgroundScope.launch { viewModel.uiState.collect() }

        advanceUntilIdle()
        clearMocks(repository, alarmScheduler)

        viewModel.onVolumeChange(150)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.setVolume(100) }

        clearMocks(repository, alarmScheduler)

        viewModel.onVolumeChange(-10)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.setVolume(0) }
        job.cancel()
    }

    /** A hardware-key delta is applied relative to the volume in the current UI state. */
    @Test
    fun onHardwareVolumeKey_updatesVolumeRelativeToCurrentState() = runTest {
        settingsFlow.value = settingsFlow.value.copy(volume = 50)

        val viewModel = createViewModel()
        val job = backgroundScope.launch { viewModel.uiState.collect() }

        advanceUntilIdle()
        clearMocks(repository, alarmScheduler)

        viewModel.onHardwareVolumeKey(7)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.setVolume(57) }
        job.cancel()
    }

    /** Hardware-key adjustments cannot persist a volume above 100 or below zero. */
    @Test
    fun onHardwareVolumeKey_clampsVolumeToBounds() = runTest {
        settingsFlow.value = settingsFlow.value.copy(volume = 98)

        val viewModel = createViewModel()
        val job = backgroundScope.launch { viewModel.uiState.collect() }

        advanceUntilIdle()
        clearMocks(repository, alarmScheduler)

        viewModel.onHardwareVolumeKey(10)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.setVolume(100) }

        clearMocks(repository, alarmScheduler)
        settingsFlow.value = settingsFlow.value.copy(volume = 2)
        advanceUntilIdle()

        viewModel.onHardwareVolumeKey(-10)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.setVolume(0) }
        job.cancel()
    }

    /** Changing progressive-volume preference forwards the selected value to the repository once. */
    @Test
    fun onProgressiveVolumeEnabledChange_updatesRepository() = runTest {
        val viewModel = createViewModel()
        val job = backgroundScope.launch { viewModel.uiState.collect() }

        advanceUntilIdle()
        clearMocks(repository, alarmScheduler)

        viewModel.onProgressiveVolumeEnabledChange(true)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.setProgressiveVolume(true) }
        job.cancel()
    }

    /** An initially enabled configuration schedules its exact time and recurring weekdays. */
    @Test
    fun enabledScheduleConfig_schedulesNextAlarm() = runTest {
        settingsFlow.value = settingsFlow.value.copy(
            enabled = true,
            hour = 7,
            minute = 15,
            enabledDays = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)
        )

        val viewModel = createViewModel()
        val job = backgroundScope.launch { viewModel.uiState.collect() }

        advanceUntilIdle()

        coVerify(atLeast = 1) {
            alarmScheduler.scheduleNextAlarm(
                hour = 7,
                minute = 15,
                enabledDays = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)
            )
        }

        job.cancel()
    }

    /** An initially disabled configuration cancels any alarm that may already be scheduled. */
    @Test
    fun disabledScheduleConfig_cancelsAlarm() = runTest {
        val viewModel = createViewModel()
        val job = backgroundScope.launch { viewModel.uiState.collect() }

        advanceUntilIdle()

        coVerify(atLeast = 1) { alarmScheduler.cancelAlarm() }
        job.cancel()
    }

    /** When repository state changes from disabled to enabled, the current configuration is scheduled. */
    @Test
    fun changingEnabledState_falseToTrue_schedulesNextAlarm() = runTest {
        val viewModel = createViewModel()
        val job = backgroundScope.launch { viewModel.uiState.collect() }

        advanceUntilIdle()
        clearMocks(repository, alarmScheduler)

        settingsFlow.value = settingsFlow.value.copy(enabled = true)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            alarmScheduler.scheduleNextAlarm(
                hour = 8,
                minute = 0,
                enabledDays = emptySet()
            )
        }

        job.cancel()
    }

    /** When repository state changes from enabled to disabled, the existing alarm is cancelled. */
    @Test
    fun changingEnabledState_trueToFalse_cancelsAlarm() = runTest {
        settingsFlow.value = settingsFlow.value.copy(enabled = true)

        val viewModel = createViewModel()
        val job = backgroundScope.launch { viewModel.uiState.collect() }

        advanceUntilIdle()
        clearMocks(repository, alarmScheduler)

        settingsFlow.value = settingsFlow.value.copy(enabled = false)
        advanceUntilIdle()

        coVerify(exactly = 1) { alarmScheduler.cancelAlarm() }
        job.cancel()
    }

    /** Changing only playback volume leaves the already valid alarm schedule untouched. */
    @Test
    fun changingVolume_doesNotRescheduleAlarm() = runTest {
        settingsFlow.value = settingsFlow.value.copy(enabled = true)

        val viewModel = createViewModel()
        val job = backgroundScope.launch { viewModel.uiState.collect() }

        advanceUntilIdle()
        clearMocks(repository, alarmScheduler)

        // Volume changes are playback settings and should not touch AlarmManager.
        settingsFlow.value = settingsFlow.value.copy(volume = 80)
        advanceUntilIdle()

        confirmVerified(alarmScheduler)
        job.cancel()
    }

    /** Changing only progressive-volume behavior does not trigger another scheduler operation. */
    @Test
    fun changingProgressiveVolume_doesNotRescheduleAlarm() = runTest {
        settingsFlow.value = settingsFlow.value.copy(enabled = true)

        val viewModel = createViewModel()
        val job = backgroundScope.launch { viewModel.uiState.collect() }

        advanceUntilIdle()
        clearMocks(repository, alarmScheduler)

        settingsFlow.value = settingsFlow.value.copy(progressiveVolume = true)
        advanceUntilIdle()

        confirmVerified(alarmScheduler)
        job.cancel()
    }

    /** An update with unchanged scheduling fields must not schedule the same alarm again. */
    @Test
    fun identicalScheduleConfig_doesNotRescheduleAlarmAgain() = runTest {
        settingsFlow.value = settingsFlow.value.copy(enabled = true)

        val viewModel = createViewModel()
        val job = backgroundScope.launch { viewModel.uiState.collect() }

        advanceUntilIdle()
        clearMocks(repository, alarmScheduler)

        settingsFlow.value = settingsFlow.value.copy(volume = 50)
        advanceUntilIdle()

        confirmVerified(alarmScheduler)
        job.cancel()
    }

    /** Changing the time of an enabled alarm replaces its schedule with the new hour and minute. */
    @Test
    fun changingTime_reschedulesAlarm_whenEnabled() = runTest {
        settingsFlow.value = settingsFlow.value.copy(enabled = true)

        val viewModel = createViewModel()
        val job = backgroundScope.launch { viewModel.uiState.collect() }

        advanceUntilIdle()
        clearMocks(repository, alarmScheduler)

        settingsFlow.value = settingsFlow.value.copy(hour = 9, minute = 45)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            alarmScheduler.scheduleNextAlarm(
                hour = 9,
                minute = 45,
                enabledDays = emptySet()
            )
        }

        job.cancel()
    }

    /** Changing weekdays on an enabled alarm reschedules it with the updated recurrence set. */
    @Test
    fun changingEnabledDays_reschedulesAlarm_whenEnabled() = runTest {
        settingsFlow.value = settingsFlow.value.copy(enabled = true)

        val viewModel = createViewModel()
        val job = backgroundScope.launch { viewModel.uiState.collect() }

        advanceUntilIdle()
        clearMocks(repository, alarmScheduler)

        settingsFlow.value = settingsFlow.value.copy(enabledDays = setOf(DayOfWeek.TUESDAY))
        advanceUntilIdle()

        coVerify(exactly = 1) {
            alarmScheduler.scheduleNextAlarm(
                hour = 8,
                minute = 0,
                enabledDays = setOf(DayOfWeek.TUESDAY)
            )
        }

        job.cancel()
    }

    private fun createViewModel(): HomeScreenViewModel {
        return HomeScreenViewModel(
            repository = repository,
            alarmScheduler = alarmScheduler,
            scheduleTextFormatter = scheduleTextFormatter
        )
    }
}
