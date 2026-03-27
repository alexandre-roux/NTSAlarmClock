package com.alexroux.ntsalarmclock.playback

import android.Manifest
import android.annotation.SuppressLint
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
import android.provider.Settings
import android.util.Log
import androidx.annotation.OptIn
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.alexroux.ntsalarmclock.R
import com.alexroux.ntsalarmclock.RingingActivity
import com.alexroux.ntsalarmclock.alarm.AlarmNotification
import com.alexroux.ntsalarmclock.data.DataStoreAlarmSettingsRepository
import com.alexroux.ntsalarmclock.data.alarmSettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val PROGRESSIVE_VOLUME_DURATION_MS = 60_000L
private const val PROGRESSIVE_VOLUME_STEP_DELAY_MS = 1_000L
private const val MANUAL_VOLUME_STEP = 0.1f

/**
 * Foreground service responsible for playing the alarm audio.
 *
 * The service is started when the alarm triggers and keeps running in the foreground
 * while the alarm is ringing.
 */
class PlaybackService : Service() {

    companion object {
        private const val TAG = "PlaybackService"

        const val ACTION_START_ALARM = "com.alexroux.ntsalarmclock.playback.action.START_ALARM"
        const val ACTION_STOP_ALARM = "com.alexroux.ntsalarmclock.playback.action.STOP_ALARM"
        const val ACTION_VOLUME_UP = "com.alexroux.ntsalarmclock.playback.action.VOLUME_UP"
        const val ACTION_VOLUME_DOWN = "com.alexroux.ntsalarmclock.playback.action.VOLUME_DOWN"
        const val ACTION_SET_VOLUME = "com.alexroux.ntsalarmclock.playback.action.SET_VOLUME"
        const val ACTION_BRING_TO_FRONT =
            "com.alexroux.ntsalarmclock.playback.action.BRING_TO_FRONT"
        const val EXTRA_VOLUME = "extra_volume"
        const val EXTRA_FALLBACK_AUDIO_ACTIVE = "extra_fallback_audio_active"
    }

    // Player instance used to stream the alarm audio.
    private var player: ExoPlayer? = null

    // Service scope used for asynchronous work tied to the service lifecycle.
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Job used to control progressive volume updates.
    private var progressiveVolumeJob: Job? = null

    // Prevents switching to the local fallback audio more than once.
    private var hasSwitchedToFallbackAudio = false

    // Remembers the target volume so fallback audio can reuse it.
    private var targetVolume: Float = 1f

    // Remembers whether progressive volume is enabled for the current alarm.
    private var progressiveVolumeEnabled: Boolean = false

