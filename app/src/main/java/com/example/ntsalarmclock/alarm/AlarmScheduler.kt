package com.example.ntsalarmclock.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.ntsalarmclock.data.AlarmSettings
import com.example.ntsalarmclock.ui.components.DayOfWeekUi
import java.util.Calendar
import java.util.Locale

class AlarmScheduler(private val context: Context) {
    private val TAG = "AlarmScheduler"

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun scheduleNextFromSettings(settings: AlarmSettings) {
        Log.d(
            TAG,
            "scheduleNextFromSettings: enabled=${settings.enabled}, time=${settings.hour}:${settings.minute}, days=${settings.enabledDays}"
        )

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

        // This cancel is not strictly required if the PendingIntent is identical,
        // but it makes behavior explicit and more reliable across devices/OEMs.
        cancel()
        scheduleAt(triggerAtMillis)
    }

    fun cancel() {
        Log.d(TAG, "cancel")
        alarmManager.cancel(alarmPendingIntent())
    }

    private fun scheduleAt(triggerAtMillis: Long) {
        val pendingIntent = alarmPendingIntent()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
            return
        }

        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        } catch (_: SecurityException) {
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
    }

    private fun alarmPendingIntent(): PendingIntent {
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_ALARM,
            Intent(context, AlarmReceiver::class.java),
            pendingIntentFlags()
        )
    }

    private fun computeNextTriggerMillis(
        hour: Int,
        minute: Int,
        enabledDays: Set<DayOfWeekUi>
    ): Long {
        val now = Calendar.getInstance()

        val effectiveDays: Set<Int> =
            if (enabledDays.isEmpty()) {
                setOf(
                    Calendar.MONDAY,
                    Calendar.TUESDAY,
                    Calendar.WEDNESDAY,
                    Calendar.THURSDAY,
                    Calendar.FRIDAY,
                    Calendar.SATURDAY,
                    Calendar.SUNDAY
                )
            } else {
                enabledDays.map { it.toCalendarDayOfWeek() }.toSet()
            }

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
    }
}