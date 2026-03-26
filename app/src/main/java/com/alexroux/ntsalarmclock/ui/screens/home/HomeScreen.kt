package com.alexroux.ntsalarmclock.ui.screens.home

import android.app.Application
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Main screen of the application where the user configures the alarm.
 *
 * This composable:
 * - Observes the ViewModel UI state
 * - Displays either a loading screen or the full Home UI
 * - Manages temporary UI-only states such as playback preview and live volume
 * - Bridges user interactions to the ViewModel
 *
 * The screen also hosts the NTS stream preview used to test the alarm volume.
 */
@Composable
fun HomeScreen(
    viewModel: HomeScreenViewModel = viewModel(
        factory = HomeScreenViewModel.factory(
            application = LocalContext.current.applicationContext as Application
        )
    )
) {
    val TAG = "HomeScreen"

    // Collect UI state from the ViewModel in a lifecycle-aware way
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when (val state = uiState) {

        // Simple loading screen displayed while alarm settings are being loaded
        HomeScreenUiState.Loading -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            )
        }

        is HomeScreenUiState.Success -> {

            // Whether the preview stream is currently playing
            var isPlaying by remember { mutableStateOf(false) }

            // Live volume used by the preview player (may differ temporarily from persisted value)
            var volumeLive by remember { mutableIntStateOf(state.volume) }

            // True while the user is dragging the volume slider
            var isDraggingVolume by remember { mutableStateOf(false) }

            // Temporary value used to avoid UI flicker while waiting for DataStore persistence
            var pendingPersistedVolume by remember { mutableStateOf<Int?>(null) }

            val lifecycleOwner = LocalLifecycleOwner.current

            // Observe lifecycle to stop the preview stream when the app loses focus
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_STOP) {
                        Log.d(TAG, "App lost focus, stop stream")
                        isPlaying = false
                    }
                }

                lifecycleOwner.lifecycle.addObserver(observer)

                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            /**
             * Synchronize UI volume with the persisted volume coming from DataStore.
             * This effect avoids overwriting the slider while the user is dragging it
             * and prevents visual jumps while waiting for the repository to confirm
             * a persisted volume change.
             */
            LaunchedEffect(state.volume, isDraggingVolume, pendingPersistedVolume) {
                if (isDraggingVolume) return@LaunchedEffect

                val pending = pendingPersistedVolume
                if (pending != null) {
                    if (state.volume == pending) {
                        pendingPersistedVolume = null
                    } else {
                        return@LaunchedEffect
                    }
                }

                volumeLive = state.volume
            }

            // Side effect that hosts the NTS preview player
            NTSPlayerEffect(
                shouldPlay = isPlaying,
                volumePercent = volumeLive
            )

            // Main Home UI content
            HomeScreenContent(
                state = state,
                isPlaying = isPlaying,
                volumeLive = volumeLive,
                onPlayPauseClick = {
                    isPlaying = !isPlaying
                    Log.d(TAG, "isPlaying=$isPlaying")
                },
                onTimeChange = viewModel::onTimeChange,
                onToggleDay = viewModel::onToggleDay,
                onVolumeLiveChange = { newVolume ->
                    // Update preview volume immediately while dragging
                    isDraggingVolume = true
                    volumeLive = newVolume.coerceIn(0, 100)
                },
                onVolumeChangeFinished = { finalVolume ->
                    // Persist volume only when the user releases the slider
                    isDraggingVolume = false
                    val clamped = finalVolume.coerceIn(0, 100)

                    volumeLive = clamped
                    pendingPersistedVolume = clamped

                    viewModel.onVolumeChange(clamped)
                },
                onAlarmEnabledClick = viewModel::onEnabledChange,
                onProgressiveVolumeEnabledChange = viewModel::onProgressiveVolumeEnabledChange
            )
        }
    }
}