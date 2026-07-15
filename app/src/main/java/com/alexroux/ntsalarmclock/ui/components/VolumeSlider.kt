package com.alexroux.ntsalarmclock.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.vivvvek.seeker.Seeker
import dev.vivvvek.seeker.SeekerDefaults
import kotlin.math.roundToInt

/**
 * Reusable volume slider based on the Seeker component.
 *
 * The UI uses a Float because Seeker requires it, while the app state uses
 * an Int percentage from 0 to 100.
 */
@Composable
fun VolumeSlider(
    currentVolume: Int,
    onVolumeChange: (Int) -> Unit,
    onVolumeChangeFinished: (Int) -> Unit,
    modifier: Modifier = Modifier,
    label: String
) {
    var sliderValue by remember(currentVolume) {
        mutableFloatStateOf(currentVolume.toFloat())
    }

    Box(
        modifier = modifier.semantics {
            contentDescription = label
            stateDescription = "$currentVolume percent"
            progressBarRangeInfo = ProgressBarRangeInfo(
                current = currentVolume.toFloat(),
                range = 0f..100f,
                steps = 99
            )
        }
    ) {
        Seeker(
            value = sliderValue,
            range = 0f..100f,
            onValueChange = { newValue ->
                sliderValue = newValue
                onVolumeChange(newValue.roundToInt())
            },
            onValueChangeFinished = {
                onVolumeChangeFinished(sliderValue.roundToInt())
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
