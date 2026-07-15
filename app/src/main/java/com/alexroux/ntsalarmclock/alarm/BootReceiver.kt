package com.alexroux.ntsalarmclock.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.alexroux.ntsalarmclock.data.AlarmSettingsRepository
import com.alexroux.ntsalarmclock.data.DataStoreAlarmSettingsRepository
import com.alexroux.ntsalarmclock.data.alarmSettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Receiver triggered after device reboot.
 *
 * Android clears all alarms scheduled with AlarmManager when the device restarts,
 * so this receiver restores the next alarm from persisted settings.
 */
open class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: run {
            Log.w(TAG, "Ignoring boot receiver call with null action")
            return
        }

        if (action != Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Ignoring unsupported broadcast action=$action")
            return
        }

        Log.d(TAG, "BOOT_COMPLETED received, restoring alarm schedule")

        val pendingResult = createPendingResult()

        createScope().launch {
            try {
                restoreAlarmSchedule(context)
            } catch (exception: Exception) {
                Log.e(TAG, "Failed to restore alarm after reboot", exception)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun restoreAlarmSchedule(context: Context) {
        val repository = createRepository(context)
        val scheduler = createScheduler(context)
        val settings = repository.settings.first()

        Log.d(
            TAG,
            "Restored alarm: enabled=${settings.enabled}, " +
                    "time=${settings.hour}:${settings.minute}, days=${settings.enabledDays}"
        )

        if (settings.enabled) {
            scheduler.scheduleNextAlarm(
                hour = settings.hour,
                minute = settings.minute,
                enabledDays = settings.enabledDays
            )
            Log.d(TAG, "Alarm rescheduled after reboot")
        } else {
            scheduler.cancelAlarm()
            Log.d(TAG, "Alarm is disabled; no schedule was restored")
        }
    }

    // These factory methods keep Android dependencies replaceable in unit tests.
    protected open fun createRepository(context: Context): AlarmSettingsRepository =
        DataStoreAlarmSettingsRepository(context.alarmSettingsDataStore)

    protected open fun createScheduler(context: Context): AlarmScheduler = AlarmScheduler(context)

    protected open fun createScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    protected open fun createPendingResult(): PendingResult = goAsync()

    companion object {
        private const val TAG = "BootReceiver"
    }
}
