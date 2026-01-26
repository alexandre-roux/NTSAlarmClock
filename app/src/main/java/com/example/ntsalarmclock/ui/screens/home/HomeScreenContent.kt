package com.example.ntsalarmclock.ui.screens.home

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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.ntsalarmclock.ui.components.DayOfWeekUi
import com.example.ntsalarmclock.ui.screens.home.components.HomeDaysSection
import com.example.ntsalarmclock.ui.screens.home.components.HomeEnableAlarmButton
import com.example.ntsalarmclock.ui.screens.home.components.HomeTimeSection
import com.example.ntsalarmclock.ui.screens.home.components.HomeVolumeSection
import com.example.ntsalarmclock.ui.theme.NTSAlarmClockTheme

@Composable
fun HomeScreenContent(
    state: HomeScreenUiState,
    isPlaying: Boolean,
    volumeLive: Int,
    onPlayPauseClick: () -> Unit,
    onTimeChange: (Int, Int) -> Unit,
    onToggleDay: (DayOfWeekUi) -> Unit,
    onVolumeLiveChange: (Int) -> Unit,
    onVolumeChangeFinished: (Int) -> Unit,
    onAlarmEnabledClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HomeTimeSection(
            hour = state.hour,
            minute = state.minute,
            onTimeChange = onTimeChange
        )

        Spacer(modifier = Modifier.height(16.dp))

        HomeVolumeSection(
            isPlaying = isPlaying,
            volumeLive = volumeLive,
            onPlayPauseClick = onPlayPauseClick,
            onVolumeLiveChange = onVolumeLiveChange,
            onVolumeChangeFinished = onVolumeChangeFinished
        )

        Spacer(modifier = Modifier.height(16.dp))

        HomeDaysSection(
            selectedDays = state.enabledDays,
            onToggleDay = onToggleDay
        )

        Spacer(modifier = Modifier.height(32.dp))

        HomeEnableAlarmButton(
            isAlarmEnabled = state.enabled,
            onClick = onAlarmEnabledClick
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenContentPreview() {
    NTSAlarmClockTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            HomeScreenContent(
                state = HomeScreenUiState(
                    enabled = true,
                    hour = 7,
                    minute = 0,
                    volume = 70,
                    enabledDays = setOf(DayOfWeekUi.MO, DayOfWeekUi.WE, DayOfWeekUi.FR)
                ),
                isPlaying = false,
                volumeLive = 70,
                onPlayPauseClick = {},
                onTimeChange = { _, _ -> },
                onToggleDay = {},
                onVolumeLiveChange = {},
                onVolumeChangeFinished = {},
                onAlarmEnabledClick = {}
            )
        }
    }
}
