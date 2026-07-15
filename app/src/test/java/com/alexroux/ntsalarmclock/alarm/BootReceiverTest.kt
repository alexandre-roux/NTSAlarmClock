package com.alexroux.ntsalarmclock.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.alexroux.ntsalarmclock.data.AlarmSettings
import com.alexroux.ntsalarmclock.data.AlarmSettingsRepository
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
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek

/** Tests restoration of persisted alarm state after a device reboot. */
@OptIn(ExperimentalCoroutinesApi::class)
class BootReceiverTest {

    private val context = mockk<Context>(relaxed = true)
    private val repository = mockk<AlarmSettingsRepository>(relaxed = true)
    private val scheduler = mockk<AlarmScheduler>(relaxed = true)
    private val pendingResult = mockk<BroadcastReceiver.PendingResult>(relaxed = true)
    private val bootIntent = mockk<Intent>()

    @Before
    fun setup() {
        // android.util.Log is a framework stub in local JVM tests.
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0

        every { bootIntent.action } returns Intent.ACTION_BOOT_COMPLETED
        every { pendingResult.finish() } just runs
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun bootCompleted_reschedulesEnabledAlarm() = runTest {
        givenSettings(
            alarmSettings(
                enabled = true,
                hour = 6,
                minute = 15,
                enabledDays = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)
            )
        )
        val receiver = testReceiver(this)

        receiver.onReceive(context, bootIntent)
        advanceUntilIdle()

        verify(exactly = 1) {
            scheduler.scheduleNextAlarm(
                hour = 6,
                minute = 15,
                enabledDays = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)
            )
        }
        verify(exactly = 0) { scheduler.cancelAlarm() }
        verify(exactly = 1) { pendingResult.finish() }
    }

    @Test
    fun bootCompleted_cancelsDisabledAlarmState() = runTest {
        givenSettings(
            alarmSettings(
                enabled = false,
                hour = 8,
                minute = 0,
                enabledDays = emptySet()
            )
        )
        val receiver = testReceiver(this)

        receiver.onReceive(context, bootIntent)
        advanceUntilIdle()

        verify(exactly = 0) { scheduler.scheduleNextAlarm(any(), any(), any()) }
        verify(exactly = 1) { scheduler.cancelAlarm() }
        verify(exactly = 1) { pendingResult.finish() }
    }

    @Test
    fun bootCompleted_finishesPendingResultWhenRepositoryFails() = runTest {
        every { repository.settings } returns flow {
            throw IllegalStateException("DataStore unavailable")
        }
        val receiver = testReceiver(this)

        receiver.onReceive(context, bootIntent)
        advanceUntilIdle()

        verify(exactly = 0) { scheduler.scheduleNextAlarm(any(), any(), any()) }
        verify(exactly = 0) { scheduler.cancelAlarm() }
        verify(exactly = 1) { pendingResult.finish() }
    }

    @Test
    fun nonBootBroadcast_isIgnored() = runTest {
        val timeChangedIntent = mockk<Intent> {
            every { action } returns Intent.ACTION_TIME_CHANGED
        }
        val receiver = testReceiver(this)

        receiver.onReceive(context, timeChangedIntent)
        advanceUntilIdle()

        verify(exactly = 0) { scheduler.scheduleNextAlarm(any(), any(), any()) }
        verify(exactly = 0) { scheduler.cancelAlarm() }
        verify(exactly = 0) { pendingResult.finish() }
    }

    private fun givenSettings(settings: AlarmSettings) {
        every { repository.settings } returns flowOf(settings)
    }

    private fun alarmSettings(
        enabled: Boolean,
        hour: Int,
        minute: Int,
        enabledDays: Set<DayOfWeek>
    ) = AlarmSettings(
        enabled = enabled,
        hour = hour,
        minute = minute,
        volume = 50,
        enabledDays = enabledDays,
        progressiveVolume = false
    )

    private fun testReceiver(scope: CoroutineScope): BootReceiver = object : BootReceiver() {
        override fun createRepository(context: Context): AlarmSettingsRepository = repository

        override fun createScheduler(context: Context): AlarmScheduler = scheduler

        override fun createScope(): CoroutineScope = scope

        override fun createPendingResult(): PendingResult = pendingResult
    }
}
