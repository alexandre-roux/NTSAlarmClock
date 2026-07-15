package com.alexroux.ntsalarmclock.alarm

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.alexroux.ntsalarmclock.R
import com.alexroux.ntsalarmclock.RingingActivity
import com.alexroux.ntsalarmclock.playback.PlaybackService

object AlarmNotification {
    const val CHANNEL_ID = "alarm_channel_v2"
    const val NOTIFICATION_ID = 1001
    const val REQUEST_CODE_FULLSCREEN = 2001
    const val REQUEST_CODE_STOP = 2002

    fun createNotificationChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notification_channel_description)

            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            // Media3 owns alarm audio; the notification must remain silent.
            enableVibration(false)
            setSound(null, null)
        }

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        notificationManager.createNotificationChannel(channel)
    }

    @SuppressLint("FullScreenIntentPolicy")
    fun buildAlarmNotification(
        context: Context,
        fallbackAudioActive: Boolean = false
    ): Notification {
        val activityIntent = Intent(context, RingingActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(PlaybackService.EXTRA_FALLBACK_AUDIO_ACTIVE, fallbackAudioActive)
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE_FULLSCREEN,
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(context, PlaybackService::class.java).apply {
            action = PlaybackService.ACTION_STOP_ALARM
        }

        val stopPendingIntent = PendingIntent.getService(
            context,
            REQUEST_CODE_STOP,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.notification_alarm_ringing))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setContentIntent(fullScreenPendingIntent)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(
                R.drawable.ic_launcher_foreground,
                context.getString(R.string.stop_alarm_button),
                stopPendingIntent
            )
            .build()
    }
}
