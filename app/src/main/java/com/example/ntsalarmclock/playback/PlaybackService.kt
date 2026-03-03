package com.example.ntsalarmclock.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.ntsalarmclock.R

class PlaybackService : Service() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
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
        val notification = buildForegroundNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        }

        // TODO Start your Media3 playback here (NTS stream, progressive volume, etc.)
    }

    private fun stopAlarm() {
        // TODO Stop your Media3 playback here
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {
            // Intentionally ignored
        }
        stopSelf()
    }

    private fun buildForegroundNotification(): android.app.Notification {
        val stopIntent = Intent(this, PlaybackService::class.java).apply {
            action = ACTION_STOP_ALARM
        }
        val stopPendingIntent =
            PendingIntents.serviceImmutable(this, REQUEST_CODE_STOP, stopIntent)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Alarm playing")
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(0, "Stop", stopPendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Alarm playback"
            setSound(null, null)
            enableVibration(false)
        }

        manager.createNotificationChannel(channel)
    }

    private object PendingIntents {
        fun serviceImmutable(context: Context, requestCode: Int, intent: Intent) =
            android.app.PendingIntent.getService(
                context,
                requestCode,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
    }

    companion object {
        const val ACTION_START_ALARM = "com.example.ntsalarmclock.playback.action.START_ALARM"
        const val ACTION_STOP_ALARM = "com.example.ntsalarmclock.playback.action.STOP_ALARM"

        private const val CHANNEL_ID = "alarm_playback_channel"
        private const val CHANNEL_NAME = "Alarm playback"
        private const val NOTIFICATION_ID = 1001

        private const val REQUEST_CODE_STOP = 2002
    }
}