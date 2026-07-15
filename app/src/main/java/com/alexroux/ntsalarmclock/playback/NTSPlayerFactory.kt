package com.alexroux.ntsalarmclock.playback

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

private const val NTS_STREAM_URL = "https://stream-relay-geo.ntslive.net/stream"

/** Creates ExoPlayer instances configured to play NTS on the alarm audio channel. */
object NTSPlayerFactory {

    fun create(context: Context): ExoPlayer {
        val alarmAudioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_ALARM)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        return ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF

            // Media3 cannot manage audio focus automatically for USAGE_ALARM.
            setAudioAttributes(alarmAudioAttributes, false)
        }
    }

    /** Replaces the current media with the NTS stream and prepares it for playback. */
    fun prepareStream(player: ExoPlayer, volume: Float) {
        player.stop()
        player.clearMediaItems()
        player.setMediaItem(MediaItem.fromUri(NTS_STREAM_URL))
        player.volume = volume
        player.prepare()
    }
}
