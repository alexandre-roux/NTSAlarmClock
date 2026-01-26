package com.example.ntsalarmclock.ui.screens.home.components

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.ntsalarmclock.R
import dev.vivvvek.seeker.Seeker
import dev.vivvvek.seeker.SeekerDefaults
import kotlin.math.roundToInt

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
                modifier = Modifier.size(64.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause stream" else "Play stream",
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }

            // Seeker uses Float, app state uses Int percent
            var volumeUi by remember { mutableFloatStateOf(volumeLive.toFloat()) }

            // Keep UI thumb in sync with live volume updates
            LaunchedEffect(volumeLive) {
                volumeUi = volumeLive.toFloat()
            }

            Seeker(
                value = volumeUi,
                range = 0f..100f,
                onValueChange = { newValue ->
                    volumeUi = newValue
                    onVolumeLiveChange(newValue.roundToInt())
                },
                onValueChangeFinished = {
                    onVolumeChangeFinished(volumeUi.roundToInt())
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
