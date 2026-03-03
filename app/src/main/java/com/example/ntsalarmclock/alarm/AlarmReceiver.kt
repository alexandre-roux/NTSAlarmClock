package com.example.ntsalarmclock.alarm

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.ntsalarmclock.R
import com.example.ntsalarmclock.RingingActivity
import com.example.ntsalarmclock.alarm.AlarmNotification.CHANNEL_ID
import com.example.ntsalarmclock.alarm.AlarmNotification.CHANNEL_NAME
import com.example.ntsalarmclock.alarm.AlarmNotification.NOTIFICATION_ID
import com.example.ntsalarmclock.alarm.AlarmNotification.REQUEST_CODE_FULLSCREEN
import com.example.ntsalarmclock.data.DataStoreAlarmSettingsRepository
import com.example.ntsalarmclock.data.alarmSettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val wakeLock = acquireShortWakeLock(context)

        try {
            ensureNotificationChannel(context)
            postFullScreenAlarmNotification(context)
        } finally {
            wakeLock?.let {
                if (it.isHeld) it.release()
            }
        }

        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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

    private fun postFullScreenAlarmNotification(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!granted) {
                // No notification permission, cannot show fullscreen UI
                return
            }
        }

        val activityIntent = Intent(context, RingingActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        val fullScreenPendingIntent = createFullScreenPendingIntent(context, activityIntent)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText("Alarm ringing")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private fun createFullScreenPendingIntent(context: Context, intent: Intent): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(
            context,
            REQUEST_CODE_FULLSCREEN,
            intent,
            flags
        )
    }

    private fun ensureNotificationChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alarm notifications"
            setSound(null, null)
            enableVibration(false)
        }

        manager.createNotificationChannel(channel)
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
}