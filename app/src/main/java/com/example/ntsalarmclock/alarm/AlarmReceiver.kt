package com.example.ntsalarmclock.alarm

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.ntsalarmclock.R
import com.example.ntsalarmclock.RingingActivity
import com.example.ntsalarmclock.data.DataStoreAlarmSettingsRepository
import com.example.ntsalarmclock.data.alarmSettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    override fun onReceive(context: Context, intent: Intent?) {
        val wakeLock = acquireShortWakeLock(context)

        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        try {
            ensureAlarmNotificationChannel(context)
            showFullScreenAlarmNotification(context)
        } catch (t: Throwable) {
            Log.e(TAG, "AlarmReceiver failed", t)
        } finally {
            wakeLock?.let { if (it.isHeld) it.release() }
        }

        scope.launch {
            try {
                val repository = DataStoreAlarmSettingsRepository(context.alarmSettingsDataStore)
                val settings = repository.settings.first()
                AlarmScheduler(context).scheduleNextFromSettings(settings)
            } catch (_: Exception) {
                // Intentionally ignored
            } finally {
                pendingResult.finish()
            }
        }
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun showFullScreenAlarmNotification(context: Context) {
        val activityIntent = Intent(context, RingingActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            AlarmNotification.REQUEST_CODE_FULLSCREEN,
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, AlarmNotification.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText("Alarm ringing")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(fullScreenPendingIntent)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .build()

        NotificationManagerCompat.from(context).notify(
            AlarmNotification.NOTIFICATION_ID,
            notification
        )
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