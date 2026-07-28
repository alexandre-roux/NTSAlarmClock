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
private const val DEFAULT_VOLUME_PERCENT = 70

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
    lateinit var settingsRepository: AlarmSettingsRepository

    private var player: ExoPlayer? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val volumeController by lazy {
        PlaybackVolumeController(serviceScope, settingsRepository)
    }

    private var isFallbackAudioActive = false

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

    /** Routes alarm and volume actions sent to the service. */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(
            TAG,
            "onStartCommand: action=${intent?.action ?: "null"}, flags=$flags, startId=$startId"
        )

        when (intent?.action) {
            ACTION_START_ALARM -> startAlarm()
            ACTION_STOP_ALARM -> stopAlarm()
            ACTION_VOLUME_UP -> adjustVolumeBy(MANUAL_VOLUME_STEP)
            ACTION_VOLUME_DOWN -> adjustVolumeBy(-MANUAL_VOLUME_STEP)
            ACTION_SET_VOLUME -> setAbsoluteVolume(
                intent.getIntExtra(EXTRA_VOLUME, DEFAULT_VOLUME_PERCENT)
            )

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
            launchRingingActivity()
            stopSelf()
            return
        }

        if (!startInForeground(notification)) {
            launchRingingActivity()
            stopSelf()
            return
        }

        launchRingingActivity()
        startPlayback()
    }

    /**
     * Promotes the service immediately so Android allows alarm playback to continue.
     */
    private fun startInForeground(notification: Notification): Boolean {
        val foregroundServiceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else {
            0
        }

        return try {
            ServiceCompat.startForeground(
                this,
                AlarmNotification.NOTIFICATION_ID,
                notification,
                foregroundServiceType
            )
            Log.d(
                TAG,
                "startForeground succeeded: notificationId=${AlarmNotification.NOTIFICATION_ID}, " +
                        "fgsType=$foregroundServiceType"
            )
            true
        } catch (error: Throwable) {
            Log.e(
                TAG,
                "startForeground failed",
                error
            )
            false
        }
    }

    /** Opens the ringing screen, including when foreground startup fails. */
    private fun launchRingingActivity() {
        try {
            val intent = Intent(this, RingingActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION)
                putExtra(EXTRA_FALLBACK_AUDIO_ACTIVE, isFallbackAudioActive)
            }
            startActivity(intent)
            Log.d(
                TAG,
                "RingingActivity launched: fallbackAudioActive=$isFallbackAudioActive"
            )
        } catch (error: Throwable) {
            Log.e(TAG, "launchRingingActivity failed", error)
        }
    }

    /**
     * Stops playback, removes the foreground state, and terminates the service.
     */
    private fun stopAlarm() {
        Log.d(TAG, "stopAlarm")

        stopPlayback()

        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        stopSelf()
    }

    /**
     * Starts the Media3 player and applies the saved volume settings.
     */
    private fun startPlayback() {
        serviceScope.launch {
            val settings = settingsRepository.settings.first()
            val targetVolume = PlaybackServiceLogic.toPlayerVolume(settings.volume)
            val isProgressiveVolumeEnabled = settings.progressiveVolume
            isFallbackAudioActive = false

            val currentPlayer = getOrCreatePlayer()

            Log.d(
                TAG,
                "startPlayback: targetVolume=$targetVolume, progressive=$isProgressiveVolumeEnabled"
            )

            // The player may be returning from a looping fallback track.
            currentPlayer.repeatMode = Player.REPEAT_MODE_OFF

            val initialVolume = PlaybackServiceLogic.initialPlayerVolume(
                targetVolumePercent = settings.volume,
                progressiveVolumeEnabled = isProgressiveVolumeEnabled
            )

            NTSPlayerFactory.prepareStream(
                player = currentPlayer,
                volume = initialVolume
            )

            currentPlayer.playWhenReady = true

            if (isProgressiveVolumeEnabled) {
                volumeController.startProgressiveVolume(currentPlayer, targetVolume)
            }
        }.invokeOnCompletion { throwable ->
            if (throwable != null) {
                Log.e(TAG, "startPlayback coroutine failed", throwable)
            }
        }
    }

    private fun getOrCreatePlayer(): ExoPlayer {
        return player ?: NTSPlayerFactory.create(this).also { newPlayer ->
            newPlayer.addListener(playerListener)
            player = newPlayer
        }
    }

    /**
     * Switches to the bundled local audio if the remote stream cannot be played.
     */
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    @OptIn(UnstableApi::class)
    private fun switchToFallbackAudioIfNeeded() {
        if (isFallbackAudioActive) {
            Log.w(TAG, "Fallback audio already active, ignoring additional player error")
            return
        }

        val activePlayer = player
        if (activePlayer == null) {
            Log.e(TAG, "Cannot switch to fallback audio because player is null")
            return
        }

        isFallbackAudioActive = true

        Log.w(TAG, "Switching to fallback audio")

        val fallbackMediaItem = MediaItem.fromUri(
            "android.resource://$packageName/${R.raw.northern_glade}"
        )

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
    private fun setAbsoluteVolume(volumePercent: Int) {
        val currentPlayer = player ?: return

        val playerVolume = volumeController.setAbsoluteVolume(currentPlayer, volumePercent)
        Log.d(TAG, "Absolute volume set: $playerVolume")
    }

    /**
     * Adjusts the current volume from hardware volume buttons and persists it
     * so the ringing UI slider stays in sync.
     */
    private fun adjustVolumeBy(delta: Float) {
        val currentPlayer = player ?: return

        val playerVolume = volumeController.adjustVolumeBy(currentPlayer, delta)
        Log.d(TAG, "Manual volume change applied: volume=$playerVolume")
    }

    /**
     * Stops playback and releases the current player instance.
     */
    private fun stopPlayback() {
        if (this::settingsRepository.isInitialized) {
            volumeController.cancelProgressiveVolume()
        }

        isFallbackAudioActive = false

        player?.let { currentPlayer ->
            try {
                currentPlayer.removeListener(playerListener)
                currentPlayer.stop()
                currentPlayer.release()
            } catch (error: Throwable) {
                Log.e(TAG, "stopPlayback failed", error)
            }
        }

        player = null
    }

    private fun buildForegroundAlarmNotificationOrNull(): Notification? {
        return try {
            AlarmNotification.buildAlarmNotification(
                context = this,
                fallbackAudioActive = isFallbackAudioActive
            )
        } catch (error: Throwable) {
            Log.e(TAG, "buildForegroundAlarmNotification failed", error)
            null
        }
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
        stopPlayback()
        serviceScope.cancel()
        super.onDestroy()
    }
}
