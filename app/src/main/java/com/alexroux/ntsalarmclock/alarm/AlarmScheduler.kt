package com.alexroux.ntsalarmclock.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.alexroux.ntsalarmclock.RingingActivity
import com.alexroux.ntsalarmclock.ui.components.DayOfWeekUi
import java.text.DateFormat
import java.util.Calendar
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

    private val TAG = "AlarmScheduler"

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    /**
     * Schedule the next alarm occurrence.
     */
    fun scheduleNextAlarm(
        hour: Int,
        minute: Int,
        enabledDays: Set<DayOfWeekUi>
    ) {
        Log.d(
            TAG,
            "scheduleNextAlarm: time=$hour:$minute, days=$enabledDays"
        )

        val triggerAtMillis = NextAlarmCalculator.computeNextTriggerMillis(
            hour = hour,
            minute = minute,
            enabledDays = enabledDays
        ) ?: return

        logNextAlarm(triggerAtMillis)

        // Replace any previously scheduled alarm with the new one.
        cancel()
        scheduleAt(triggerAtMillis)

        // Log what the system reports as the next visible alarm clock.
        logSystemNextAlarmClock()
    }

    /**
     * Cancel the currently scheduled alarm, if any.
     */
    fun cancel() {
        Log.d(TAG, "cancel")

        val pendingIntent = alarmPendingIntent()

        alarmManager.cancel(pendingIntent)

        // Explicitly cancel the PendingIntent to avoid stale instances
        // being reused on some devices.
        pendingIntent.cancel()

        logSystemNextAlarmClock()
    }

    /**
     * Alias kept for consistency with HomeScreenViewModel.
     */
    fun cancelAlarm() {
        cancel()
    }

    /**
     * Schedule an alarm at the given timestamp.
     *
     * Scheduling strategy:
     * - Prefer setAlarmClock() for real alarm clock behavior
     * - If exact alarms are restricted, fall back to inexact scheduling
     * - If setAlarmClock() fails, try setExactAndAllowWhileIdle() before
     *   using a final inexact fallback
     */
    private fun scheduleAt(triggerAtMillis: Long) {
        val pendingIntent = alarmPendingIntent()

        // On Android 12+, exact alarms may be restricted by system policy or user settings.
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !alarmManager.canScheduleExactAlarms()
        ) {
            Log.w(TAG, "Exact alarms are not allowed, falling back to inexact scheduling")

            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )

            return
        }

        try {
            // This intent is used by the system for the "next alarm" affordance.
            val showIntent = Intent(context, RingingActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }

            val showPendingIntent = PendingIntent.getActivity(
                context,
                REQUEST_CODE_SHOW_ALARM,
                showIntent,
                pendingIntentFlags()
            )

            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(triggerAtMillis, showPendingIntent),
                pendingIntent
            )

            Log.d(TAG, "Alarm scheduled with setAlarmClock()")
        } catch (_: SecurityException) {
            scheduleExactFallback(triggerAtMillis, pendingIntent)
        }
    }

    /**
     * Try an exact fallback when setAlarmClock() cannot be used.
     *
     * This is useful on some recent Android versions and OEM implementations
     * where exact scheduling is still possible even if setAlarmClock() fails.
     */
    private fun scheduleExactFallback(
        triggerAtMillis: Long,
        pendingIntent: PendingIntent
    ) {
        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )

            Log.w(TAG, "Alarm scheduled with exact fallback")
        } catch (_: SecurityException) {
            // Final fallback if exact scheduling is still rejected.
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )

            Log.w(TAG, "Exact fallback rejected, used inexact set() fallback")
        }
    }

    /**
     * Create the PendingIntent that triggers AlarmReceiver.
     */
    private fun alarmPendingIntent(): PendingIntent {
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_ALARM,
            Intent(context, AlarmReceiver::class.java),
            pendingIntentFlags()
        )
    }

    /**
     * Log the next scheduled alarm in a readable format.
     */
    private fun logNextAlarm(triggerAtMillis: Long) {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = triggerAtMillis
        }

        val dayLabel = dayOfWeekLabel(calendar.get(Calendar.DAY_OF_WEEK))
        val dateLabel =
            "${calendar.get(Calendar.YEAR)}-" +
                    twoDigits(calendar.get(Calendar.MONTH) + 1) + "-" +
                    twoDigits(calendar.get(Calendar.DAY_OF_MONTH)) +
                    " " +
                    twoDigits(calendar.get(Calendar.HOUR_OF_DAY)) + ":" +
                    twoDigits(calendar.get(Calendar.MINUTE))

        Log.d(TAG, "Next alarm scheduled for: $dayLabel, $dateLabel (millis=$triggerAtMillis)")
    }

    /**
     * Log the next alarm clock reported by the Android system.
     *
     * This is very useful when debugging because it confirms what the OS
     * currently exposes as the next upcoming alarm clock.
     */
    fun logSystemNextAlarmClock() {
        val nextAlarmClock = alarmManager.nextAlarmClock

        if (nextAlarmClock == null) {
            Log.d(TAG, "System nextAlarmClock: none")
            return
        }

        val formattedTime = DateFormat.getDateTimeInstance(
            DateFormat.MEDIUM,
            DateFormat.SHORT,
            Locale.getDefault()
        ).format(Date(nextAlarmClock.triggerTime))

        Log.d(
            TAG,
            "System nextAlarmClock: triggerTime=${nextAlarmClock.triggerTime}, formatted=$formattedTime"
        )
    }

    /**
     * Convert a Calendar day constant into a readable English label.
     */
    private fun dayOfWeekLabel(dayOfWeek: Int): String {
        return when (dayOfWeek) {
            Calendar.MONDAY -> "Monday"
            Calendar.TUESDAY -> "Tuesday"
            Calendar.WEDNESDAY -> "Wednesday"
            Calendar.THURSDAY -> "Thursday"
            Calendar.FRIDAY -> "Friday"
            Calendar.SATURDAY -> "Saturday"
            Calendar.SUNDAY -> "Sunday"
            else -> "Unknown"
        }
    }

    /**
     * Format an integer as a two-digit string.
     */
    private fun twoDigits(value: Int): String {
        return String.format(Locale.US, "%02d", value)
    }

    /**
     * Return the flags used for all PendingIntents created by this scheduler.
     */
    private fun pendingIntentFlags(): Int {
        return PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    }

    companion object {
        private const val REQUEST_CODE_ALARM = 1001
        private const val REQUEST_CODE_SHOW_ALARM = 1002
    }
}