    private val playerListener = object : Player.Listener {
        @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "Player error while playing alarm audio", error)
            switchToFallbackAudioIfNeeded()
        }
    }

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
            ACTION_VOLUME_UP -> adjustTemporaryVolumeBy(MANUAL_VOLUME_STEP)
            ACTION_VOLUME_DOWN -> adjustTemporaryVolumeBy(-MANUAL_VOLUME_STEP)
            ACTION_SET_VOLUME -> {
                val volume = intent.getIntExtra(EXTRA_VOLUME, 70)
                setAbsoluteVolume(volume)
            }
            ACTION_BRING_TO_FRONT -> bringRingingActivityToFront()
            else -> Unit
        }

        // Do not recreate the service automatically if the system kills it.
        return START_NOT_STICKY
    }

    /**
     * Promotes the service to the foreground and starts audio playback.
     *
     * On some devices the fullScreenIntent is often blocked when the screen
     * is off and the app is in background. If the SYSTEM_ALERT_WINDOW permission
     * has been granted by the user, we launch RingingActivity directly via
     * startActivity() which bypasses the background activity launch restriction.
     */
    private fun startAlarm() {
        Log.d(TAG, "startAlarm")

        val notification = buildForegroundAlarmNotificationOrNull()
        if (notification == null) {
            Log.e(TAG, "Notification build failed")
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

        // On Samsung (and other OEMs), fullScreenIntent is blocked when the screen
        // is off. If the overlay permission is granted, launch RingingActivity directly.
        if (Settings.canDrawOverlays(this)) {
            Log.d(TAG, "startAlarm: overlay permission granted, launching RingingActivity directly")
            launchRingingActivity()
        } else {
            Log.d(TAG, "startAlarm: no overlay permission, relying on fullScreenIntent only")
        }

        startPlayback()
    }

    /**
     * Launches RingingActivity directly using the overlay permission.
     * This is the reliable way to display the alarm UI on some devices
     * when the screen is off or locked.
     */
    private fun launchRingingActivity() {
        try {
            val intent = Intent(this, RingingActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(EXTRA_FALLBACK_AUDIO_ACTIVE, hasSwitchedToFallbackAudio)
            }
            startActivity(intent)
        } catch (t: Throwable) {
            Log.e(TAG, "launchRingingActivity failed", t)
        }
    }

    /**
     * Re-posts the foreground notification to force Android to re-evaluate
     * the fullScreenIntent, which brings RingingActivity back to the front.
     * Called when RingingActivity goes to the background unexpectedly
     */
    private fun bringRingingActivityToFront() {
        Log.d(TAG, "bringRingingActivityToFront")

        // If overlay permission is available, just relaunch directly — more reliable.
        if (Settings.canDrawOverlays(this)) {
            launchRingingActivity()
            return
        }

        // Fallback: re-post the notification so its fullScreenIntent fires again.
        val notification = buildForegroundAlarmNotificationOrNull() ?: return
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            manager.notify(AlarmNotification.NOTIFICATION_ID, notification)
        }
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
            targetVolume = settings.volume.coerceIn(0, 100) / 100f
            progressiveVolumeEnabled = settings.progressiveVolume
            hasSwitchedToFallbackAudio = false

            val currentPlayer = player ?: NTSPlayerFactory.create(
                context = this@PlaybackService
            ).also {
                it.addListener(playerListener)
                player = it
            }

            Log.d(
                TAG,
                "startPlayback: url=$NTS_STREAM_URL, targetVolume=$targetVolume, progressive=$progressiveVolumeEnabled"
            )

            // Use normal repeat mode for the remote stream.
            currentPlayer.repeatMode = Player.REPEAT_MODE_OFF

            // Start from zero when progressive volume is enabled.
            val initialVolume = if (progressiveVolumeEnabled) 0f else targetVolume

            NTSPlayerFactory.prepareStream(
                player = currentPlayer,
                volume = initialVolume
            )

            Log.d(TAG, "after prepare: volume=${currentPlayer.volume}")
            currentPlayer.playWhenReady = true
            Log.d(TAG, "after prepare: playWhenReady=${currentPlayer.playWhenReady}")

            if (progressiveVolumeEnabled) {
                startProgressiveVolume(targetVolume)
            }
        }
    }

    /**
     * Switches to the bundled local audio if the remote stream cannot be played.
     */
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    @OptIn(UnstableApi::class)
    private fun switchToFallbackAudioIfNeeded() {
        if (hasSwitchedToFallbackAudio) {
            Log.w(TAG, "Fallback audio already active, ignoring additional player error")
            return
        }

        val currentPlayer = player ?: run {
            Log.e(TAG, "Cannot switch to fallback audio because player is null")
            return
        }

        hasSwitchedToFallbackAudio = true

        Log.w(TAG, "Switching to fallback audio")

        val fallbackMediaItem =
            MediaItem.fromUri("android.resource://$packageName/${R.raw.northern_glade}")

        val currentVolume = currentPlayer.volume

        // Loop the local fallback track continuously while the alarm is ringing.
        currentPlayer.repeatMode = Player.REPEAT_MODE_ONE
        currentPlayer.setMediaItem(fallbackMediaItem)
        currentPlayer.volume = currentVolume
        currentPlayer.prepare()
        currentPlayer.playWhenReady = true

        // Update the foreground notification so the fullscreen intent carries the new state.
        updateNotificationWithFallbackState()
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
     * Sets an absolute volume from the UI slider and persists it.
     */
    private fun setAbsoluteVolume(volume: Int) {
        val currentPlayer = player ?: return

        val sanitizedVolume = volume.coerceIn(0, 100)
        val normalized = sanitizedVolume / 100f

        progressiveVolumeJob?.cancel()
        progressiveVolumeJob = null
        progressiveVolumeEnabled = false

        currentPlayer.volume = normalized
        targetVolume = normalized

        serviceScope.launch {
            val repository =
                DataStoreAlarmSettingsRepository(applicationContext.alarmSettingsDataStore)
            repository.setVolume(sanitizedVolume)
        }

        Log.d(TAG, "Absolute volume set: $normalized")
    }

    /**
     * Adjusts the current volume from hardware volume buttons and persists it
     * so the ringing UI slider stays in sync.
     */
    private fun adjustTemporaryVolumeBy(delta: Float) {
        val currentPlayer = player ?: return

        progressiveVolumeJob?.cancel()
        progressiveVolumeJob = null
        progressiveVolumeEnabled = false

        val updatedVolume = (currentPlayer.volume + delta).coerceIn(0f, 1f)
        currentPlayer.volume = updatedVolume
        targetVolume = updatedVolume

        serviceScope.launch {
            val repository =
                DataStoreAlarmSettingsRepository(applicationContext.alarmSettingsDataStore)
            val volumeInt = (updatedVolume * 100).toInt()
            repository.setVolume(volumeInt)
        }

        Log.d(TAG, "Manual volume change applied: volume=$updatedVolume")
    }

    /**
     * Stops playback and releases the current player instance.
     */
    private fun stopPlayback() {
        progressiveVolumeJob?.cancel()
        progressiveVolumeJob = null

        hasSwitchedToFallbackAudio = false

        player?.runCatching {
            removeListener(playerListener)
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
     * Reposts the foreground notification with the current fallback state.
     */
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun updateNotificationWithFallbackState() {
        val notification = buildForegroundAlarmNotificationOrNull() ?: return
        NotificationManagerCompat.from(this).notify(AlarmNotification.NOTIFICATION_ID, notification)
    }

    /**
     * Builds the foreground notification displayed while the alarm is ringing.
     */
    @SuppressLint("FullScreenIntentPolicy")
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
            putExtra(EXTRA_FALLBACK_AUDIO_ACTIVE, hasSwitchedToFallbackAudio)
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
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(R.drawable.ic_launcher_foreground, "Stop", stopPendingIntent)
            .build()
    }

    /**
     * Creates the notification channel used by the alarm notification.
     */
    private fun createNotificationChannelIfNeeded() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        if (manager.getNotificationChannel(AlarmNotification.CHANNEL_ID) != null) return

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