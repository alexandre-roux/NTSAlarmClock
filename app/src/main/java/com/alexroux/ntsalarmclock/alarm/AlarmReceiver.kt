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
 */
open class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        Log.d(TAG, "onReceive: action=${intent?.action ?: "null"}")

        val pendingResult = createPendingResult()
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
        withContext(Dispatchers.Main) {
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
        if (wakeLock.isHeld) {
            wakeLock.release()
            Log.d(TAG, "Wake lock released")
        }

        pendingResult.finish()
        Log.d(TAG, "PendingResult finished")
    }

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

    private fun hasPostNotificationsPermission(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
    }

    protected open fun createRepository(context: Context): AlarmSettingsRepository =
        DataStoreAlarmSettingsRepository(context.alarmSettingsDataStore)

    protected open fun createScheduler(context: Context): AlarmScheduler = AlarmScheduler(context)

    protected open fun createScope(): CoroutineScope = CoroutineScope(Dispatchers.IO)

    protected open fun createPendingResult(): PendingResult = goAsync()

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
