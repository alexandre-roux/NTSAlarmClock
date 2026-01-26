package com.example.ntsalarmclock.ui.screens.home

import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ntsalarmclock.R
import com.example.ntsalarmclock.ui.components.CyclicTimePicker
import com.example.ntsalarmclock.ui.theme.NTSAlarmClockTheme
import dev.vivvvek.seeker.Seeker
import dev.vivvvek.seeker.SeekerDefaults
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    viewModel: HomeScreenViewModel = viewModel()
) {
    val TAG = "HomeScreen"

    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var isPlaying by remember { mutableStateOf(false) }
    var volumeLive by remember(state.volume) { mutableIntStateOf(state.volume) }

    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                Log.d(TAG, "App lost focus -> stop stream")
                isPlaying = false
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
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
        onPlayPauseClick = {
            isPlaying = !isPlaying
            Log.d(TAG, "isPlaying=$isPlaying")
        },
        onEnabledChange = viewModel::onEnabledChange,
        onTimeChange = viewModel::onTimeChange,
        onVolumeChange = viewModel::onVolumeChange,
        onVolumeLiveChange = { }
    )
}

@Composable
private fun HomeScreenContent(
    state: HomeScreenUiState,
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onTimeChange: (Int, Int) -> Unit,
    onVolumeChange: (Int) -> Unit,
    onVolumeLiveChange: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.select_time),
                style = MaterialTheme.typography.headlineLarge
            )
            CyclicTimePicker(
                hour = state.hour,
                minute = state.minute,
                onTimeChange = onTimeChange,
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.set_volume),
                style = MaterialTheme.typography.headlineLarge
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onPlayPauseClick,
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause stream" else "Play stream",
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }
                // Keep UI responsive while dragging, then persist on release
                var volumeUi by remember { mutableFloatStateOf(state.volume.toFloat()) }
                LaunchedEffect(state.volume) {
                    volumeUi = state.volume.toFloat()
                }
                Seeker(
                    value = volumeUi,
                    range = 0f..50f,
                    onValueChange = { newValue ->
                        volumeUi = newValue
                        onVolumeLiveChange(volumeUi.roundToInt())
                    },
                    onValueChangeFinished = {
                        onVolumeChange(volumeUi.roundToInt())
                    },
                    colors = SeekerDefaults.seekerColors(
                        progressColor = Color.White,
                        trackColor = Color(0xFF6B6B6B),
                        thumbColor = Color.White,
                        readAheadColor = Color(0xFF6B6B6B)
                    ),
                    dimensions = SeekerDefaults.seekerDimensions(
                        trackHeight = 6.dp,
                        progressHeight = 6.dp,
                        thumbRadius = 10.dp,
                        gap = 0.dp
                    )
                )
            }


        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    NTSAlarmClockTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            HomeScreenContent(
                state = HomeScreenUiState(enabled = true, hour = 7, minute = 0, volume = 70),
                onEnabledChange = {},
                onTimeChange = { _, _ -> },
                onVolumeChange = {},
                isPlaying = false,
                onPlayPauseClick = {},
            ) {}
        }
    }
}
