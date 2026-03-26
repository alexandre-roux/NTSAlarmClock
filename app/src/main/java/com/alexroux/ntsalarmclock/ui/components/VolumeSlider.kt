package com.alexroux.ntsalarmclock.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
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
    volumeLive: Int,
    onVolumeLiveChange: (Int) -> Unit,
    onVolumeChangeFinished: (Int) -> Unit
) {
    var volumeUi by remember { mutableFloatStateOf(volumeLive.toFloat()) }

    // Keep the thumb position in sync with external state updates.
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