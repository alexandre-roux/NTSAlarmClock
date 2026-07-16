package com.alexroux.ntsalarmclock.alarm

import android.Manifest
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.alexroux.ntsalarmclock.data.AlarmSettings
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
 *
 * This receiver only coordinates the alarm event. [PlaybackService] owns audio playback and
 * the foreground notification, and that service launches `RingingActivity` to show the alarm UI.
 */
open class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        Log.d(TAG, "onReceive: action=${intent?.action ?: "null"}")

        // BroadcastReceiver.onReceive returns immediately, so goAsync keeps this broadcast alive
        // until the coroutine finishes reading settings and updating the next alarm.
        val pendingResult = createPendingResult()

        // A partial wake lock keeps the CPU running while the device is asleep. The timeout is a
        // final safeguard in case coroutine cleanup cannot release it normally.
        val wakeLock = createWakeLock(context)

        wakeLock.acquire(WAKE_LOCK_TIMEOUT_MS)
        Log.d(TAG, "Wake lock acquired for ${WAKE_LOCK_TIMEOUT_MS}ms")

        createScope().launch {
            try {
                handleAlarm(context)
            } catch (throwable: Throwable) {
                Log.e(TAG, "AlarmReceiver failed", throwable)
            } finally {
                finishReceiverWork(wakeLock, pendingResult)
            }
        }
    }

    private suspend fun handleAlarm(context: Context) {
        // Read one settings snapshot so all work for this broadcast uses a consistent alarm state.
        val repository = createRepository(context)
        val settings = repository.settings.first()

        Log.d(
            TAG,
            "Alarm fired with settings: enabled=${settings.enabled}, " +
                    "time=${settings.hour}:${settings.minute}, " +
                    "days=${settings.enabledDays}, " +
                    "progressiveVolume=${settings.progressiveVolume}"
        )

        startPlaybackIfAllowed(context)
        updateScheduleAfterAlarm(context, settings, repository)
    }

    private suspend fun startPlaybackIfAllowed(context: Context) {
        // Keep Android component startup on the main dispatcher while repository work stays on IO.
        withContext(Dispatchers.Main) {
            // A foreground service must be able to post its notification. Starting playback without
            // that notification would violate Android's foreground-service requirements.
            if (!areNotificationsAllowed(context)) {
                Log.e(
                    TAG,
                    "Notifications are not allowed, so alarm playback cannot start"
                )
                return@withContext
            }

            Log.d(TAG, "Starting alarm playback service")
            startPlaybackService(context)
        }
    }

    /** Applies the post-fire policy for disabled, one-shot, and recurring alarms. */
    private suspend fun updateScheduleAfterAlarm(
        context: Context,
        settings: AlarmSettings,
        repository: AlarmSettingsRepository
    ) {
        when {
            !settings.enabled -> {
                Log.d(TAG, "Alarm is disabled; no next occurrence will be scheduled")
            }

            settings.enabledDays.isEmpty() -> {
                Log.d(TAG, "One-shot alarm fired; disabling it")
                repository.setEnabled(false)
            }

            else -> {
                Log.d(TAG, "Recurring alarm fired; scheduling its next occurrence")
                createScheduler(context).scheduleNextAlarm(
                    hour = settings.hour,
                    minute = settings.minute,
                    enabledDays = settings.enabledDays
                )
            }
        }
    }

    private fun finishReceiverWork(
        wakeLock: PowerManager.WakeLock,
        pendingResult: PendingResult
    ) {
        // Both resources must be completed even after an exception so Android can suspend the app
        // again and does not consider the broadcast to be running indefinitely.
        if (wakeLock.isHeld) {
            wakeLock.release()
            Log.d(TAG, "Wake lock released")
        }

        pendingResult.finish()
        Log.d(TAG, "PendingResult finished")
    }

    /**
     * Checks every notification gate required by the playback foreground service: the app-level
     * switch, Android 13+ runtime permission, and the alarm channel's user-selected importance.
     */
    protected open fun areNotificationsAllowed(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            Log.e(TAG, "Notifications are disabled at app level")
            return false
        }

        if (!hasPostNotificationsPermission(context)) {
            Log.e(TAG, "POST_NOTIFICATIONS permission is denied")
            return false
        }

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val alarmChannel =
            notificationManager.getNotificationChannel(AlarmNotification.CHANNEL_ID)

        if (alarmChannel?.importance == NotificationManager.IMPORTANCE_NONE) {
            Log.w(TAG, "Alarm notification channel is blocked by the user")
            return false
        }

        Log.d(
            TAG,
            "Notification checks passed: channelImportance=${alarmChannel?.importance ?: "missing"}"
        )
        return true
    }

    // POST_NOTIFICATIONS is a runtime permission only on Android 13 (API 33) and newer.
    private fun hasPostNotificationsPermission(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
    }

    // These overridable factories are test seams for replacing Android and persistence dependencies.
    protected open fun createRepository(context: Context): AlarmSettingsRepository =
        DataStoreAlarmSettingsRepository(context.alarmSettingsDataStore)

    protected open fun createScheduler(context: Context): AlarmScheduler = AlarmScheduler(context)

    protected open fun createScope(): CoroutineScope = CoroutineScope(Dispatchers.IO)

    protected open fun createPendingResult(): PendingResult = goAsync()

    protected open fun createWakeLock(context: Context): PowerManager.WakeLock {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

        // PARTIAL_WAKE_LOCK keeps computation alive without forcing the display to turn on.
        return powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "${context.packageName}:alarm_receiver"
        )
    }

    /**
     * Delegates the long-running alarm work to [PlaybackService]. The service enters the
     * foreground, starts audio, and opens `RingingActivity`; the receiver does none of those itself.
     */
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
