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

    /**
     * Create the repository used to restore persisted settings.
     *
     * Overridable for tests.
     */
    protected open fun createRepository(context: Context): AlarmSettingsRepository {
        return DataStoreAlarmSettingsRepository(context.alarmSettingsDataStore)
    }

    /**
     * Create the scheduler used to restore or cancel alarms.
     *
     * Overridable for tests.
     */
    protected open fun createScheduler(context: Context): AlarmScheduler {
        return AlarmScheduler(context)
    }

    /**
     * Create the coroutine scope used for async receiver work.
     *
     * Overridable for tests.
     */
    protected open fun createScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    /**
     * Wrapper around goAsync() so tests can provide a fake PendingResult
     * without overriding the final BroadcastReceiver.goAsync() method.
     */
    protected open fun createPendingResult(): PendingResult {
        return goAsync()
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return

        if (action != Intent.ACTION_BOOT_COMPLETED) {
            return
        }

        Log.d(TAG, "BOOT_COMPLETED received, restoring alarm schedule")

        val pendingResult = createPendingResult()

        createScope().launch {
            try {
                val repository = createRepository(context)
                val scheduler = createScheduler(context)

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