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

/** Tests alarm broadcasts without starting Android services or scheduling real alarms. */
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

    /** An enabled recurring alarm should schedule its next occurrence, start playback, and clean up. */
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

    /** A one-shot alarm should disable itself after firing rather than schedule another occurrence. */
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

    /** A stale broadcast for a disabled alarm should make no scheduling or persistence changes. */
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

    /** Without notification permission, a one-shot alarm should skip playback but still disable itself. */
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

    /** A recurring alarm should still be rescheduled when notification permission prevents playback. */
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

    /** A settings read failure should skip alarm work and playback while still releasing receiver resources. */
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
        override fun createRepository(context: Context): AlarmSettingsRepository = repository

        override fun createScheduler(context: Context): AlarmScheduler = scheduler

        override fun createScope(): CoroutineScope = scope

        override fun createPendingResult(): PendingResult = pendingResult

        override fun createWakeLock(context: Context): PowerManager.WakeLock = wakeLock

        override fun areNotificationsAllowed(context: Context): Boolean = notificationsAllowed

        override fun startPlaybackService(context: Context) = onPlaybackStart()
    }

    private fun verifyReceiverCleanup() {
        verify(exactly = 1) { wakeLock.acquire(any<Long>()) }
        verify(exactly = 1) { wakeLock.release() }
        verify(exactly = 1) { pendingResult.finish() }
    }
}
