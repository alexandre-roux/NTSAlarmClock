package com.alexroux.ntsalarmclock.ui.screens.home

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private const val TAG = "HomeScreen"

/**
 * Main screen of the application where the user configures the alarm.
 *
 * This composable:
 * - Observes the ViewModel UI state
 * - Displays either a loading screen or the full Home UI
 * - Manages temporary UI-only state for stream playback and preview volume
 * - Bridges user interactions to the ViewModel
 *
 * The screen also hosts the NTS stream preview used to test the alarm volume.
 */
@Composable
fun HomeScreen(
    viewModel: HomeScreenViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when (val state = uiState) {
        HomeScreenUiState.Loading -> {
            HomeScreenLoadingContent()
        }

        is HomeScreenUiState.Success -> {
            var isPreviewPlaying by remember { mutableStateOf(false) }
            var previewVolume by remember { mutableIntStateOf(state.volume) }
            var isDraggingVolume by remember { mutableStateOf(false) }

            LaunchedEffect(state.volume) {
                if (!isDraggingVolume) {
                    previewVolume = state.volume
                }
            }

            // Observe lifecycle to stop the preview stream when the app loses focus
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_STOP) {
                        Log.d(TAG, "App lost focus, stop stream")
                        isPreviewPlaying = false
                    }
                }

                lifecycleOwner.lifecycle.addObserver(observer)

                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            NTSPlayerEffect(
                shouldPlay = isPreviewPlaying,
                volumePercent = previewVolume
            )

            HomeScreenContent(
                state = state,
                isPlaying = isPreviewPlaying,
                currentVolume = previewVolume,
                onPlayPauseClick = {
                    isPreviewPlaying = !isPreviewPlaying
                    Log.d(TAG, "isPreviewPlaying=$isPreviewPlaying")
                },
                onTimeChange = viewModel::onTimeChange,
                onToggleDay = viewModel::onToggleDay,
                onVolumeChange = { newVolume ->
                    isDraggingVolume = true
                    previewVolume = newVolume.coerceIn(0, 100)
                },
                onVolumeChangeFinished = { finalVolume ->
                    isDraggingVolume = false
                    val clamped = finalVolume.coerceIn(0, 100)
                    previewVolume = clamped
                    viewModel.onVolumeChange(clamped)
                },
                onAlarmEnabledClick = viewModel::onEnabledChange,
                onProgressiveVolumeEnabledChange = viewModel::onProgressiveVolumeEnabledChange
            )
        }
    }
}
