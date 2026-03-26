package com.alexroux.ntsalarmclock.ui.screens.home

import android.util.Log
import com.alexroux.ntsalarmclock.alarm.AlarmScheduler
import com.alexroux.ntsalarmclock.data.AlarmSettings
import com.alexroux.ntsalarmclock.data.AlarmSettingsRepository
import com.alexroux.ntsalarmclock.playback.NTS_STREAM_URL
import com.alexroux.ntsalarmclock.ui.components.DayOfWeekUi
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

@OptIn(ExperimentalCoroutinesApi::class)
class HomeScreenViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val repository = mockk<AlarmSettingsRepository>(relaxed = true)
    private val alarmScheduler = mockk<AlarmScheduler>(relaxed = true)

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
        Dispatchers.setMain(testDispatcher)
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { repository.settings } returns settingsFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_isLoading() = runTest {
        val viewModel = createViewModel()

        assertTrue(viewModel.uiState.value is HomeScreenUiState.Loading)
    }

    @Test
    fun state_becomesSuccess_afterRepositoryEmits() = runTest {
        val viewModel = createViewModel()
        val job = backgroundScope.launch { viewModel.uiState.collect() }

        advanceUntilIdle()

        val state = viewModel.uiState.value as HomeScreenUiState.Success
        assertEquals(8, state.hour)
        assertEquals(0, state.minute)
        assertEquals(false, state.enabled)
        assertEquals(50, state.volume)
        assertEquals(emptySet<DayOfWeekUi>(), state.enabledDays)
        assertEquals(false, state.progressiveVolume)
        assertEquals(NTS_STREAM_URL, state.streamUrl)
        assertEquals("Alarm is disabled", state.scheduledInText)

        job.cancel()
    }

    @Test
    fun onTimeChange_updatesRepository() = runTest {
        val viewModel = createViewModel()
        val job = backgroundScope.launch { viewModel.uiState.collect() }

        advanceUntilIdle()
        clearMocks(repository, alarmScheduler)

        viewModel.onTimeChange(9, 30)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.setTime(9, 30) }
        job.cancel()
    }

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

    @Test
    fun onEnabledChange_doesNothing_whileLoading() = runTest {
        val viewModel = createViewModel()

        viewModel.onEnabledChange()
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.setEnabled(any()) }
    }

    @Test
    fun onToggleDay_addsDay_ifNotPresent() = runTest {
        val viewModel = createViewModel()
        val job = backgroundScope.launch { viewModel.uiState.collect() }

        advanceUntilIdle()
        clearMocks(repository, alarmScheduler)

        viewModel.onToggleDay(DayOfWeekUi.MO)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.setEnabledDays(setOf(DayOfWeekUi.MO)) }
        job.cancel()
    }

    @Test
    fun onToggleDay_removesDay_ifPresent() = runTest {
        settingsFlow.value = settingsFlow.value.copy(enabledDays = setOf(DayOfWeekUi.MO))

        val viewModel = createViewModel()
        val job = backgroundScope.launch { viewModel.uiState.collect() }

        advanceUntilIdle()
        clearMocks(repository, alarmScheduler)

        viewModel.onToggleDay(DayOfWeekUi.MO)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.setEnabledDays(emptySet()) }
        job.cancel()
    }

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

    @Test
    fun enabledScheduleConfig_schedulesNextAlarm() = runTest {
        settingsFlow.value = settingsFlow.value.copy(
            enabled = true,
            hour = 7,
            minute = 15,
            enabledDays = setOf(DayOfWeekUi.MO, DayOfWeekUi.FR)
        )

        val viewModel = createViewModel()
        val job = backgroundScope.launch { viewModel.uiState.collect() }

        advanceUntilIdle()

        coVerify(atLeast = 1) {
            alarmScheduler.scheduleNextAlarm(
                hour = 7,
                minute = 15,
                enabledDays = setOf(DayOfWeekUi.MO, DayOfWeekUi.FR)
            )
        }

        job.cancel()
    }

    @Test
    fun disabledScheduleConfig_cancelsAlarm() = runTest {
        val viewModel = createViewModel()
        val job = backgroundScope.launch { viewModel.uiState.collect() }

        advanceUntilIdle()

        coVerify(atLeast = 1) { alarmScheduler.cancelAlarm() }
        job.cancel()
    }

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

    @Test
    fun changingVolume_doesNotRescheduleAlarm() = runTest {
        settingsFlow.value = settingsFlow.value.copy(enabled = true)

        val viewModel = createViewModel()
        val job = backgroundScope.launch { viewModel.uiState.collect() }

        advanceUntilIdle()
        clearMocks(repository, alarmScheduler)

        settingsFlow.value = settingsFlow.value.copy(volume = 80)
        advanceUntilIdle()

        confirmVerified(alarmScheduler)
        job.cancel()
    }

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

    @Test
    fun changingEnabledDays_reschedulesAlarm_whenEnabled() = runTest {
        settingsFlow.value = settingsFlow.value.copy(enabled = true)

        val viewModel = createViewModel()
        val job = backgroundScope.launch { viewModel.uiState.collect() }

        advanceUntilIdle()
        clearMocks(repository, alarmScheduler)

        settingsFlow.value = settingsFlow.value.copy(enabledDays = setOf(DayOfWeekUi.TU))
        advanceUntilIdle()

        coVerify(exactly = 1) {
            alarmScheduler.scheduleNextAlarm(
                hour = 8,
                minute = 0,
                enabledDays = setOf(DayOfWeekUi.TU)
            )
        }

        job.cancel()
    }

    private fun createViewModel(): HomeScreenViewModel {
        return HomeScreenViewModel(
            repository = repository,
            alarmScheduler = alarmScheduler
        )
    }
}
