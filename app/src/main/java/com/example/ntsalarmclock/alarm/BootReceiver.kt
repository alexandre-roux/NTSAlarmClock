package com.example.ntsalarmclock.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.ntsalarmclock.data.DataStoreAlarmSettingsRepository
import com.example.ntsalarmclock.data.alarmSettingsDataStore
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
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return

        if (action != Intent.ACTION_BOOT_COMPLETED) {
            return
        }

        Log.d(TAG, "BOOT_COMPLETED received, restoring alarm schedule")

        val pendingResult = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repository = DataStoreAlarmSettingsRepository(
                    context.alarmSettingsDataStore
                )
                val scheduler = AlarmScheduler(context)

                // Read the latest persisted settings once from DataStore.
                val settings = repository.settings.first()

                if (settings.enabled) {
                    scheduler.scheduleNextAlarm(
                        hour = settings.hour,
                        minute = settings.minute,
                        enabledDays = settings.enabledDays
                    )
                    Log.d(TAG, "Alarm rescheduled after reboot")
                } else {
                    scheduler.cancel()
                    Log.d(TAG, "Alarm disabled, nothing to reschedule after reboot")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restore alarm after reboot", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}