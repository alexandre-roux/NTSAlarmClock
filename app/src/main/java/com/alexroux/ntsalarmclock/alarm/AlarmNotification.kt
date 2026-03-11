package com.alexroux.ntsalarmclock.alarm

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

    // Notification channel used for alarm notifications
    const val CHANNEL_ID = "alarm_channel_v2"
    const val CHANNEL_NAME = "Alarm"

    // Notification id used when posting the alarm notification
    const val NOTIFICATION_ID = 1001

    // Request codes used for the PendingIntents
    const val REQUEST_CODE_FULLSCREEN = 2001
    const val REQUEST_CODE_STOP = 2002

    /**
     * Creates the notification channel used for alarms.
     *
     * On Android 8+ notifications must be posted to a channel.
     * This method is safe to call multiple times because the system
     * ignores the call if the channel already exists.
     */
    fun createNotificationChannel(context: Context) {

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications used when the alarm is ringing"

            // Make the notification visible on the lock screen
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC

            // Disable vibration and notification sound so only the alarm player is heard
            enableVibration(false)
            setSound(null, null)
        }

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        notificationManager.createNotificationChannel(channel)
    }

    /**
     * Builds the notification shown when the alarm is ringing.
     *
     * The notification:
     * - launches the full screen alarm UI
     * - appears above the lock screen
     * - provides an action to stop the alarm
     */
    fun buildAlarmNotification(context: Context): NotificationCompat.Builder {

        /**
         * Intent used to open the alarm ringing screen.
         */
        val activityIntent = Intent(context, RingingActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        /**
         * PendingIntent used for both tapping the notification
         * and launching the full screen alarm UI.
         */
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE_FULLSCREEN,
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        /**
         * Intent used to stop the alarm playback.
         */
        val stopIntent = Intent(context, PlaybackService::class.java).apply {
            action = PlaybackService.ACTION_STOP_ALARM
        }

        /**
         * PendingIntent triggered by the "Stop" action button.
         */
        val stopPendingIntent = PendingIntent.getService(
            context,
            REQUEST_CODE_STOP,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        /**
         * Build the alarm notification.
         */
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText("Alarm ringing")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setContentIntent(fullScreenPendingIntent)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(R.drawable.ic_launcher_foreground, "Stop", stopPendingIntent)
    }
}