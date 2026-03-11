package com.alexroux.ntsalarmclock.alarm

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import com.alexroux.ntsalarmclock.data.DataStoreAlarmSettingsRepository
import com.alexroux.ntsalarmclock.data.alarmSettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Receives the scheduled alarm broadcast.
 *
 * Responsibilities:
 * - Wake up the app when the alarm fires
 * - Show the alarm notification / UI
 * - Re-schedule the next occurrence only for recurring alarms
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val TAG = "AlarmReceiver"
        Log.d(TAG, "onReceive")

        val pendingResult = goAsync()

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "${context.packageName}:alarm_receiver"
        )

        // Keep the CPU awake for the short amount of work done here.
        wakeLock.acquire(WAKE_LOCK_TIMEOUT_MS)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = DataStoreAlarmSettingsRepository(
                    context.alarmSettingsDataStore
                )

                val settings = repository.settings.first()

                Log.d(
                    TAG,
                    "Alarm fired with settings: enabled=${settings.enabled}, " +
                            "time=${settings.hour}:${settings.minute}, " +
                            "days=${settings.enabledDays}, " +
                            "progressiveVolume=${settings.progressiveVolume}"
                )

                // Show the alarm entry point for the user.
                withContext(Dispatchers.Main) {
                    val notificationManager =
                        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                    val notification =
                        AlarmNotification.buildAlarmNotification(context).build()

                    notificationManager.notify(
                        AlarmNotification.NOTIFICATION_ID,
                        notification
                    )
                }

                /**
                 * Re-schedule only when the alarm is still enabled and is configured
                 * as recurring.
                 *
                 * This avoids the classic bug where a one-shot alarm gets scheduled
                 * again after it has already fired.
                 */
                if (settings.enabled && settings.enabledDays.isNotEmpty()) {
                    Log.d(TAG, "Recurring alarm detected, scheduling the next occurrence")

                    AlarmScheduler(context).scheduleNextAlarm(
                        hour = settings.hour,
                        minute = settings.minute,
                        enabledDays = settings.enabledDays
                    )
                } else {
                    Log.d(
                        TAG,
                        "No re-schedule needed: enabled=${settings.enabled}, days=${settings.enabledDays}"
                    )
                }
            } catch (t: Throwable) {
                Log.e(TAG, "AlarmReceiver failed", t)
            } finally {
                if (wakeLock.isHeld) {
                    wakeLock.release()
                }

                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val WAKE_LOCK_TIMEOUT_MS = 10_000L
    }
}