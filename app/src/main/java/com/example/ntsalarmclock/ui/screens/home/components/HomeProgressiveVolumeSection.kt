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
import com.example.ntsalarmclock.ui.screens.home.HomeScreenUiState

@Composable
fun HomeProgressiveVolumeSection(
    state: HomeScreenUiState.Success,
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
            checked = state.progressiveVolume,
            onCheckedChange = {
                onProgressiveVolumeEnabledChange(it)
            }
        )
    }
}