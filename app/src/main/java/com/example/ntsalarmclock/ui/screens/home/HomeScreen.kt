package com.example.ntsalarmclock.ui.screens.home

import CyclicTimePicker
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ntsalarmclock.R
import com.example.ntsalarmclock.ui.theme.NTSAlarmClockTheme
import java.time.LocalTime

@Composable
fun HomeScreen(
    viewModel: HomeScreenViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    HomeScreenContent(
        state = state,
        onEnabledChange = viewModel::onEnabledChange,
        onTimeChange = viewModel::onTimeChange
    )
}

private const val TAG = "HomeScreenContent"
@Composable
private fun HomeScreenContent(
    state: HomeScreenUiState,
    onEnabledChange: (Boolean) -> Unit,
    onTimeChange: (Int, Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            stringResource(R.string.activate_alarm),
            style = MaterialTheme.typography.headlineLarge
        )
        Switch(
            checked = state.enabled,
            onCheckedChange = {
                Log.d(TAG, "onCheckedChange: $it")
                onEnabledChange(it)
            }
        )

        AnimatedVisibility(visible = state.enabled) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.select_time),
                    style = MaterialTheme.typography.headlineLarge
                )

                CyclicTimePicker(
                    hour = state.hour,
                    minute = state.minute,
                    onTimeChange = onTimeChange,
                )

                val now = LocalTime.now()
                val durationUntilAlarm = remember(state.hour, state.minute) {
                    val selectedTime = LocalTime.of(state.hour, state.minute)
                    val minutesNow = now.hour * 60 + now.minute
                    val minutesSelected = selectedTime.hour * 60 + selectedTime.minute

                    val diffMinutes = if (minutesSelected >= minutesNow) {
                        minutesSelected - minutesNow
                    } else {
                        24 * 60 - minutesNow + minutesSelected
                    }

                    val hours = diffMinutes / 60
                    val minutes = diffMinutes % 60

                    hours to minutes
                }
                Text(
                    text = "This alarm will start in ${durationUntilAlarm.first} hours ${durationUntilAlarm.second} minutes",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    NTSAlarmClockTheme {
        Surface(
            color = MaterialTheme.colorScheme.background
        ) {
            HomeScreenContent(
                state = HomeScreenUiState(enabled = true, hour = 7, minute = 0),
                onEnabledChange = {},
                onTimeChange = { _, _ -> }
            )
        }
    }
}