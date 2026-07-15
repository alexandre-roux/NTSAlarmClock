package com.alexroux.ntsalarmclock.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime

/**
 * JVM tests for AlarmScheduler's interaction with Android AlarmManager.
 *
 * Android framework entry points are mocked so the tests can verify scheduling
 * calls without registering real alarms on a device.
 */
class AlarmSchedulerTest {

    private val context = mockk<Context>(relaxed = true)
    private val alarmManager = mockk<AlarmManager>(relaxed = true)
    private val alarmPendingIntent = mockk<PendingIntent>(relaxed = true)
    private val showPendingIntent = mockk<PendingIntent>(relaxed = true)

    private lateinit var scheduler: AlarmScheduler

    @Before
    fun setup() {
        // AlarmScheduler obtains AlarmManager and PendingIntents internally, so
        // those Android APIs are mocked before the scheduler is constructed.
        every { context.getSystemService(AlarmManager::class.java) } returns alarmManager
        every { alarmManager.canScheduleExactAlarms() } returns true
        every { alarmPendingIntent.cancel() } just runs

        mockkStatic(PendingIntent::class)
        every {
            PendingIntent.getBroadcast(any(), any(), any(), any())
        } returns alarmPendingIntent
        every {
            PendingIntent.getActivity(any(), any(), any(), any())
        } returns showPendingIntent

        scheduler = AlarmScheduler(context)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun scheduleNextAlarm_replacesExistingAlarmBeforeScheduling() {
        givenNextTrigger(TRIGGER_AT_MILLIS)

        scheduler.scheduleNextAlarm(
            hour = 8,
            minute = 0,
            enabledDays = emptySet()
        )

        verifyOrder {
            alarmManager.cancel(alarmPendingIntent)
            alarmManager.setAlarmClock(any<AlarmManager.AlarmClockInfo>(), alarmPendingIntent)
        }
    }

    @Test
    fun scheduleNextAlarm_usesExactFallback_whenAlarmClockThrowsSecurityException() {
        givenNextTrigger(TRIGGER_AT_MILLIS)
        every {
            alarmManager.setAlarmClock(any<AlarmManager.AlarmClockInfo>(), alarmPendingIntent)
        } throws SecurityException("setAlarmClock denied")
        every {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                TRIGGER_AT_MILLIS,
                alarmPendingIntent
            )
        } just runs

        scheduler.scheduleNextAlarm(
            hour = 8,
            minute = 0,
            enabledDays = emptySet()
        )

        // Some OEM/API combinations reject setAlarmClock(); the scheduler should
        // still try the exact while-idle fallback before giving up.
        verify(exactly = 1) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                TRIGGER_AT_MILLIS,
                alarmPendingIntent
            )
        }
        verify(exactly = 0) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, TRIGGER_AT_MILLIS, alarmPendingIntent)
        }
    }

    @Test
    fun scheduleNextAlarm_usesInexactFallback_whenExactFallbackThrowsSecurityException() {
        givenNextTrigger(TRIGGER_AT_MILLIS)
        every {
            alarmManager.setAlarmClock(any<AlarmManager.AlarmClockInfo>(), alarmPendingIntent)
        } throws SecurityException("setAlarmClock denied")
        every {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                TRIGGER_AT_MILLIS,
                alarmPendingIntent
            )
        } throws SecurityException("exact fallback denied")
        every {
            alarmManager.set(AlarmManager.RTC_WAKEUP, TRIGGER_AT_MILLIS, alarmPendingIntent)
        } just runs

        scheduler.scheduleNextAlarm(
            hour = 8,
            minute = 0,
            enabledDays = emptySet()
        )

        // The final fallback is intentionally inexact. It is less precise, but
        // still better than dropping the alarm completely when exact APIs fail.
        verify(exactly = 1) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, TRIGGER_AT_MILLIS, alarmPendingIntent)
        }
    }

    @Test
    fun scheduleNextAlarm_doesNothingIfTriggerIsNull() {
        givenNextTrigger(null)

        scheduler.scheduleNextAlarm(
            hour = 8,
            minute = 0,
            enabledDays = emptySet()
        )

        verify(exactly = 0) {
            alarmManager.setAlarmClock(
                any<AlarmManager.AlarmClockInfo>(),
                any<PendingIntent>()
            )
        }
    }

    @Test
    fun cancelAlarm_cancelsAlarmAndPendingIntent() {
        scheduler.cancelAlarm()

        verify { alarmManager.cancel(alarmPendingIntent) }
        verify { alarmPendingIntent.cancel() }
    }

    private fun givenNextTrigger(triggerAtMillis: Long?) {
        // The calculator is mocked because these tests focus on AlarmManager calls.
        mockkObject(NextAlarmCalculator)
        every {
            NextAlarmCalculator.computeNextTriggerMillis(
                now = any<LocalDateTime>(),
                hour = any<Int>(),
                minute = any<Int>(),
                enabledDays = any<Set<DayOfWeek>>()
            )
        } returns triggerAtMillis
    }

    private companion object {
        const val TRIGGER_AT_MILLIS = 123_456L
    }
}
