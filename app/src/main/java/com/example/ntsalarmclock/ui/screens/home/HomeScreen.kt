package com.example.ntsalarmclock.ui.screens.home

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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun HomeScreen(
    viewModel: HomeScreenViewModel
) {
    val TAG = "HomeScreen"

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when (val state = uiState) {

        HomeScreenUiState.Loading -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            )
        }

        is HomeScreenUiState.Success -> {

            var isPlaying by remember { mutableStateOf(false) }

            var volumeLive by remember { mutableIntStateOf(state.volume) }

            var isDraggingVolume by remember { mutableStateOf(false) }

            var pendingPersistedVolume by remember { mutableStateOf<Int?>(null) }

            val lifecycleOwner = LocalLifecycleOwner.current

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
                    Log.d(TAG, "isPlaying=$isPlaying")
                },
                onTimeChange = viewModel::onTimeChange,
                onToggleDay = viewModel::onToggleDay,
                onVolumeLiveChange = { newVolume ->
                    isDraggingVolume = true
                    volumeLive = newVolume.coerceIn(0, 100)
                },
                onVolumeChangeFinished = { finalVolume ->
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