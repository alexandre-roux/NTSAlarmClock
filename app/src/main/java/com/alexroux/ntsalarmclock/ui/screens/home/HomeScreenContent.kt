package com.alexroux.ntsalarmclock.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.alexroux.ntsalarmclock.R
import com.alexroux.ntsalarmclock.ui.screens.home.components.HomeDaysSection
import com.alexroux.ntsalarmclock.ui.screens.home.components.HomeEnableAlarmButton
import com.alexroux.ntsalarmclock.ui.screens.home.components.HomeProgressiveVolumeSection
import com.alexroux.ntsalarmclock.ui.screens.home.components.HomeTimeSection
import com.alexroux.ntsalarmclock.ui.screens.home.components.HomeVolumeSection
import com.alexroux.ntsalarmclock.ui.theme.NTSAlarmClockTheme
import java.time.DayOfWeek

@Composable
fun HomeScreenLoadingContent() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.loading_settings),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}

@Composable
fun HomeScreenContent(
    state: HomeScreenUiState.Success,
    isPlaying: Boolean,
    volumeLive: Int,
    onPlayPauseClick: () -> Unit,
    onTimeChange: (Int, Int) -> Unit,
    onToggleDay: (DayOfWeek) -> Unit,
    onVolumeLiveChange: (Int) -> Unit,
    onVolumeChangeFinished: (Int) -> Unit,
    onAlarmEnabledClick: () -> Unit,
    onProgressiveVolumeEnabledChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
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

@Preview(showBackground = true, name = "Home Screen Loading - English", locale = "en")
@Preview(showBackground = true, name = "Home Screen Loading - French", locale = "fr")
@Preview(showBackground = true, name = "Home Screen Loading - German", locale = "de")
@Composable
private fun HomeScreenLoadingContentPreview() {
    NTSAlarmClockTheme {
        HomeScreenLoadingContent()
    }
}

@Preview(showBackground = true, name = "Home Screen - English", locale = "en")
@Preview(showBackground = true, name = "Home Screen - French", locale = "fr")
@Preview(showBackground = true, name = "Home Screen - German", locale = "de")
@Composable
private fun HomeScreenContentAlarmSetPreview() {
    NTSAlarmClockTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            HomeScreenContent(
                state = HomeScreenUiState.Success(
                    enabled = true,
                    hour = 7,
                    minute = 0,
                    volume = 70,
                    streamUrl = "https://stream-relay-geo.ntslive.net/stream",
                    enabledDays = setOf(
                        DayOfWeek.MONDAY,
                        DayOfWeek.WEDNESDAY,
                        DayOfWeek.FRIDAY
                    ),
                    progressiveVolume = false,
                    scheduledInText = stringResource(R.string.preview_alarm_scheduled_in)
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

@Preview(showBackground = true, name = "Home Screen Not Set - English", locale = "en")
@Preview(showBackground = true, name = "Home Screen Not Set - French", locale = "fr")
@Preview(showBackground = true, name = "Home Screen Not Set - German", locale = "de")
@Composable
private fun HomeScreenContentAlarmNotSetPreview() {
    NTSAlarmClockTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            HomeScreenContent(
                state = HomeScreenUiState.Success(
                    enabled = false,
                    hour = 7,
                    minute = 0,
                    volume = 70,
                    streamUrl = "https://stream-relay-geo.ntslive.net/stream",
                    enabledDays = emptySet(),
                    progressiveVolume = false,
                    scheduledInText = ""
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
