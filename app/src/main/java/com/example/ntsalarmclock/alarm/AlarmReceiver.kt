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

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val wakeLock = acquireShortWakeLock(context)

        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        val app = context.applicationContext as NTSAlarmClockApplication

        try {
            ensureAlarmNotificationChannel(context)

            if (canPostNotifications(context)) {
                postAlarmNotification(context)
            } else {
                Log.e(TAG, "Cannot post notifications, alarm UI may not appear on some devices")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "AlarmReceiver failed", t)
        } finally {
            wakeLock?.let { if (it.isHeld) it.release() }
        }

        scope.launch {
            try {
                val settings = app.repository.settings.first()
                app.alarmScheduler.scheduleNextFromSettings(settings)
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to schedule next alarm", t)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun postAlarmNotification(context: Context) {
        val notification = AlarmNotification
            .buildAlarmNotification(context)
            .build()

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        NotificationManagerCompat.from(context).notify(
            AlarmNotification.NOTIFICATION_ID,
            notification
        )
    }

    private fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun ensureAlarmNotificationChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val existing = manager.getNotificationChannel(AlarmNotification.CHANNEL_ID)
        if (existing != null) return

        val channel = NotificationChannel(
            AlarmNotification.CHANNEL_ID,
            AlarmNotification.CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alarm notifications"
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        manager.createNotificationChannel(channel)
        Log.d(TAG, "Notification channel created: ${AlarmNotification.CHANNEL_ID}")
    }

    private fun acquireShortWakeLock(context: Context): PowerManager.WakeLock? {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "NTSAlarmClock:AlarmReceiver"
        )
        return try {
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