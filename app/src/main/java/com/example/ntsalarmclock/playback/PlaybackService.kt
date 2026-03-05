package com.example.ntsalarmclock.playback

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.example.ntsalarmclock.R
import com.example.ntsalarmclock.RingingActivity
import com.example.ntsalarmclock.alarm.AlarmNotification

class PlaybackService : Service() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannelIfNeeded()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_ALARM -> startAlarm()
            ACTION_STOP_ALARM -> stopAlarm()
            else -> Unit
        }
        return START_NOT_STICKY
    }

    private fun startAlarm() {
        Log.d(TAG, "startAlarm")

        val notification = buildForegroundAlarmNotificationOrNull()
        if (notification == null) {
            Log.e(TAG, "Notification build failed, stopping service")
            stopSelf()
            return
        }

        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else {
            0
        }

        try {
            ServiceCompat.startForeground(
                this,
                AlarmNotification.NOTIFICATION_ID,
                notification,
                fgsType
            )
        } catch (t: Throwable) {
            Log.e(TAG, "startForeground failed", t)
            stopSelf()
            return
        }

        // TODO Start Media3 playback here
    }

    private fun stopAlarm() {
        Log.d(TAG, "stopAlarm")

        // TODO Stop Media3 playback here
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        stopSelf()
    }

    private fun buildForegroundAlarmNotificationOrNull(): Notification? {
        return runCatching { buildForegroundAlarmNotification() }
            .onFailure { Log.e(TAG, "buildForegroundAlarmNotification failed", it) }
            .getOrNull()
    }

    private fun buildForegroundAlarmNotification(): Notification {
        val notificationsEnabled = NotificationManagerCompat.from(this).areNotificationsEnabled()

        val hasPostNotificationsPermission =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }

        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channelImportance =
            manager.getNotificationChannel(AlarmNotification.CHANNEL_ID)?.importance

        Log.d(
            TAG,
            "notifEnabled=$notificationsEnabled, postPerm=$hasPostNotificationsPermission, channelImportance=$channelImportance"
        )

        val activityIntent = Intent(this, RingingActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            AlarmNotification.REQUEST_CODE_FULLSCREEN,
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, PlaybackService::class.java).apply {
            action = ACTION_STOP_ALARM
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            AlarmNotification.REQUEST_CODE_STOP,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, AlarmNotification.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Alarm ringing")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(fullScreenPendingIntent)
            // Key change: always set full screen intent
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(R.drawable.ic_launcher_foreground, "Stop", stopPendingIntent)
            .build()
    }

    private fun createNotificationChannelIfNeeded() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

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
    }

    companion object {
        private const val TAG = "PlaybackService"

        const val ACTION_START_ALARM = "com.example.ntsalarmclock.playback.action.START_ALARM"
        const val ACTION_STOP_ALARM = "com.example.ntsalarmclock.playback.action.STOP_ALARM"
    }
}