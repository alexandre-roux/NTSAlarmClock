package com.example.ntsalarmclock.ui.screens.home

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
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun HomeScreen(
    viewModel: HomeScreenViewModel = viewModel()
) {
    val tag = "HomeScreen"

    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var isPlaying by remember { mutableStateOf(false) }

    // Volume used by the player, updated live while dragging
    var volumeLive by remember { mutableIntStateOf(state.volume) }

    // Prevent persisted state updates from fighting with the user's drag gesture
    var isDraggingVolume by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                Log.d(tag, "App lost focus, stop stream")
                isPlaying = false
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Sync persisted volume into live volume only when not dragging
    LaunchedEffect(state.volume) {
        if (!isDraggingVolume) {
            volumeLive = state.volume
        }
    }

    NTSPlayerEffect(
        streamUrl = state.streamUrl,
        shouldPlay = isPlaying,
        volumePercent = volumeLive
    )

    HomeScreenContent(
        state = state,
        isPlaying = isPlaying,
        volumeLive = volumeLive,
        onPlayPauseClick = {
            isPlaying = !isPlaying
            Log.d(tag, "isPlaying=$isPlaying")
        },
        onTimeChange = viewModel::onTimeChange,
        onToggleDay = viewModel::onToggleDay,
        onVolumeLiveChange = { _ ->
            isDraggingVolume = true
        },
        onVolumeChangeFinished = { finalVolume ->
            isDraggingVolume = false
            viewModel.onVolumeChange(finalVolume)
        },
        onAlarmEnabledClick = {
            viewModel.onEnabledChange()
        },
        onProgressiveVolumeEnabledChange = {
            viewModel.onProgressiveVolumeEnabledChange(it)
        }
    )
}
