package com.alexroux.ntsalarmclock.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.os.PowerManager
import android.util.Log
import com.alexroux.ntsalarmclock.data.AlarmSettings
import com.alexroux.ntsalarmclock.data.AlarmSettingsRepository
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
import kotlinx.coroutines.flow.flow
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
import java.time.DayOfWeek

/**
 * Tests AlarmReceiver without starting real Android services.
 *
 * AlarmReceiver creates Android-specific collaborators internally, so each test
 * uses an anonymous subclass to inject mocks for repository, scheduler,
 * wake lock, and goAsync() pending result. `advanceUntilIdle()` drains the
 * coroutine launched from onReceive() so assertions are deterministic.
 */
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
        // onReceive() switches to Dispatchers.Main before starting playback.
        // The test dispatcher makes that main-thread work controllable in JVM tests.
        Dispatchers.setMain(mainDispatcher)

        // Mock Log because android.util.Log is a framework stub in local unit tests.
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
                enabledDays = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
                progressiveVolume = true
            )
        )
        every { pendingResult.finish() } just runs
        every { wakeLock.isHeld } returns true
        every { wakeLock.acquire(any<Long>()) } just runs
        every { wakeLock.release() } just runs

        var playbackStarted = false

        val receiver = object : AlarmReceiver() {
            override fun createRepository(context: Context): AlarmSettingsRepository = repository

            override fun createScheduler(context: Context): AlarmScheduler = scheduler

            override fun createScope(): CoroutineScope = scope

            override fun createPendingResult(): PendingResult = pendingResult

            override fun createWakeLock(context: Context): PowerManager.WakeLock = wakeLock

            override fun areNotificationsAllowed(context: Context): Boolean = true

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
                enabledDays = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)
            )
        }
        coVerify(exactly = 0) { repository.setEnabled(any()) }
        verify(exactly = 1) { wakeLock.acquire(any<Long>()) }
        verify(exactly = 1) { wakeLock.release() }
        verify(exactly = 1) { pendingResult.finish() }
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

        // Empty enabledDays represents a one-shot alarm; once it fires, the
        // repository should be updated so the UI no longer shows it enabled.
        val receiver = object : AlarmReceiver() {
            override fun createRepository(context: Context): AlarmSettingsRepository = repository

            override fun createScheduler(context: Context): AlarmScheduler = scheduler

            override fun createScope(): CoroutineScope = scope

            override fun createPendingResult(): PendingResult = pendingResult

            override fun createWakeLock(context: Context): PowerManager.WakeLock = wakeLock

            override fun areNotificationsAllowed(context: Context): Boolean = true

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
                enabledDays = setOf(DayOfWeek.TUESDAY),
                progressiveVolume = false
            )
        )
        every { pendingResult.finish() } just runs
        every { wakeLock.isHeld } returns true
        every { wakeLock.acquire(any<Long>()) } just runs
        every { wakeLock.release() } just runs

        // A disabled alarm can still receive a stale broadcast, but it should
        // not reschedule or change repository state.
        val receiver = object : AlarmReceiver() {
            override fun createRepository(context: Context): AlarmSettingsRepository = repository

            override fun createScheduler(context: Context): AlarmScheduler = scheduler

            override fun createScope(): CoroutineScope = scope

            override fun createPendingResult(): PendingResult = pendingResult

            override fun createWakeLock(context: Context): PowerManager.WakeLock = wakeLock

            override fun areNotificationsAllowed(context: Context): Boolean = true

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

        var playbackStarted = false

        // Even when playback is blocked by notification permission, one-shot
        // alarms should still be disabled after their stale broadcast is handled.
        val receiver = object : AlarmReceiver() {
            override fun createRepository(context: Context): AlarmSettingsRepository = repository

            override fun createScheduler(context: Context): AlarmScheduler = scheduler

            override fun createScope(): CoroutineScope = scope

            override fun createPendingResult(): PendingResult = pendingResult

            override fun createWakeLock(context: Context): PowerManager.WakeLock = wakeLock

            override fun areNotificationsAllowed(context: Context): Boolean = false

            override fun startPlaybackService(context: Context) {
                playbackStarted = true
            }
        }

        receiver.onReceive(context, null)
        advanceUntilIdle()

        verify(exactly = 0) { scheduler.scheduleNextAlarm(any(), any(), any()) }
        coVerify(exactly = 1) { repository.setEnabled(false) }
        verify(exactly = 1) { pendingResult.finish() }
        assert(!playbackStarted)
    }

    @Test
    fun notificationsDenied_skipsPlayback_butReschedulesRecurringAlarm() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)

        every { repository.settings } returns flowOf(
            AlarmSettings(
                enabled = true,
                hour = 6,
                minute = 45,
                volume = 80,
                enabledDays = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY),
                progressiveVolume = true
            )
        )
        every { pendingResult.finish() } just runs
        every { wakeLock.isHeld } returns true
        every { wakeLock.acquire(any<Long>()) } just runs
        every { wakeLock.release() } just runs

        var playbackStarted = false

        // Override factory methods so receiver logic runs while all Android
        // side effects are replaced by mocks we can verify.
        val receiver = object : AlarmReceiver() {
            override fun createRepository(context: Context): AlarmSettingsRepository = repository

            override fun createScheduler(context: Context): AlarmScheduler = scheduler

            override fun createScope(): CoroutineScope = scope

            override fun createPendingResult(): PendingResult = pendingResult

            override fun createWakeLock(context: Context): PowerManager.WakeLock = wakeLock

            override fun areNotificationsAllowed(context: Context): Boolean = false

            override fun startPlaybackService(context: Context) {
                playbackStarted = true
            }
        }

        receiver.onReceive(context, null)
        advanceUntilIdle()

        // Recurring alarms should schedule the next occurrence and keep
        // the alarm enabled.
        verify(exactly = 1) {
            scheduler.scheduleNextAlarm(
                hour = 6,
                minute = 45,
                enabledDays = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY)
            )
        }
        coVerify(exactly = 0) { repository.setEnabled(any()) }
        verify(exactly = 1) { pendingResult.finish() }
        verify(exactly = 1) { wakeLock.release() }
        assert(!playbackStarted)
    }

    @Test
    fun repositoryFailure_releasesWakeLockAndFinishesPendingResult() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)

        // Simulates a DataStore read failure after the receiver has already
        // acquired resources for asynchronous broadcast handling.
        every { repository.settings } returns flow {
            throw IllegalStateException("DataStore read failed")
        }
        every { pendingResult.finish() } just runs
        every { wakeLock.isHeld } returns true
        every { wakeLock.acquire(any<Long>()) } just runs
        every { wakeLock.release() } just runs

        var playbackStarted = false

        val receiver = object : AlarmReceiver() {
            override fun createRepository(context: Context): AlarmSettingsRepository = repository

            override fun createScheduler(context: Context): AlarmScheduler = scheduler

            override fun createScope(): CoroutineScope = scope

            override fun createPendingResult(): PendingResult = pendingResult

            override fun createWakeLock(context: Context): PowerManager.WakeLock = wakeLock

            override fun areNotificationsAllowed(context: Context): Boolean = true

            override fun startPlaybackService(context: Context) {
                playbackStarted = true
            }
        }

        receiver.onReceive(context, null)
        advanceUntilIdle()

        // Cleanup must happen on failure to avoid a leaked wake lock or an
        // unfinished goAsync() pending result.
        verify(exactly = 0) { scheduler.scheduleNextAlarm(any(), any(), any()) }
        coVerify(exactly = 0) { repository.setEnabled(any()) }
        verify(exactly = 1) { wakeLock.release() }
        verify(exactly = 1) { pendingResult.finish() }
        assert(!playbackStarted)
    }
}
