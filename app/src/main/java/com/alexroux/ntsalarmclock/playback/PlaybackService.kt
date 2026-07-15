package com.alexroux.ntsalarmclock.playback

import android.Manifest
import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.OptIn
import androidx.annotation.RequiresPermission
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
import com.alexroux.ntsalarmclock.data.AlarmSettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val MANUAL_VOLUME_STEP = 0.1f

/**
 * Foreground service responsible for playing the alarm audio.
 *
 * The service is started when the alarm triggers and keeps running in the foreground
 * while the alarm is ringing.
 */
@AndroidEntryPoint
class PlaybackService : Service() {

    companion object {
        private const val TAG = "PlaybackService"

        const val ACTION_START_ALARM = "com.alexroux.ntsalarmclock.playback.action.START_ALARM"
        const val ACTION_STOP_ALARM = "com.alexroux.ntsalarmclock.playback.action.STOP_ALARM"
        const val ACTION_VOLUME_UP = "com.alexroux.ntsalarmclock.playback.action.VOLUME_UP"
        const val ACTION_VOLUME_DOWN = "com.alexroux.ntsalarmclock.playback.action.VOLUME_DOWN"
        const val ACTION_SET_VOLUME = "com.alexroux.ntsalarmclock.playback.action.SET_VOLUME"
        const val EXTRA_VOLUME = "extra_volume"
        const val EXTRA_FALLBACK_AUDIO_ACTIVE = "extra_fallback_audio_active"
    }

    @Inject
    lateinit var repository: AlarmSettingsRepository

    // Player instance used to stream the alarm audio.
    private var player: ExoPlayer? = null

    // Service scope used for asynchronous work tied to the service lifecycle.
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val volumeController by lazy {
        PlaybackVolumeController(serviceScope, repository)
    }

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

        AlarmNotification.createNotificationChannel(this)
    }

    // This service does not support binding.
    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Handles start and stop actions sent to the service.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(
            TAG,
            "onStartCommand: action=${intent?.action ?: "null"}, flags=$flags, startId=$startId"
        )

        when (intent?.action) {
            ACTION_START_ALARM -> startAlarm()
            ACTION_STOP_ALARM -> stopAlarm()
            ACTION_VOLUME_UP -> adjustTemporaryVolumeBy(MANUAL_VOLUME_STEP)
            ACTION_VOLUME_DOWN -> adjustTemporaryVolumeBy(-MANUAL_VOLUME_STEP)
            ACTION_SET_VOLUME -> {
                val volume = intent.getIntExtra(EXTRA_VOLUME, 70)
                setAbsoluteVolume(volume)
            }

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
            Log.e(TAG, "Notification build failed, launching RingingActivity as fallback")
            launchRingingActivityAsFallback()
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
            Log.d(
                TAG,
                "startForeground succeeded: notificationId=${AlarmNotification.NOTIFICATION_ID}, fgsType=$fgsType"
            )
        } catch (t: Throwable) {
            Log.e(TAG, "startForeground failed, launching RingingActivity as fallback", t)
            launchRingingActivityAsFallback()
            stopSelf()
            return
        }

        launchRingingActivityAsFallback()

        startPlayback()
    }

    /**
     * Launches RingingActivity directly as a last resort fallback when the
     * foreground service cannot be started.
     */
    private fun launchRingingActivityAsFallback() {
        try {
            val intent = Intent(this, RingingActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION)
                putExtra(EXTRA_FALLBACK_AUDIO_ACTIVE, hasSwitchedToFallbackAudio)
            }
            startActivity(intent)
            Log.d(
                TAG,
                "RingingActivity fallback launched: fallbackAudioActive=$hasSwitchedToFallbackAudio"
            )
        } catch (t: Throwable) {
            Log.e(TAG, "launchRingingActivityAsFallback failed", t)
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
            val settings = repository.settings.first()

            // Convert the saved volume from 0..100 to the ExoPlayer range 0f..1f.
            targetVolume = PlaybackServiceLogic.toPlayerVolume(settings.volume)
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
            val initialVolume = PlaybackServiceLogic.initialPlayerVolume(
                targetVolumePercent = settings.volume,
                progressiveVolumeEnabled = progressiveVolumeEnabled
            )

            NTSPlayerFactory.prepareStream(
                player = currentPlayer,
                volume = initialVolume
            )

            Log.d(TAG, "after prepare: volume=${currentPlayer.volume}")
            currentPlayer.playWhenReady = true
            Log.d(TAG, "after prepare: playWhenReady=${currentPlayer.playWhenReady}")

            if (progressiveVolumeEnabled) {
                volumeController.startProgressiveVolume(currentPlayer, targetVolume)
            }
        }.invokeOnCompletion { throwable ->
            if (throwable != null) {
                Log.e(TAG, "startPlayback coroutine failed", throwable)
            }
        }
    }

    /**
     * Switches to the bundled local audio if the remote stream cannot be played.
     */
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    @OptIn(UnstableApi::class)
    private fun switchToFallbackAudioIfNeeded() {
        val currentPlayer = player
        if (!PlaybackServiceLogic.canSwitchToFallbackAudio(
                hasSwitchedToFallbackAudio = hasSwitchedToFallbackAudio,
                playerAvailable = currentPlayer != null
            )
        ) {
            if (hasSwitchedToFallbackAudio) {
                Log.w(TAG, "Fallback audio already active, ignoring additional player error")
                return
            }

            Log.e(TAG, "Cannot switch to fallback audio because player is null")
            return
        }

        val activePlayer = currentPlayer ?: return
        hasSwitchedToFallbackAudio = true

        Log.w(TAG, "Switching to fallback audio")

        val fallbackMediaItem =
            MediaItem.fromUri("android.resource://$packageName/${R.raw.northern_glade}")

        val currentVolume = activePlayer.volume

        // Loop the local fallback track continuously while the alarm is ringing.
        activePlayer.repeatMode = Player.REPEAT_MODE_ONE
        activePlayer.setMediaItem(fallbackMediaItem)
        activePlayer.volume = currentVolume
        activePlayer.prepare()
        activePlayer.playWhenReady = true

        // Update the foreground notification so the fullscreen intent carries the new state.
        updateNotificationWithFallbackState()
    }

    /**
     * Sets an absolute volume from the UI slider and persists it.
     */
    private fun setAbsoluteVolume(volume: Int) {
        val currentPlayer = player ?: return

        progressiveVolumeEnabled = false
        targetVolume = volumeController.setAbsoluteVolume(currentPlayer, volume)
        Log.d(TAG, "Absolute volume set: $targetVolume")
    }

    /**
     * Adjusts the current volume from hardware volume buttons and persists it
     * so the ringing UI slider stays in sync.
     */
    private fun adjustTemporaryVolumeBy(delta: Float) {
        val currentPlayer = player ?: return

        progressiveVolumeEnabled = false
        targetVolume = volumeController.adjustVolume(currentPlayer, delta)
        Log.d(TAG, "Manual volume change applied: volume=$targetVolume")
    }

    /**
     * Stops playback and releases the current player instance.
     */
    private fun stopPlayback() {
        if (this::repository.isInitialized) {
            volumeController.cancelProgressiveVolume()
        }

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
        return runCatching {
            AlarmNotification.buildAlarmNotification(
                context = this,
                fallbackAudioActive = hasSwitchedToFallbackAudio
            )
        }
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

    override fun onDestroy() {
        // Always release the player when the service is destroyed.
        stopPlayback()
        serviceScope.cancel()
        super.onDestroy()
    }
}
