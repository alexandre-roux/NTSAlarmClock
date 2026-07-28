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
 *
 * How to read these tests:
 * - [settingsFlow] behaves like the stream of saved alarm preferences from DataStore. Assigning a
 *   new value simulates the repository reporting that the stored settings changed.
 * - `backgroundScope.launch { viewModel.uiState.collect() }` represents the Home screen observing
 *   the ViewModel. The ViewModel's state is lazy, so repository collection does not start until
 *   something observes it.
 * - `advanceUntilIdle()` runs every coroutine currently queued on the test dispatcher. Without it,
 *   an assertion could run before the ViewModel has processed the simulated event.
 * - `clearMocks(...)` forgets calls made during initialization. Later verifications therefore
 *   describe only the user action or repository update being tested.
 * - `coVerify` checks calls to suspend functions. `confirmVerified` is stronger in the negative
 *   scheduling tests: it proves that no unverified scheduler call happened.
 * - Each collector job is cancelled at the end because the real screen would stop collecting when
 *   it leaves composition; the test must perform that lifecycle cleanup explicitly.
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

    /**
     * Scenario: the ViewModel has been created, but no screen is collecting [HomeScreenViewModel.uiState].
     *
     * Because the state flow is lazy, repository observation has not begun yet. The assertion proves
     * that callers receive a safe `Loading` value instead of partially initialized alarm settings.
     */
    @Test
    fun initialState_isLoading() = runTest {
        val viewModel = createViewModel()

        assertTrue(viewModel.uiState.value is HomeScreenUiState.Loading)
    }

    /**
     * Scenario: the Home screen begins collecting state while the repository contains its default
     * settings and the formatter returns "Alarm is disabled".
     *
     * Starting collection and draining queued coroutines should convert the repository model into a
     * [HomeScreenUiState.Success]. Every assertion checks one mapping so a missing or swapped field is
     * easy to identify, including the separately calculated schedule description.
     */
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

    /**
     * Scenario: settings have loaded and the user changes the alarm time to 09:30.
     *
     * Calls produced by initial collection are cleared first. The final verification therefore
     * proves that [HomeScreenViewModel.onTimeChange] sends exactly the selected hour and minute to
     * the repository once, rather than mutating the fake state directly or writing twice.
     */
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

    /**
     * Scenario: the loaded alarm is disabled and the user presses its enable switch.
     *
     * The ViewModel must read the current UI state, invert `false` to `true`, and persist that new
     * value. Verifying one `setEnabled(true)` call checks both the toggle calculation and delegation
     * to the repository.
     */
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

    /**
     * Scenario: the user triggers the enable action before repository settings have loaded.
     *
     * A `Loading` state has no trustworthy enabled value to invert. The zero-call verification
     * proves the ViewModel safely ignores the action instead of guessing a value and overwriting
     * persisted settings.
     */
    @Test
    fun onEnabledChange_doesNothing_whileLoading() = runTest {
        val viewModel = createViewModel()

        viewModel.onEnabledChange()
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.setEnabled(any()) }
    }

    /**
     * Scenario: no repeat days are selected and the user taps Monday.
     *
     * The ViewModel should treat weekday selection like a set toggle. The repository verification
     * proves Monday is added while the original empty set is not otherwise modified.
     */
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

    /**
     * Scenario: Monday is already a repeat day and the user taps Monday again.
     *
     * The test seeds that state before constructing the ViewModel. Persisting an empty set proves
     * the same toggle operation removes an existing day instead of adding a duplicate or leaving it
     * selected.
     */
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

    /**
     * Scenario: the UI reports volume values outside the supported 0 through 100 range.
     *
     * The upper and lower cases are checked independently, clearing calls between them. Persisting
     * 100 for an input of 150 and 0 for an input of -10 proves invalid slider/input values can never
     * reach storage.
     */
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

    /**
     * Scenario: the saved volume is 50 and a hardware key event requests a relative increase of 7.
     *
     * Unlike the absolute volume callback, this action must add the delta to the current state.
     * Verifying that 57 is persisted proves the ViewModel used the loaded value as its starting point.
     */
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

    /**
     * Scenario: relative hardware-key adjustments would cross both volume boundaries.
     *
     * Starting from 98, `+10` must become 100; after the simulated repository moves to 2, `-10`
     * must become 0. Together these checks prove relative updates use the latest state and apply the
     * same safety bounds as direct edits.
     */
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

    /**
     * Scenario: settings are loaded and the user enables progressive volume.
     *
     * This preference belongs in persistent alarm settings. The verification proves the ViewModel
     * forwards `true` exactly once and does not handle persistence itself.
     */
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

    /**
     * Scenario: repository collection starts with an enabled 07:15 alarm repeating Monday and Friday.
     *
     * Initial synchronization should register that configuration with [AlarmScheduler]. `atLeast = 1`
     * is used because startup collection may legitimately emit more than once; the important contract
     * is that the correct time and complete weekday set are scheduled.
     */
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

    /**
     * Scenario: the repository's initial alarm configuration is disabled.
     *
     * The ViewModel cannot assume AlarmManager is already clean—for example, a stale system alarm may
     * remain after process recreation. Verifying cancellation proves initial state synchronization
     * actively removes any such alarm.
     */
    @Test
    fun disabledScheduleConfig_cancelsAlarm() = runTest {
        val viewModel = createViewModel()
        val job = backgroundScope.launch { viewModel.uiState.collect() }

        advanceUntilIdle()

        coVerify(atLeast = 1) { alarmScheduler.cancelAlarm() }
        job.cancel()
    }

    /**
     * Scenario: an observed alarm changes from disabled to enabled without changing its default time.
     *
     * Initialization calls are cleared, then the fake repository emits the new state. Exactly one
     * scheduler call with 08:00 and no repeat days proves the ViewModel reacts to the transition and
     * uses the complete current schedule configuration.
     */
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

    /**
     * Scenario: an initially enabled alarm becomes disabled in the repository.
     *
     * After ignoring startup interactions, the test emits the disabled state. One cancellation proves
     * the ViewModel removes the platform alarm as soon as persistence reports this transition.
     */
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

    /**
     * Scenario: an enabled alarm remains enabled while only its playback volume changes.
     *
     * Volume affects how the alarm sounds, not when it fires. After clearing initialization calls,
     * [confirmVerified] proves the repository emission causes no scheduler interaction at all, avoiding
     * unnecessary cancellation and re-registration with Android's AlarmManager.
     */
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

    /**
     * Scenario: an enabled alarm keeps the same time and days while progressive volume is enabled.
     *
     * Progressive volume is another playback-only option. The absence of any scheduler call proves
     * scheduling decisions compare only fields that affect the trigger time.
     */
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

    /**
     * Scenario: the repository emits a new settings object whose scheduling fields are identical to
     * the already processed enabled configuration.
     *
     * Even though StateFlow receives an assignment, the ViewModel should not register an equivalent
     * alarm again. No scheduler interaction proves duplicate schedule work is suppressed.
     */
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

    /**
     * Scenario: an enabled alarm's time changes from 08:00 to 09:45.
     *
     * Time directly affects the next trigger, so this update must reach the scheduler. The exact
     * argument verification proves the new time is used while the unchanged empty repeat-day set is
     * preserved.
     */
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

    /**
     * Scenario: an enabled one-shot alarm becomes a Tuesday repeating alarm without changing 08:00.
     *
     * Repeat days affect which date should fire next. The verification proves the ViewModel registers
     * a replacement using Tuesday while retaining the current hour and minute.
     */
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
