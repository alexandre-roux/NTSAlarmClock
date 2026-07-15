package com.alexroux.ntsalarmclock.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.alexroux.ntsalarmclock.RingingActivity
import java.text.DateFormat
import java.time.DayOfWeek
import java.util.Date
import java.util.Locale

/**
 * Responsible for scheduling and cancelling alarms using AlarmManager.
 *
 * Behavior:
 * - If no day is selected, the alarm is treated as a one-shot alarm
 * - If one or more days are selected, the alarm is treated as recurring
 */
class AlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    /** Schedules the next one-shot or recurring alarm occurrence. */
    fun scheduleNextAlarm(
        hour: Int,
        minute: Int,
        enabledDays: Set<DayOfWeek>
    ) {
        Log.d(
            TAG,
            "scheduleNextAlarm: time=$hour:$minute, days=$enabledDays"
        )

        val triggerAtMillis = NextAlarmCalculator.computeNextTriggerMillis(
            hour = hour,
            minute = minute,
            enabledDays = enabledDays
        ) ?: run {
            Log.w(TAG, "scheduleNextAlarm: no future trigger could be computed")
            return
        }

        logNextAlarm(triggerAtMillis)

        cancelAlarm()
        scheduleAlarmAt(triggerAtMillis)
        logSystemNextAlarmClock()
    }

    /** Cancels the currently scheduled alarm, if one exists. */
    fun cancelAlarm() {
        Log.d(TAG, "Cancelling scheduled alarm")

        val alarmPendingIntent = createAlarmPendingIntent()
        alarmManager.cancel(alarmPendingIntent)
        // Prevent Android from reusing the token after its alarm is removed.
        alarmPendingIntent.cancel()

        logSystemNextAlarmClock()
    }

    /**
     * Schedules an alarm at the given timestamp.
     *
     * Alarm-clock scheduling is preferred because it gives Android the correct
     * alarm affordances. Restricted exact alarms fall back to inexact scheduling.
     */
    private fun scheduleAlarmAt(triggerAtMillis: Long) {
        val alarmPendingIntent = createAlarmPendingIntent()

        if (!canScheduleExactAlarms()) {
            Log.w(
                TAG,
                "Exact alarms are not allowed, using inexact set() for triggerAtMillis=$triggerAtMillis"
            )
            scheduleInexactAlarm(triggerAtMillis, alarmPendingIntent)
            return
        }

        try {
            Log.d(TAG, "Scheduling alarm with setAlarmClock(): triggerAtMillis=$triggerAtMillis")
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(triggerAtMillis, createShowAlarmPendingIntent()),
                alarmPendingIntent
            )
            Log.d(TAG, "Alarm scheduled with setAlarmClock()")
        } catch (securityException: SecurityException) {
            Log.w(
                TAG,
                "setAlarmClock() rejected: ${securityException.message}. Trying exact fallback."
            )
            scheduleExactFallback(triggerAtMillis, alarmPendingIntent)
        }
    }

    private fun scheduleExactFallback(
        triggerAtMillis: Long,
        alarmPendingIntent: PendingIntent
    ) {
        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                alarmPendingIntent
            )
            Log.w(TAG, "Alarm scheduled with exact fallback: triggerAtMillis=$triggerAtMillis")
        } catch (securityException: SecurityException) {
            scheduleInexactAlarm(triggerAtMillis, alarmPendingIntent)
            Log.w(
                TAG,
                "Exact fallback rejected: ${securityException.message}. Used inexact set() fallback."
            )
        }
    }

    private fun scheduleInexactAlarm(
        triggerAtMillis: Long,
        alarmPendingIntent: PendingIntent
    ) {
        alarmManager.set(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            alarmPendingIntent
        )
    }

    private fun canScheduleExactAlarms(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                alarmManager.canScheduleExactAlarms()
    }

    private fun createAlarmPendingIntent(): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_ALARM,
            Intent(context, AlarmReceiver::class.java),
            PENDING_INTENT_FLAGS
        )

    private fun createShowAlarmPendingIntent(): PendingIntent {
        val intent = Intent(context, RingingActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        return PendingIntent.getActivity(
            context,
            REQUEST_CODE_SHOW_ALARM,
            intent,
            PENDING_INTENT_FLAGS
        )
    }

    private fun logNextAlarm(triggerAtMillis: Long) {
        Log.d(
            TAG,
            "Next alarm: ${formatTriggerTime(triggerAtMillis)} (millis=$triggerAtMillis)"
        )
    }

    private fun logSystemNextAlarmClock() {
        val nextAlarmClock = alarmManager.nextAlarmClock

        if (nextAlarmClock == null) {
            Log.d(TAG, "System nextAlarmClock: none")
            return
        }

        Log.d(
            TAG,
            "System nextAlarmClock: ${formatTriggerTime(nextAlarmClock.triggerTime)} " +
                    "(millis=${nextAlarmClock.triggerTime})"
        )
    }

    private fun formatTriggerTime(triggerAtMillis: Long): String =
        DateFormat.getDateTimeInstance(
            DateFormat.MEDIUM,
            DateFormat.SHORT,
            Locale.getDefault()
        ).format(Date(triggerAtMillis))

    companion object {
        private const val TAG = "AlarmScheduler"
        private const val REQUEST_CODE_ALARM = 1001
        private const val REQUEST_CODE_SHOW_ALARM = 1002
        private const val PENDING_INTENT_FLAGS =
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    }
}
