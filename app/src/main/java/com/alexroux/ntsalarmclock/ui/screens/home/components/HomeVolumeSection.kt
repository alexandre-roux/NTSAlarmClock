package com.alexroux.ntsalarmclock.ui.screens.home.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.alexroux.ntsalarmclock.R
import com.alexroux.ntsalarmclock.ui.components.VolumeSlider

/**
 * Section of the Home screen that controls the playback preview volume.
 *
 * It provides:
 * - a play/pause button to start or stop the NTS stream preview
 * - a slider allowing the user to adjust the alarm volume (0-100)
 */
@Composable
fun HomeVolumeSection(
    isPlaying: Boolean,
    volumeLive: Int,
    onPlayPauseClick: () -> Unit,
    onVolumeLiveChange: (Int) -> Unit,
    onVolumeChangeFinished: (Int) -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(R.string.volume),
            style = MaterialTheme.typography.headlineLarge
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onPlayPauseClick,
                modifier = Modifier.semantics {
                    contentDescription = if (isPlaying) {
                        "Pause stream preview"
                    } else {
                        "Play stream preview"
                    }
                }
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }

            VolumeSlider(
                volumeLive = volumeLive,
                onVolumeLiveChange = onVolumeLiveChange,
                onVolumeChangeFinished = onVolumeChangeFinished,
                label = "Alarm volume"
            )
        }
    }
}