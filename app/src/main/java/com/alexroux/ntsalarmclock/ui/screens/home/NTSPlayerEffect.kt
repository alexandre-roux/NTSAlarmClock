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
import com.alexroux.ntsalarmclock.playback.PlaybackServiceLogic

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
     * Prepare the player once when the composable enters the composition.
     * This avoids restarting the stream on every recomposition.
     */
    LaunchedEffect(Unit) {
        NTSPlayerFactory.prepareStream(
            player = player,
            volume = PlaybackServiceLogic.toPlayerVolume(volumePercent)
        )
    }

    // Update the player volume when the UI volume changes
    LaunchedEffect(volumePercent) {
        player.volume = PlaybackServiceLogic.toPlayerVolume(volumePercent)
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
                    player.pause()
                }
                else -> Unit
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.release()
        }
    }
}
