package com.alexroux.ntsalarmclock.ui.screens.home

import android.app.Application
import android.util.Log
import com.alexroux.ntsalarmclock.alarm.AlarmScheduler
import com.alexroux.ntsalarmclock.data.AlarmSettings
import com.alexroux.ntsalarmclock.data.AlarmSettingsRepository
import com.alexroux.ntsalarmclock.ui.components.DayOfWeekUi
import io.mockk.coVerify
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
    private val application = mockk<Application>(relaxed = true)

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
    fun `initial state is Loading`() = runTest {
        val viewModel = createViewModel()
        assertTrue(viewModel.uiState.value is HomeScreenUiState.Loading)
    }

    @Test
    fun `state becomes Success after repository emits`() = runTest {
        val viewModel = createViewModel()

        // Start collecting to trigger SharingStarted.WhileSubscribed
        val job = backgroundScope.launch { viewModel.uiState.collect() }

        advanceUntilIdle()

        val state = viewModel.uiState.value as HomeScreenUiState.Success
        assertEquals(8, state.hour)
        assertEquals(0, state.minute)
        assertEquals(false, state.enabled)

        job.cancel()
    }

    @Test
    fun `onTimeChange updates repository`() = runTest {
        val viewModel = createViewModel()
        val job = backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        viewModel.onTimeChange(9, 30)
        advanceUntilIdle()

        coVerify { repository.setTime(9, 30) }
        job.cancel()
    }

    @Test
    fun `onEnabledChange toggles enabled state`() = runTest {
        val viewModel = createViewModel()
        val job = backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        viewModel.onEnabledChange()
        advanceUntilIdle()

        coVerify { repository.setEnabled(true) }
        job.cancel()
    }

    @Test
    fun `onToggleDay adds day if not present`() = runTest {
        val viewModel = createViewModel()
        val job = backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        viewModel.onToggleDay(DayOfWeekUi.MO)
        advanceUntilIdle()

        coVerify { repository.setEnabledDays(setOf(DayOfWeekUi.MO)) }
        job.cancel()
    }

    @Test
    fun `onToggleDay removes day if present`() = runTest {
        settingsFlow.value = settingsFlow.value.copy(enabledDays = setOf(DayOfWeekUi.MO))
        val viewModel = createViewModel()
        val job = backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        viewModel.onToggleDay(DayOfWeekUi.MO)
        advanceUntilIdle()

        coVerify { repository.setEnabledDays(emptySet()) }
        job.cancel()
    }

    @Test
    fun `onVolumeChange clamps volume`() = runTest {
        val viewModel = createViewModel()
        val job = backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        viewModel.onVolumeChange(150)
        advanceUntilIdle()
        coVerify { repository.setVolume(100) }

        viewModel.onVolumeChange(-10)
        advanceUntilIdle()
        coVerify { repository.setVolume(0) }
        job.cancel()
    }

    private fun createViewModel(): HomeScreenViewModel {
        return HomeScreenViewModel(
            application = application,
            repository = repository,
            alarmScheduler = alarmScheduler
        )
    }
}