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
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek

/**
 * Tests alarm broadcasts without starting Android services or scheduling real alarms.
 *
 * [AlarmReceiver.onReceive] must return quickly, so production calls `goAsync()`, acquires a partial
 * wake lock, and launches coroutine work. The anonymous receiver built by [testReceiver] replaces all
 * of those Android boundaries with mocks and the `runTest` scope. `advanceUntilIdle()` then runs the
 * whole asynchronous path deterministically.
 *
 * The `playbackStarted` Boolean is a lightweight substitute for launching `PlaybackService`. Mock
 * verifications separately cover persistent state and next-alarm scheduling. Cleanup assertions matter
 * as much as business behavior: a leaked wake lock wastes battery, and an unfinished pending result
 * leaves Android believing the broadcast is still active.
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
        Dispatchers.setMain(mainDispatcher)

        // android.util.Log is a framework stub in local JVM tests.
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0

        every { pendingResult.finish() } just runs
        every { wakeLock.isHeld } returns true
        every { wakeLock.acquire(any<Long>()) } just runs
        every { wakeLock.release() } just runs
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(Log::class)
    }

    /**
     * Given an enabled 07:30 alarm with Monday and Friday repeat days, firing the broadcast should start
     * playback now and register the following occurrence with the same schedule. A recurring alarm stays
     * enabled, so `repository.setEnabled` must not be called. [verifyReceiverCleanup] proves the wake lock
     * and asynchronous broadcast are both completed after successful work.
     */
    @Test
    fun recurringAlarm_reschedulesNextAlarm() = runTest {
        givenSettings(
            alarmSettings(
                hour = 7,
                minute = 30,
                enabledDays = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
                progressiveVolume = true
            )
        )
        var playbackStarted = false
        val receiver = testReceiver(this) { playbackStarted = true }

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
        verifyReceiverCleanup()
        assertTrue(playbackStarted)
    }

    /**
     * An empty repeat-day set represents a one-shot alarm. After it fires, scheduling another occurrence
     * would make it repeat daily, so the scheduler must receive no call. Persisting `enabled = false`
     * prevents stale UI state from claiming the alarm is still active; finishing the pending result closes
     * the receiver's asynchronous work.
     */
    @Test
    fun oneShotAlarm_disablesAlarmAfterItFires() = runTest {
        givenSettings(alarmSettings(enabledDays = emptySet()))
        val receiver = testReceiver(this)

        receiver.onReceive(context, null)
        advanceUntilIdle()

        verify(exactly = 0) { scheduler.scheduleNextAlarm(any(), any(), any()) }
        coVerify(exactly = 1) { repository.setEnabled(false) }
        verify(exactly = 1) { pendingResult.finish() }
    }

    /**
     * A broadcast can arrive after the user disabled its alarm because system delivery and app state can
     * race. Given `enabled = false`, the post-fire scheduling policy should neither create a next alarm nor
     * write enabled state again. The test still requires pending-result cleanup after inspecting settings.
     */
    @Test
    fun disabledAlarm_doesNotReschedule() = runTest {
        givenSettings(
            alarmSettings(
                enabled = false,
                enabledDays = setOf(DayOfWeek.TUESDAY)
            )
        )
        val receiver = testReceiver(this)

        receiver.onReceive(context, null)
        advanceUntilIdle()

        verify(exactly = 0) { scheduler.scheduleNextAlarm(any(), any(), any()) }
        coVerify(exactly = 0) { repository.setEnabled(any()) }
        verify(exactly = 1) { pendingResult.finish() }
    }

    /**
     * Android foreground playback requires a visible notification. With notification checks forced to
     * fail, a one-shot alarm must not start the service, but its state transition still has to complete:
     * it is disabled and not rescheduled. This prevents repeated attempts while respecting Android's
     * notification restriction.
     */
    @Test
    fun notificationsDenied_skipsPlaybackAndDisablesOneShotAlarm() = runTest {
        givenSettings(alarmSettings(enabledDays = emptySet()))
        var playbackStarted = false
        val receiver = testReceiver(
            scope = this,
            notificationsAllowed = false,
            onPlaybackStart = { playbackStarted = true }
        )

        receiver.onReceive(context, null)
        advanceUntilIdle()

        verify(exactly = 0) { scheduler.scheduleNextAlarm(any(), any(), any()) }
        coVerify(exactly = 1) { repository.setEnabled(false) }
        verify(exactly = 1) { pendingResult.finish() }
        assertFalse(playbackStarted)
    }

    /**
     * Notification denial affects what the app may do for the current occurrence, not the user's saved
     * recurrence. The service-start flag must remain false, while the exact 06:45 Tuesday/Thursday schedule
     * is registered for next time. The alarm remains enabled, and receiver resources are released normally.
     */
    @Test
    fun notificationsDenied_skipsPlaybackButReschedulesRecurringAlarm() = runTest {
        givenSettings(
            alarmSettings(
                hour = 6,
                minute = 45,
                enabledDays = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY),
                progressiveVolume = true
            )
        )
        var playbackStarted = false
        val receiver = testReceiver(
            scope = this,
            notificationsAllowed = false,
            onPlaybackStart = { playbackStarted = true }
        )

        receiver.onReceive(context, null)
        advanceUntilIdle()

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
        assertFalse(playbackStarted)
    }

    /**
     * The repository flow throws before any [AlarmSettings] snapshot is available. Without trustworthy
     * settings the receiver must not start playback, schedule a recurrence, or change enabled state. The
     * `finally` cleanup path must nevertheless release the wake lock and finish the pending result exactly
     * once, demonstrating exception safety.
     */
    @Test
    fun repositoryFailure_releasesWakeLockAndFinishesPendingResult() = runTest {
        every { repository.settings } returns flow {
            throw IllegalStateException("DataStore read failed")
        }
        var playbackStarted = false
        val receiver = testReceiver(this) { playbackStarted = true }

        receiver.onReceive(context, null)
        advanceUntilIdle()

        verify(exactly = 0) { scheduler.scheduleNextAlarm(any(), any(), any()) }
        coVerify(exactly = 0) { repository.setEnabled(any()) }
        verify(exactly = 1) { wakeLock.release() }
        verify(exactly = 1) { pendingResult.finish() }
        assertFalse(playbackStarted)
    }

    private fun givenSettings(settings: AlarmSettings) {
        // A single flow emission models DataStore returning the settings snapshot at firing time.
        every { repository.settings } returns flowOf(settings)
    }

    private fun alarmSettings(
        enabled: Boolean = true,
        hour: Int = 7,
        minute: Int = 30,
        enabledDays: Set<DayOfWeek>,
        progressiveVolume: Boolean = false
    ) = AlarmSettings(
        enabled = enabled,
        hour = hour,
        minute = minute,
        volume = 50,
        enabledDays = enabledDays,
        progressiveVolume = progressiveVolume
    )

    private fun testReceiver(
        scope: CoroutineScope,
        notificationsAllowed: Boolean = true,
        onPlaybackStart: () -> Unit = {}
    ): AlarmReceiver = object : AlarmReceiver() {
        // AlarmReceiver exposes these creation methods as test seams. Overriding them keeps all effects
        // observable and ensures launched work belongs to runTest instead of a real global IO dispatcher.
        override fun createRepository(context: Context): AlarmSettingsRepository = repository

        override fun createScheduler(context: Context): AlarmScheduler = scheduler

        override fun createScope(): CoroutineScope = scope

        override fun createPendingResult(): PendingResult = pendingResult

        override fun createWakeLock(context: Context): PowerManager.WakeLock = wakeLock

        override fun areNotificationsAllowed(context: Context): Boolean = notificationsAllowed

        override fun startPlaybackService(context: Context) = onPlaybackStart()
    }

    private fun verifyReceiverCleanup() {
        // The normal lifecycle is one acquire when onReceive begins, followed by one release and finish
        // after coroutine work ends. Exact counts also catch accidental double-cleanup.
        verify(exactly = 1) { wakeLock.acquire(any<Long>()) }
        verify(exactly = 1) { wakeLock.release() }
        verify(exactly = 1) { pendingResult.finish() }
    }
}
