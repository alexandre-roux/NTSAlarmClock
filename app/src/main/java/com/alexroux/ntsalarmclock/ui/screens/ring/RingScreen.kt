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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alexroux.ntsalarmclock.R
import com.alexroux.ntsalarmclock.ui.components.NTSButton
import com.alexroux.ntsalarmclock.ui.theme.NTSAlarmClockTheme

/**
 * Screen displayed when the alarm is ringing.
 *
 * This composable shows a minimal UI with a message and a button
 * allowing the user to stop the alarm. When the button is pressed:
 * - the ViewModel stops the alarm playback
 * - the screen is dismissed via [onDismiss]
 */
@Composable
fun RingScreen(
    isFallbackAudioActive: Boolean,
    onDismiss: () -> Unit,
    viewModel: RingScreenViewModel = viewModel()
) {
    RingScreenContent(
        isFallbackAudioActive = isFallbackAudioActive,
        onStopClick = {
            viewModel.stopAlarm()
            onDismiss()
        }
    )
}

@Composable
fun RingScreenContent(
    isFallbackAudioActive: Boolean,
    onStopClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.alarm_ringing),
            style = MaterialTheme.typography.displaySmall
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (isFallbackAudioActive) {
            Text(
                text = stringResource(R.string.offline_fallback_music_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        NTSButton(
            text = "STOP",
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
                onStopClick = {}
            )
        }
    }
}