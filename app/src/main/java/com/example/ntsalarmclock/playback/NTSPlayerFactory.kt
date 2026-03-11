package com.example.ntsalarmclock.playback

import android.content.Context
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

const val NTS_STREAM_URL = "https://stream-relay-geo.ntslive.net/stream"

/**
 * Factory responsible for creating and preparing ExoPlayer instances
 * used for NTS playback on the Android alarm audio channel.
 */
object NTSPlayerFactory {

    /**
     * Creates a new ExoPlayer instance configured for alarm playback.
     */
    fun create(context: Context, tag: String = "NTSPlayer"): ExoPlayer {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_ALARM)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        return ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
            setAudioAttributes(audioAttributes, true)

            addListener(
                object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        Log.d(tag, "state=$playbackState")
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        Log.d(tag, "isPlaying=$isPlaying")
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Log.e(tag, "playerError", error)
                    }
                }
            )
        }
    }

    /**
     * Prepares the NTS stream on an existing player and applies the provided volume.
     */
    fun prepareStream(player: ExoPlayer, volume: Float) {
        player.stop()
        player.clearMediaItems()
        player.setMediaItem(MediaItem.fromUri(NTS_STREAM_URL))
        player.volume = volume
        player.prepare()
    }
}