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
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.example.ntsalarmclock.R
import com.example.ntsalarmclock.RingingActivity
import com.example.ntsalarmclock.alarm.AlarmNotification
import com.example.ntsalarmclock.data.DataStoreAlarmSettingsRepository
import com.example.ntsalarmclock.data.alarmSettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val NTS_STREAM_URL = "https://stream-relay-geo.ntslive.net/stream"
private const val PROGRESSIVE_VOLUME_DURATION_MS = 30_000L
private const val PROGRESSIVE_VOLUME_STEP_DELAY_MS = 500L

/**
 * Foreground service responsible for playing the alarm audio.
 *
 * The service is started when the alarm triggers and keeps running in the foreground
 * while the alarm is ringing.
 */
class PlaybackService : Service() {

    companion object {
        private const val TAG = "PlaybackService"

        const val ACTION_START_ALARM = "com.example.ntsalarmclock.playback.action.START_ALARM"
        const val ACTION_STOP_ALARM = "com.example.ntsalarmclock.playback.action.STOP_ALARM"
    }

    // Player instance used to stream the alarm audio.
    private var player: ExoPlayer? = null

    // Service scope used for asynchronous work tied to the service lifecycle.
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Job used to control progressive volume updates.
    private var progressiveVolumeJob: Job? = null

    override fun onCreate() {
        super.onCreate()

        // Ensure the notification channel exists before posting notifications.
        createNotificationChannelIfNeeded()
    }

    // This service does not support binding.
    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Handles start and stop actions sent to the service.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_ALARM -> startAlarm()
            ACTION_STOP_ALARM -> stopAlarm()
            else -> Unit
        }

        // Do not recreate the service automatically if the system kills it.
        return START_NOT_STICKY
    }

    /**
     * Promotes the service to the foreground and starts audio playback.
     */
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
            // Move the service to the foreground immediately so Android allows it to keep running.
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

        startPlayback()
    }

    /**
     * Stops playback, removes the foreground state, and terminates the service.
     */
    private fun stopAlarm() {
        Log.d(TAG, "stopAlarm")

        stopPlayback()

        // Remove the foreground notification.
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }

        // Stop the service completely.
        stopSelf()
    }

    /**
     * Starts the Media3 player and applies the saved volume settings.
     */
    private fun startPlayback() {
        serviceScope.launch {
            val repository =
                DataStoreAlarmSettingsRepository(applicationContext.alarmSettingsDataStore)
            val settings = repository.settings.first()

            // Convert the saved volume from 0..100 to the ExoPlayer range 0f..1f.
            val targetVolume = (settings.volume.coerceIn(0, 100) / 100f)

            val currentPlayer = player ?: ExoPlayer.Builder(this@PlaybackService).build().also {
                player = it
            }

            currentPlayer.apply {
                // Reset any previous playback state before starting the stream again.
                stop()
                clearMediaItems()
                setMediaItem(MediaItem.fromUri(NTS_STREAM_URL))

                // Start from zero when progressive volume is enabled.
                volume = if (settings.progressiveVolume) 0f else targetVolume

                prepare()
                playWhenReady = true
            }

            if (settings.progressiveVolume) {
                startProgressiveVolume(targetVolume)
            }
        }
    }

    /**
     * Gradually increases the player volume until the saved target volume is reached.
     */
    private fun startProgressiveVolume(targetVolume: Float) {
        progressiveVolumeJob?.cancel()

        progressiveVolumeJob = serviceScope.launch {
            val currentPlayer = player ?: return@launch
            val stepCount =
                (PROGRESSIVE_VOLUME_DURATION_MS / PROGRESSIVE_VOLUME_STEP_DELAY_MS).toInt()

            val volumeStep = targetVolume / stepCount

            repeat(stepCount) {
                delay(PROGRESSIVE_VOLUME_STEP_DELAY_MS)

                val updatedVolume = (currentPlayer.volume + volumeStep).coerceAtMost(targetVolume)
                currentPlayer.volume = updatedVolume

                if (updatedVolume >= targetVolume) {
                    return@launch
                }
            }

            currentPlayer.volume = targetVolume
        }
    }

    /**
     * Stops playback and releases the current player instance.
     */
    private fun stopPlayback() {
        progressiveVolumeJob?.cancel()
        progressiveVolumeJob = null

        player?.runCatching {
            stop()
            release()
        }?.onFailure {
            Log.e(TAG, "stopPlayback failed", it)
        }

        player = null
    }

    /**
     * Safe wrapper around notification creation.
     */
    private fun buildForegroundAlarmNotificationOrNull(): Notification? {
        return runCatching { buildForegroundAlarmNotification() }
            .onFailure { Log.e(TAG, "buildForegroundAlarmNotification failed", it) }
            .getOrNull()
    }

    /**
     * Builds the foreground notification displayed while the alarm is ringing.
     */
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

        if (!notificationsEnabled || !hasPostNotificationsPermission) {
            throw IllegalStateException(
                "Notifications are not allowed. notifEnabled=$notificationsEnabled, postPerm=$hasPostNotificationsPermission"
            )
        }

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

            // Show the ringing screen above the lock screen when possible.
            .setFullScreenIntent(fullScreenPendingIntent, true)

            // Allow the user to stop the alarm directly from the notification.
            .addAction(R.drawable.ic_launcher_foreground, "Stop", stopPendingIntent)
            .build()
    }

    /**
     * Creates the notification channel used by the alarm notification.
     */
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

            // Alarm sound is handled by Media3 playback, not by the notification channel.
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        manager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        // Always release the player when the service is destroyed.
        stopPlayback()
        serviceScope.cancel()
        super.onDestroy()
    }
}