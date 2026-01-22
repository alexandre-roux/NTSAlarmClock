package com.example.ntsalarmclock.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.util.Calendar

class AlarmScheduler(private val context: Context) {
    val TAG = "AlarmScheduler"

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun scheduleNext(hour: Int, minute: Int) {
        Log.d(TAG, "scheduleNext: $hour:$minute")
        val triggerAtMillis = computeNextTriggerMillis(hour, minute)

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_ALARM,
            Intent(context, AlarmReceiver::class.java),
            pendingIntentFlags()
        )

        // MVP: exact alarm when allowed. Fallback to inexact alarm otherwise.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                // Fallback: still schedule something (inexact) so the app keeps working.
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
                return
            }
        }

        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        } catch (_: SecurityException) {
            // Fallback if the OS blocks exact alarms (Android 12+).
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
    }

    fun cancel() {
        Log.d(TAG, "cancel")
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_ALARM,
            Intent(context, AlarmReceiver::class.java),
            pendingIntentFlags()
        )
        alarmManager.cancel(pendingIntent)
    }

    private fun computeNextTriggerMillis(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val next = Calendar.getInstance().apply {
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
        }

        if (next.timeInMillis <= now.timeInMillis) {
            next.add(Calendar.DAY_OF_YEAR, 1)
        }
        return next.timeInMillis
    }

    private fun pendingIntentFlags(): Int {
        val mutableOrImmutable = PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.FLAG_UPDATE_CURRENT or mutableOrImmutable
    }

    companion object {
        private const val REQUEST_CODE_ALARM = 1001
    }
}
