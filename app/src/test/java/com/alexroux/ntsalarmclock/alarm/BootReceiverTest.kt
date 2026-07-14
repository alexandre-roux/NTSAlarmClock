package com.alexroux.ntsalarmclock.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.alexroux.ntsalarmclock.data.AlarmSettings
import com.alexroux.ntsalarmclock.data.AlarmSettingsRepository
import com.alexroux.ntsalarmclock.ui.components.DayOfWeekUi
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * JVM tests for restoring alarm state after device reboot.
 *
 * BootReceiver uses Android broadcast APIs and DataStore-backed collaborators,
 * so tests inject mocks through the receiver's factory methods and drive the
 * asynchronous work with a TestScope.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BootReceiverTest {

    private val context = mockk<Context>(relaxed = true)
    private val repository = mockk<AlarmSettingsRepository>(relaxed = true)
    private val scheduler = mockk<AlarmScheduler>(relaxed = true)
    private val pendingResult = mockk<BroadcastReceiver.PendingResult>(relaxed = true)
    private val bootIntent = mockk<Intent>()

    @Before
    fun setup() {
        // Mock Log because android.util.Log is a framework stub in local unit tests.
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        every { bootIntent.action } returns Intent.ACTION_BOOT_COMPLETED
        every { pendingResult.finish() } just runs
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun bootCompleted_reschedulesEnabledAlarm() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)

        every { repository.settings } returns flowOf(
            AlarmSettings(
                enabled = true,
                hour = 6,
                minute = 15,
                volume = 70,
                enabledDays = setOf(DayOfWeekUi.MO, DayOfWeekUi.FR),
                progressiveVolume = false
            )
        )

        val receiver = testReceiver(scope)

        receiver.onReceive(context, bootIntent)
        advanceUntilIdle()

        // Android clears AlarmManager entries on reboot; an enabled persisted
        // alarm must be scheduled again with the exact stored schedule.
        verify(exactly = 1) {
            scheduler.scheduleNextAlarm(
                hour = 6,
                minute = 15,
                enabledDays = setOf(DayOfWeekUi.MO, DayOfWeekUi.FR)
            )
        }
        verify(exactly = 0) { scheduler.cancel() }
        verify(exactly = 1) { pendingResult.finish() }
    }

    @Test
    fun bootCompleted_cancelsDisabledAlarmState() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)

        every { repository.settings } returns flowOf(
            AlarmSettings(
                enabled = false,
                hour = 8,
                minute = 0,
                volume = 50,
                enabledDays = emptySet(),
                progressiveVolume = false
            )
        )

        val receiver = testReceiver(scope)

        receiver.onReceive(context, bootIntent)
        advanceUntilIdle()

        // If persisted state says the alarm is disabled, reboot handling should
        // clear any stale schedule instead of creating a new one.
        verify(exactly = 0) { scheduler.scheduleNextAlarm(any(), any(), any()) }
        verify(exactly = 1) { scheduler.cancel() }
        verify(exactly = 1) { pendingResult.finish() }
    }

    @Test
    fun bootCompleted_finishesPendingResultWhenRepositoryFails() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)

        every { repository.settings } returns flow {
            throw IllegalStateException("DataStore unavailable")
        }

        val receiver = testReceiver(scope)

        receiver.onReceive(context, bootIntent)
        advanceUntilIdle()

        // goAsync() must always be completed, even when persisted settings
        // cannot be read during reboot recovery.
        verify(exactly = 0) { scheduler.scheduleNextAlarm(any(), any(), any()) }
        verify(exactly = 0) { scheduler.cancel() }
        verify(exactly = 1) { pendingResult.finish() }
    }

    @Test
    fun nonBootBroadcast_isIgnored() = runTest {
        val otherIntent = mockk<Intent> {
            every { action } returns Intent.ACTION_TIME_CHANGED
        }

        val receiver = testReceiver(TestScope(UnconfinedTestDispatcher(testScheduler)))

        receiver.onReceive(context, otherIntent)
        advanceUntilIdle()

        // The receiver is registered for BOOT_COMPLETED only; unrelated
        // broadcasts should not start async work or touch scheduling.
        verify(exactly = 0) { scheduler.scheduleNextAlarm(any(), any(), any()) }
        verify(exactly = 0) { scheduler.cancel() }
        verify(exactly = 0) { pendingResult.finish() }
    }

    private fun testReceiver(scope: CoroutineScope): BootReceiver {
        return object : BootReceiver() {
            override fun createRepository(context: Context): AlarmSettingsRepository = repository

            override fun createScheduler(context: Context): AlarmScheduler = scheduler

            override fun createScope(): CoroutineScope = scope

            override fun createPendingResult(): PendingResult = pendingResult
        }
    }
}
