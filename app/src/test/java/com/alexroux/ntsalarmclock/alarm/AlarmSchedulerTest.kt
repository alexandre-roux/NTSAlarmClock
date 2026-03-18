package com.alexroux.ntsalarmclock.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import com.alexroux.ntsalarmclock.ui.components.DayOfWeekUi
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime

class AlarmSchedulerTest {

    private val context = mockk<Context>(relaxed = true)
    private val alarmManager = mockk<AlarmManager>(relaxed = true)
    private val alarmPendingIntent = mockk<PendingIntent>(relaxed = true)
    private val showPendingIntent = mockk<PendingIntent>(relaxed = true)

    private lateinit var scheduler: AlarmScheduler

    @Before
    fun setup() {
        every { context.getSystemService(AlarmManager::class.java) } returns alarmManager
        every { alarmManager.canScheduleExactAlarms() } returns true
        every { alarmPendingIntent.cancel() } just runs
        every { showPendingIntent.cancel() } just runs

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
        unmockkObject(NextAlarmCalculator)
        unmockkStatic(PendingIntent::class)
        unmockkAll()
    }

    @Test
    fun scheduleNextAlarm_callsCancelThenSchedules() {
        mockkObject(NextAlarmCalculator)

        every {
            NextAlarmCalculator.computeNextTriggerMillis(
                now = any<LocalDateTime>(),
                hour = any<Int>(),
                minute = any<Int>(),
                enabledDays = any<Set<DayOfWeekUi>>()
            )
        } returns 123456L

        scheduler.scheduleNextAlarm(
            hour = 8,
            minute = 0,
            enabledDays = emptySet()
        )

        verify { alarmManager.cancel(alarmPendingIntent) }
        verify {
            alarmManager.setAlarmClock(
                any<AlarmManager.AlarmClockInfo>(),
                alarmPendingIntent
            )
        }
    }

    @Test
    fun scheduleNextAlarm_doesNothingIfTriggerIsNull() {
        mockkObject(NextAlarmCalculator)

        every {
            NextAlarmCalculator.computeNextTriggerMillis(
                now = any<LocalDateTime>(),
                hour = any<Int>(),
                minute = any<Int>(),
                enabledDays = any<Set<DayOfWeekUi>>()
            )
        } returns null

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
    fun cancelAlarm_cancelsPendingIntent() {
        scheduler.cancelAlarm()

        verify { alarmManager.cancel(alarmPendingIntent) }
        verify { alarmPendingIntent.cancel() }
    }
}