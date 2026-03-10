package com.example.ntsalarmclock.ui.screens.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * Compose side-effect that hosts a lightweight ExoPlayer instance used
 * to preview the NTS radio stream from the Home screen.
 *
 * This composable does not render UI. Instead it manages the lifecycle
 * of the player and reacts to state changes from the HomeScreen:
 *
 * - prepares the player when the stream URL changes
 * - updates the playback volume
 * - starts or pauses playback depending on the UI state
 * - pauses playback when the app loses focus
 * - releases the player when the composable leaves the composition
 */
@Composable
fun NTSPlayerEffect(
    streamUrl: String,
    shouldPlay: Boolean,
    volumePercent: Int,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Create the ExoPlayer instance once and keep it across recompositions
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    /**
     * Convert the app volume (0..100) into ExoPlayer's expected range (0f..1f)
     * and apply it to the player.
     */
    fun applyVolume(percent: Int) {
        val v = (percent.coerceIn(0, 100) / 100f).coerceIn(0f, 1f)
        player.volume = v
    }

    // Prepare the player when the stream URL changes
    LaunchedEffect(streamUrl) {
        player.setMediaItem(MediaItem.fromUri(streamUrl))
        player.prepare()
    }

    // Update the player volume when the UI volume changes
    LaunchedEffect(volumePercent) {
        applyVolume(volumePercent)
    }

    // Start or pause playback depending on the requested state
    LaunchedEffect(shouldPlay) {
        if (shouldPlay) {
            player.play()
        } else {
            player.pause()
        }
    }

    // Observe lifecycle events to stop playback when the app loses focus
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    // Stop audio when app loses focus, but keep player prepared
                    player.pause()
                }

                else -> Unit
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            // Clean up resources when the composable leaves the composition
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.release()
        }
    }
}