package com.alexroux.ntsalarmclock.alarm

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import com.alexroux.ntsalarmclock.data.AlarmSettingsRepository
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
open class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        Log.d(TAG, "onReceive")

        val pendingResult = createPendingResult()
        val wakeLock = createWakeLock(context)

        // Keep the CPU awake for the short amount of work done here.
        wakeLock.acquire(WAKE_LOCK_TIMEOUT_MS)

        createScope().launch {
            try {
                val repository = createRepository(context)
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
                    showAlarmNotification(context)
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

                    createScheduler(context).scheduleNextAlarm(
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

    /**
     * Overridable for tests.
     */
    protected open fun createRepository(context: Context): AlarmSettingsRepository {
        return DataStoreAlarmSettingsRepository(context.alarmSettingsDataStore)
    }

    /**
     * Overridable for tests.
     */
    protected open fun createScheduler(context: Context): AlarmScheduler {
        return AlarmScheduler(context)
    }

    /**
     * Overridable for tests.
     */
    protected open fun createScope(): CoroutineScope {
        return CoroutineScope(Dispatchers.IO)
    }

    /**
     * Overridable for tests.
     */
    protected open fun createPendingResult(): PendingResult {
        return goAsync()
    }

    /**
     * Overridable for tests.
     */
    protected open fun createWakeLock(context: Context): PowerManager.WakeLock {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "${context.packageName}:alarm_receiver"
        )
    }

    /**
     * Overridable for tests.
     */
    protected open fun showAlarmNotification(context: Context) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notification =
            AlarmNotification.buildAlarmNotification(context).build()

        notificationManager.notify(
            AlarmNotification.NOTIFICATION_ID,
            notification
        )
    }

    companion object {
        private const val TAG = "AlarmReceiver"
        private const val WAKE_LOCK_TIMEOUT_MS = 10_000L
    }
}