package com.alexroux.ntsalarmclock.ui.screens.ring

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alexroux.ntsalarmclock.R
import com.alexroux.ntsalarmclock.ui.components.NTSButton
import com.alexroux.ntsalarmclock.ui.components.VolumeSlider
import com.alexroux.ntsalarmclock.ui.theme.NTSAlarmClockTheme

/**
 * Screen displayed when the alarm is ringing.
 *
 * This composable shows the current alarm UI with:
 * - the ringing message
 * - the currently playing show when available
 * - a volume slider
 * - a button allowing the user to stop the alarm
 */
@Composable
fun RingScreen(
    isFallbackAudioActive: Boolean,
    onDismiss: () -> Unit,
    viewModel: RingScreenViewModel = viewModel()
) {
    val currentShow by viewModel.currentShow.collectAsState()
    val volumeLive by viewModel.volumeLive.collectAsState()

    RingScreenContent(
        isFallbackAudioActive = isFallbackAudioActive,
        currentShow = currentShow,
        volumeLive = volumeLive,
        onVolumeLiveChange = viewModel::onVolumeLiveChange,
        onVolumeChangeFinished = viewModel::onVolumeChangeFinished,
        onStopClick = {
            viewModel.stopAlarm()
            onDismiss()
        }
    )
}

@Composable
fun RingScreenContent(
    isFallbackAudioActive: Boolean,
    currentShow: String?,
    volumeLive: Int,
    onVolumeLiveChange: (Int) -> Unit,
    onVolumeChangeFinished: (Int) -> Unit,
    onStopClick: () -> Unit
) {
    val decodedCurrentShow = if (!currentShow.isNullOrBlank()) {
        HtmlCompat.fromHtml(
            currentShow,
            HtmlCompat.FROM_HTML_MODE_LEGACY
        ).toString()
    } else {
        null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.alarm_ringing),
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.semantics {
                liveRegion = LiveRegionMode.Assertive
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (!decodedCurrentShow.isNullOrBlank()) {
            Text(
                text = stringResource(R.string.currently_playing, decodedCurrentShow),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
                textAlign = TextAlign.Center,
            )
        }

        if (isFallbackAudioActive) {
            Text(
                text = stringResource(R.string.offline_fallback_music_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.volume),
            style = MaterialTheme.typography.headlineLarge
        )

        VolumeSlider(
            volumeLive = volumeLive,
            onVolumeLiveChange = onVolumeLiveChange,
            onVolumeChangeFinished = onVolumeChangeFinished,
            label = stringResource(R.string.alarm_volume)
        )

        Spacer(modifier = Modifier.height(24.dp))

        NTSButton(
            text = stringResource(R.string.stop_alarm_button),
            textStyle = MaterialTheme.typography.displayLarge,
            onClick = onStopClick
        )
    }
}

@Preview(showBackground = true)
@Composable
fun RingScreenPreview() {
    NTSAlarmClockTheme {
        Surface {
            RingScreenContent(
                isFallbackAudioActive = true,
                currentShow = "Breakfast Show",
                volumeLive = 70,
                onVolumeLiveChange = {},
                onVolumeChangeFinished = {},
                onStopClick = {}
            )
        }
    }
}
