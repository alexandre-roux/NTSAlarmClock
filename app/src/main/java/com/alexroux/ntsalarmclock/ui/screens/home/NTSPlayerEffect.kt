package com.alexroux.ntsalarmclock.ui.screens.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.alexroux.ntsalarmclock.playback.NTSPlayerFactory

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
    shouldPlay: Boolean,
    volumePercent: Int,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Create the ExoPlayer instance once and keep it across recompositions
    val player = remember {
        NTSPlayerFactory.create(context)
    }

    /**
     * Convert the app volume (0..100) into ExoPlayer's expected range (0f..1f).
     */
    fun toPlayerVolume(percent: Int): Float {
        return (percent.coerceIn(0, 100) / 100f).coerceIn(0f, 1f)
    }

    // Prepare the player when the stream URL changes
    NTSPlayerFactory.prepareStream(
        player = player,
        volume = toPlayerVolume(volumePercent)
    )

    // Update the player volume when the UI volume changes
    LaunchedEffect(volumePercent) {
        player.volume = toPlayerVolume(volumePercent)
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