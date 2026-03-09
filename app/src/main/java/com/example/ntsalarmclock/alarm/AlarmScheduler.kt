package com.example.ntsalarmclock.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.ntsalarmclock.RingingActivity
import com.example.ntsalarmclock.data.AlarmSettings
import com.example.ntsalarmclock.ui.components.DayOfWeekUi
import java.util.Calendar
import java.util.Locale

/**
 * Responsible for scheduling and cancelling alarms using AlarmManager.
 *
 * This class computes the next alarm time based on the user's settings
 * and schedules it with AlarmManager.
 */
class AlarmScheduler(private val context: Context) {

    private val TAG = "AlarmScheduler"

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    /**
     * Schedules the next alarm based on the current settings.
     */
    fun scheduleNextFromSettings(settings: AlarmSettings) {
        Log.d(
            TAG,
            "scheduleNextFromSettings: enabled=${settings.enabled}, time=${settings.hour}:${settings.minute}, days=${settings.enabledDays}"
        )

        // If the alarm is disabled, cancel any existing scheduled alarm
        if (!settings.enabled) {
            cancel()
            return
        }

        val triggerAtMillis = computeNextTriggerMillis(
            hour = settings.hour,
            minute = settings.minute,
            enabledDays = settings.enabledDays
        )

        logNextAlarm(triggerAtMillis)

        cancel()
        scheduleAt(triggerAtMillis)
    }

    /**
     * Cancels any scheduled alarm.
     */
    fun cancel() {
        Log.d(TAG, "cancel")
        alarmManager.cancel(alarmPendingIntent())
    }

    /**
     * Schedules an alarm at the given timestamp.
     */
    private fun scheduleAt(triggerAtMillis: Long) {
        val pendingIntent = alarmPendingIntent()

        // On Android 12+, exact alarms may be restricted by user or system settings.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            Log.w(TAG, "Exact alarms are not allowed, falling back to inexact scheduling")
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
            return
        }

        try {
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
            // Final fallback in case exact scheduling is rejected.
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )

            Log.w(TAG, "SecurityException while scheduling exact alarm, used set() fallback")
        }
    }

    /**
     * Creates the PendingIntent that will trigger AlarmReceiver.
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
     * Computes the next alarm timestamp based on hour, minute and enabled days.
     */
    fun computeNextTriggerMillis(
        hour: Int,
        minute: Int,
        enabledDays: Set<DayOfWeekUi>
    ): Long {
        val now = Calendar.getInstance()

        // No selected day means a one shot alarm.
        if (enabledDays.isEmpty()) {
            val candidate = Calendar.getInstance().apply {
                timeInMillis = now.timeInMillis
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
            }

            // If today's time has already passed, schedule for tomorrow.
            if (candidate.timeInMillis <= now.timeInMillis) {
                candidate.add(Calendar.DAY_OF_YEAR, 1)
            }

            return candidate.timeInMillis
        }

        val effectiveDays = enabledDays.map { it.toCalendarDayOfWeek() }.toSet()

        // Look up to 7 days ahead to find the next valid recurring alarm time.
        for (offset in 0..6) {
            val candidate = Calendar.getInstance().apply {
                timeInMillis = now.timeInMillis
                add(Calendar.DAY_OF_YEAR, offset)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
            }

            val candidateDay = candidate.get(Calendar.DAY_OF_WEEK)

            if (!effectiveDays.contains(candidateDay)) continue

            if (candidate.timeInMillis > now.timeInMillis) {
                return candidate.timeInMillis
            }
        }

        // Safety fallback. This should rarely be reached for recurring alarms.
        val fallback = Calendar.getInstance().apply {
            timeInMillis = now.timeInMillis
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
        }

        return fallback.timeInMillis
    }

    /**
     * Logs the next scheduled alarm in a readable format.
     */
    private fun logNextAlarm(triggerAtMillis: Long) {
        val calendar = Calendar.getInstance().apply { timeInMillis = triggerAtMillis }

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

    private fun twoDigits(value: Int): String = String.format(Locale.US, "%02d", value)

    /**
     * Converts the UI enum to Calendar day constants.
     */
    private fun DayOfWeekUi.toCalendarDayOfWeek(): Int {
        return when (this) {
            DayOfWeekUi.MO -> Calendar.MONDAY
            DayOfWeekUi.TU -> Calendar.TUESDAY
            DayOfWeekUi.WE -> Calendar.WEDNESDAY
            DayOfWeekUi.TH -> Calendar.THURSDAY
            DayOfWeekUi.FR -> Calendar.FRIDAY
            DayOfWeekUi.SA -> Calendar.SATURDAY
            DayOfWeekUi.SU -> Calendar.SUNDAY
        }
    }

    private fun pendingIntentFlags(): Int {
        return PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    }

    companion object {
        private const val REQUEST_CODE_ALARM = 1001
        private const val REQUEST_CODE_SHOW_ALARM = 1002
    }
}