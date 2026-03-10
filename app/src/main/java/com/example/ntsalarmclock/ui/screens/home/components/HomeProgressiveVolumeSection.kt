package com.example.ntsalarmclock.ui.screens.home.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.example.ntsalarmclock.R

/**
 * Section of the Home screen that allows the user to enable or disable
 * the progressive volume feature of the alarm.
 *
 * When enabled, the alarm volume gradually increases instead of
 * starting immediately at the configured volume level.
 *
 * The selection state is controlled by the ViewModel via [progressiveVolume].
 */
@Composable
fun HomeProgressiveVolumeSection(
    progressiveVolume: Boolean,
    onProgressiveVolumeEnabledChange: (Boolean) -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(R.string.progressive_volume),
            style = MaterialTheme.typography.headlineLarge
        )
        Text(
            text = stringResource(R.string.progressive_volume_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
            textAlign = TextAlign.Center,
        )
        Switch(
            checked = progressiveVolume,
            onCheckedChange = {
                onProgressiveVolumeEnabledChange(it)
            }
        )
    }
}