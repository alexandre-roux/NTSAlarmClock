package com.alexroux.ntsalarmclock.alarm

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.alexroux.ntsalarmclock.data.AlarmSettingsRepository
import com.alexroux.ntsalarmclock.data.DataStoreAlarmSettingsRepository
import com.alexroux.ntsalarmclock.data.alarmSettingsDataStore
import com.alexroux.ntsalarmclock.playback.PlaybackService
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
 * - Check that notifications are allowed before starting audio playback
 * - Start alarm playback in the foreground service
 * - Re-schedule the next occurrence only for recurring alarms
 * - Disable one-shot alarms after they have fired
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

                withContext(Dispatchers.Main) {
                    /**
                     * Check notification permissions before attempting to start the
                     * foreground service.
                     */
                    if (!areNotificationsAllowed(context)) {
                        Log.e(
                            TAG,
                            "Notifications are not allowed, skipping playback service start. " +
                                    "The alarm fired but the user will not hear it."
                        )
                        return@withContext
                    }

                    startPlaybackService(context)
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

                    /**
                     * A one-shot alarm has no selected recurring days.
                     * Once it has fired, persist it as disabled so the UI state
                     * stays consistent with the actual system schedule.
                     */
                    if (settings.enabled && settings.enabledDays.isEmpty()) {
                        Log.d(TAG, "One-shot alarm consumed, disabling it")
                        repository.setEnabled(false)
                    }
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
     * Returns true if the app is allowed to post notifications.
     */
    protected open fun areNotificationsAllowed(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            if (!granted) return false
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = manager.getNotificationChannel(AlarmNotification.CHANNEL_ID)

        if (channel != null && channel.importance == NotificationManager.IMPORTANCE_NONE) {
            Log.w(TAG, "Alarm notification channel is blocked by the user")
            return false
        }

        return true
    }

    protected open fun createRepository(context: Context): AlarmSettingsRepository {
        return DataStoreAlarmSettingsRepository(context.alarmSettingsDataStore)
    }

    protected open fun createScheduler(context: Context): AlarmScheduler {
        return AlarmScheduler(context)
    }

    protected open fun createScope(): CoroutineScope {
        return CoroutineScope(Dispatchers.IO)
    }

    protected open fun createPendingResult(): PendingResult {
        return goAsync()
    }

    protected open fun createWakeLock(context: Context): PowerManager.WakeLock {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "${context.packageName}:alarm_receiver"
        )
    }

    protected open fun startPlaybackService(context: Context) {
        val intent = Intent(context, PlaybackService::class.java).apply {
            action = PlaybackService.ACTION_START_ALARM
        }
        ContextCompat.startForegroundService(context, intent)
    }

    companion object {
        private const val TAG = "AlarmReceiver"
        private const val WAKE_LOCK_TIMEOUT_MS = 10_000L
    }
}