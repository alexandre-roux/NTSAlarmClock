package com.alexroux.ntsalarmclock.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
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
import com.alexroux.ntsalarmclock.ui.components.DayOfWeekUi
import com.alexroux.ntsalarmclock.ui.screens.home.components.HomeDaysSection
import com.alexroux.ntsalarmclock.ui.screens.home.components.HomeEnableAlarmButton
import com.alexroux.ntsalarmclock.ui.screens.home.components.HomeProgressiveVolumeSection
import com.alexroux.ntsalarmclock.ui.screens.home.components.HomeTimeSection
import com.alexroux.ntsalarmclock.ui.screens.home.components.HomeVolumeSection
import com.alexroux.ntsalarmclock.ui.theme.NTSAlarmClockTheme

@Composable
fun HomeScreenContent(
    state: HomeScreenUiState.Success,
    isPlaying: Boolean,
    volumeLive: Int,
    onPlayPauseClick: () -> Unit,
    onTimeChange: (Int, Int) -> Unit,
    onToggleDay: (DayOfWeekUi) -> Unit,
    onVolumeLiveChange: (Int) -> Unit,
    onVolumeChangeFinished: (Int) -> Unit,
    onAlarmEnabledClick: () -> Unit,
    onProgressiveVolumeEnabledChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        HomeTimeSection(
            hour = state.hour,
            minute = state.minute,
            scheduledInText = state.scheduledInText,
            onTimeChange = onTimeChange
        )

        HomeVolumeSection(
            isPlaying = isPlaying,
            volumeLive = volumeLive,
            onPlayPauseClick = onPlayPauseClick,
            onVolumeLiveChange = onVolumeLiveChange,
            onVolumeChangeFinished = onVolumeChangeFinished
        )

        HomeProgressiveVolumeSection(
            progressiveVolume = state.progressiveVolume,
            onProgressiveVolumeEnabledChange = onProgressiveVolumeEnabledChange
        )

        HomeDaysSection(
            selectedDays = state.enabledDays,
            onToggleDay = onToggleDay
        )

        Spacer(modifier = Modifier.height(8.dp))

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
                state = HomeScreenUiState.Success(
                    enabled = true,
                    hour = 7,
                    minute = 0,
                    volume = 70,
                    streamUrl = "https://stream-relay-geo.ntslive.net/stream",
                    enabledDays = setOf(DayOfWeekUi.MO, DayOfWeekUi.WE, DayOfWeekUi.FR),
                    progressiveVolume = false,
                    scheduledInText = "This alarm is scheduled in 10 hours and 12 minutes"
                ),
                isPlaying = false,
                volumeLive = 70,
                onPlayPauseClick = {},
                onTimeChange = { _, _ -> },
                onToggleDay = {},
                onVolumeLiveChange = {},
                onVolumeChangeFinished = {},
                onAlarmEnabledClick = {},
                onProgressiveVolumeEnabledChange = {}
            )
        }
    }
}