package com.example.ntsalarmclock.alarm

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.ntsalarmclock.NTSAlarmClockApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver triggered by AlarmManager when the alarm fires.
 *
 * Responsibilities:
 * - Acquire a temporary wake lock to keep the CPU awake
 * - Show the alarm notification
 * - Reschedule the next alarm only for recurring alarms
 * - Disable one shot alarms after they have fired
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        // Acquire a short wake lock to prevent the CPU from sleeping
        // while the receiver is still doing work.
        val wakeLock = acquireShortWakeLock(context)

        // goAsync() allows the receiver to continue work asynchronously
        // after onReceive() returns.
        val pendingResult = goAsync()

        // Use an IO scope for repository access and scheduling work.
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        // Access shared app level dependencies.
        val app = context.applicationContext as NTSAlarmClockApplication

        try {
            // Ensure the notification channel exists before posting.
            ensureAlarmNotificationChannel(context)

            // Only post the alarm notification if the app is allowed to do so.
            if (canPostNotifications(context)) {
                postAlarmNotification(context)
            } else {
                Log.e(TAG, "Cannot post notifications, alarm UI may not appear on some devices")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "AlarmReceiver failed", t)
        }

        // Continue the rest of the work asynchronously.
        scope.launch {
            try {
                // Read the latest alarm settings from the repository.
                val settings = app.repository.settings.first()

                if (settings.enabledDays.isEmpty()) {
                    // No selected day means this is a one shot alarm.
                    // Disable it after it fires so it is not scheduled again.
                    Log.d(TAG, "One shot alarm fired, disabling it without rescheduling")
                    app.repository.setEnabled(false)
                    app.alarmScheduler.cancelAlarm()
                } else {
                    // Selected days mean this is a recurring alarm.
                    // Schedule the next valid occurrence.
                    Log.d(TAG, "Recurring alarm fired, scheduling next occurrence")
                    app.alarmScheduler.scheduleNextAlarm(
                        hour = settings.hour,
                        minute = settings.minute,
                        enabledDays = settings.enabledDays
                    )
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to handle post alarm scheduling", t)
            } finally {
                // Release the wake lock only after all async work is complete.
                wakeLock?.let {
                    if (it.isHeld) {
                        it.release()
                    }
                }

                // Tell the system that the receiver finished its async work.
                pendingResult.finish()
            }
        }
    }

    /**
     * Builds and posts the alarm notification.
     */
    private fun postAlarmNotification(context: Context) {
        val notification = AlarmNotification
            .buildAlarmNotification(context)
            .build()

        // On Android 13+, posting notifications requires runtime permission.
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "POST_NOTIFICATIONS permission not granted")
            return
        }

        NotificationManagerCompat.from(context).notify(
            AlarmNotification.NOTIFICATION_ID,
            notification
        )
    }

    /**
     * Returns true if the app can post notifications.
     */
    private fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true

        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Creates the notification channel used for alarm notifications.
     * Required on Android 8+.
     */
    private fun ensureAlarmNotificationChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val existing = manager.getNotificationChannel(AlarmNotification.CHANNEL_ID)

        // Do nothing if the channel already exists.
        if (existing != null) return

        val channel = NotificationChannel(
            AlarmNotification.CHANNEL_ID,
            AlarmNotification.CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alarm notifications"

            // Disable default channel sound and vibration because
            // alarm playback is handled separately by the app.
            setSound(null, null)
            enableVibration(false)

            // Make the notification visible on the lock screen.
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        manager.createNotificationChannel(channel)
        Log.d(TAG, "Notification channel created: ${AlarmNotification.CHANNEL_ID}")
    }

    /**
     * Acquires a short partial wake lock to keep the CPU awake
     * while the receiver completes its work.
     */
    private fun acquireShortWakeLock(context: Context): PowerManager.WakeLock? {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager

        val wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "NTSAlarmClock:AlarmReceiver"
        )

        return try {
            // Hold the wake lock for up to 10 seconds as a safety limit.
            wakeLock.acquire(10_000L)
            wakeLock
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val TAG = "AlarmReceiver"
    }
}