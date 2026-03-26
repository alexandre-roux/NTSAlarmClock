package com.alexroux.ntsalarmclock.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.os.PowerManager
import android.util.Log
import com.alexroux.ntsalarmclock.data.AlarmSettings
import com.alexroux.ntsalarmclock.data.AlarmSettingsRepository
import com.alexroux.ntsalarmclock.ui.components.DayOfWeekUi
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AlarmReceiverTest {

    private val context = mockk<Context>(relaxed = true)
    private val repository = mockk<AlarmSettingsRepository>(relaxed = true)
    private val scheduler = mockk<AlarmScheduler>(relaxed = true)
    private val pendingResult = mockk<BroadcastReceiver.PendingResult>(relaxed = true)
    private val wakeLock = mockk<PowerManager.WakeLock>(relaxed = true)

    private val mainDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(mainDispatcher)
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun recurringAlarm_reschedulesNextAlarm() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)

        every { repository.settings } returns flowOf(
            AlarmSettings(
                enabled = true,
                hour = 7,
                minute = 30,
                volume = 50,
                enabledDays = setOf(DayOfWeekUi.MO, DayOfWeekUi.FR),
                progressiveVolume = true
            )
        )
        every { pendingResult.finish() } just runs
        every { wakeLock.isHeld } returns true
        every { wakeLock.acquire(any<Long>()) } just runs
        every { wakeLock.release() } just runs

        var notificationShown = false
        var playbackStarted = false

        val receiver = object : AlarmReceiver() {
            override fun createRepository(context: Context): AlarmSettingsRepository = repository

            override fun createScheduler(context: Context): AlarmScheduler = scheduler

            override fun createScope(): CoroutineScope = scope

            override fun createPendingResult(): PendingResult = pendingResult

            override fun createWakeLock(context: Context): PowerManager.WakeLock = wakeLock

            override fun areNotificationsAllowed(context: Context): Boolean = true

            override fun showAlarmNotification(context: Context) {
                notificationShown = true
            }

            override fun startPlaybackService(context: Context) {
                playbackStarted = true
            }
        }

        receiver.onReceive(context, null)
        advanceUntilIdle()

        verify(exactly = 1) {
            scheduler.scheduleNextAlarm(
                hour = 7,
                minute = 30,
                enabledDays = setOf(DayOfWeekUi.MO, DayOfWeekUi.FR)
            )
        }
        coVerify(exactly = 0) { repository.setEnabled(any()) }
        verify(exactly = 1) { wakeLock.acquire(any<Long>()) }
        verify(exactly = 1) { wakeLock.release() }
        verify(exactly = 1) { pendingResult.finish() }
        assert(notificationShown)
        assert(playbackStarted)
    }

    @Test
    fun oneShotAlarm_disablesAlarmAfterItFires() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)

        every { repository.settings } returns flowOf(
            AlarmSettings(
                enabled = true,
                hour = 7,
                minute = 30,
                volume = 50,
                enabledDays = emptySet(),
                progressiveVolume = false
            )
        )
        every { pendingResult.finish() } just runs
        every { wakeLock.isHeld } returns true
        every { wakeLock.acquire(any<Long>()) } just runs
        every { wakeLock.release() } just runs

        val receiver = object : AlarmReceiver() {
            override fun createRepository(context: Context): AlarmSettingsRepository = repository

            override fun createScheduler(context: Context): AlarmScheduler = scheduler

            override fun createScope(): CoroutineScope = scope

            override fun createPendingResult(): PendingResult = pendingResult

            override fun createWakeLock(context: Context): PowerManager.WakeLock = wakeLock

            override fun areNotificationsAllowed(context: Context): Boolean = true

            override fun showAlarmNotification(context: Context) = Unit

            override fun startPlaybackService(context: Context) = Unit
        }

        receiver.onReceive(context, null)
        advanceUntilIdle()

        verify(exactly = 0) { scheduler.scheduleNextAlarm(any(), any(), any()) }
        coVerify(exactly = 1) { repository.setEnabled(false) }
        verify(exactly = 1) { pendingResult.finish() }
    }

    @Test
    fun disabledAlarm_doesNotReschedule() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)

        every { repository.settings } returns flowOf(
            AlarmSettings(
                enabled = false,
                hour = 7,
                minute = 30,
                volume = 50,
                enabledDays = setOf(DayOfWeekUi.TU),
                progressiveVolume = false
            )
        )
        every { pendingResult.finish() } just runs
        every { wakeLock.isHeld } returns true
        every { wakeLock.acquire(any<Long>()) } just runs
        every { wakeLock.release() } just runs

        val receiver = object : AlarmReceiver() {
            override fun createRepository(context: Context): AlarmSettingsRepository = repository

            override fun createScheduler(context: Context): AlarmScheduler = scheduler

            override fun createScope(): CoroutineScope = scope

            override fun createPendingResult(): PendingResult = pendingResult

            override fun createWakeLock(context: Context): PowerManager.WakeLock = wakeLock

            override fun areNotificationsAllowed(context: Context): Boolean = true

            override fun showAlarmNotification(context: Context) = Unit

            override fun startPlaybackService(context: Context) = Unit
        }

        receiver.onReceive(context, null)
        advanceUntilIdle()

        verify(exactly = 0) { scheduler.scheduleNextAlarm(any(), any(), any()) }
        coVerify(exactly = 0) { repository.setEnabled(any()) }
        verify(exactly = 1) { pendingResult.finish() }
    }

    @Test
    fun notificationsDenied_skipsNotificationAndPlayback_andDisablesOneShotAlarm() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)

        every { repository.settings } returns flowOf(
            AlarmSettings(
                enabled = true,
                hour = 7,
                minute = 30,
                volume = 50,
                enabledDays = emptySet(),
                progressiveVolume = false
            )
        )
        every { pendingResult.finish() } just runs
        every { wakeLock.isHeld } returns true
        every { wakeLock.acquire(any<Long>()) } just runs
        every { wakeLock.release() } just runs

        var notificationShown = false
        var playbackStarted = false

        val receiver = object : AlarmReceiver() {
            override fun createRepository(context: Context): AlarmSettingsRepository = repository

            override fun createScheduler(context: Context): AlarmScheduler = scheduler

            override fun createScope(): CoroutineScope = scope

            override fun createPendingResult(): PendingResult = pendingResult

            override fun createWakeLock(context: Context): PowerManager.WakeLock = wakeLock

            override fun areNotificationsAllowed(context: Context): Boolean = false

            override fun showAlarmNotification(context: Context) {
                notificationShown = true
            }

            override fun startPlaybackService(context: Context) {
                playbackStarted = true
            }
        }

        receiver.onReceive(context, null)
        advanceUntilIdle()

        verify(exactly = 0) { scheduler.scheduleNextAlarm(any(), any(), any()) }
        coVerify(exactly = 1) { repository.setEnabled(false) }
        verify(exactly = 1) { pendingResult.finish() }
        assert(!notificationShown)
        assert(!playbackStarted)
    }
}