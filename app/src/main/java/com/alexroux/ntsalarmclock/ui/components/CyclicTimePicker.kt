package com.alexroux.ntsalarmclock.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.commandiron.wheel_picker_compose.core.WheelPickerDefaults
import com.commandiron.wheel_picker_compose.core.WheelTextPicker

@Composable
fun CyclicTimePicker(
    hour: Int,
    minute: Int,
    onTimeChange: (Int, Int) -> Unit
) {
    // Create large cyclic lists so the wheel appears infinite
    val hours = remember { List(24 * 100) { it % 24 } }
    val minutes = remember { List(60 * 100) { it % 60 } }

    // Cache the displayed strings to avoid recreating large lists on every recomposition
    val hourTexts = remember { hours.map { it.toString().padStart(2, '0') } }
    val minuteTexts = remember { minutes.map { it.toString().padStart(2, '0') } }

    // Compute a base index near the middle to allow scrolling in both directions
    val baseHoursIndex = (hours.size / 2) - ((hours.size / 2) % 24)
    val baseMinutesIndex = (minutes.size / 2) - ((minutes.size / 2) % 60)

    val startHourIndex = remember(hour) { baseHoursIndex + hour }
    val startMinuteIndex = remember(minute) { baseMinutesIndex + minute }

    val pickerSize = DpSize(90.dp, 180.dp)
    val rowCount = 3
    val centerRowHeight = pickerSize.height / rowCount

    // Current values displayed by the UI
    var selectedHour by remember { mutableIntStateOf(hour) }
    var selectedMinute by remember { mutableIntStateOf(minute) }

    // Last values sent to the outside world to prevent duplicate updates
    var lastCommittedHour by remember { mutableIntStateOf(hour) }
    var lastCommittedMinute by remember { mutableIntStateOf(minute) }

    // Ignore the first callback emitted by the picker after initialization
    var ignoreHourCallback by remember { mutableStateOf(true) }
    var ignoreMinuteCallback by remember { mutableStateOf(true) }

    // Synchronize the local UI state when the external hour changes
    LaunchedEffect(hour) {
        selectedHour = hour
        lastCommittedHour = hour
        ignoreHourCallback = true
    }

    // Synchronize the local UI state when the external minute changes
    LaunchedEffect(minute) {
        selectedMinute = minute
        lastCommittedMinute = minute
        ignoreMinuteCallback = true
    }

    Box {
        Row(verticalAlignment = Alignment.CenterVertically) {
            key(startHourIndex) {
                WheelTextPicker(
                    size = pickerSize,
                    texts = hourTexts,
                    style = MaterialTheme.typography.headlineMedium,
                    rowCount = rowCount,
                    startIndex = startHourIndex,
                    selectorProperties = WheelPickerDefaults.selectorProperties(enabled = false)
                ) { snappedIndex ->
                    val newHour = hours[snappedIndex]
                    selectedHour = newHour

                    if (ignoreHourCallback) {
                        ignoreHourCallback = false
                        return@WheelTextPicker null
                    }

                    // Emit an update only if the effective time really changed
                    if (newHour != lastCommittedHour || selectedMinute != lastCommittedMinute) {
                        lastCommittedHour = newHour
                        lastCommittedMinute = selectedMinute
                        onTimeChange(newHour, selectedMinute)
                    }

                    null
                }
            }

            Box(
                modifier = Modifier
                    .width(0.dp)
                    .height(pickerSize.height),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = ":",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )
            }

            key(startMinuteIndex) {
                WheelTextPicker(
                    size = pickerSize,
                    texts = minuteTexts,
                    style = MaterialTheme.typography.headlineMedium,
                    rowCount = rowCount,
                    startIndex = startMinuteIndex,
                    selectorProperties = WheelPickerDefaults.selectorProperties(enabled = false)
                ) { snappedIndex ->
                    val newMinute = minutes[snappedIndex]
                    selectedMinute = newMinute

                    if (ignoreMinuteCallback) {
                        ignoreMinuteCallback = false
                        return@WheelTextPicker null
                    }

                    if (selectedHour != lastCommittedHour || newMinute != lastCommittedMinute) {
                        lastCommittedHour = selectedHour
                        lastCommittedMinute = newMinute
                        onTimeChange(selectedHour, newMinute)
                    }

                    null
                }
            }
        }

        // Overlay showing the selected row in the middle of the picker
        Box(
            modifier = Modifier
                .zIndex(1f)
                .width(pickerSize.width * 2)
                .height(centerRowHeight)
                .align(Alignment.Center)
                .border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(0.dp)
                )
        )
    }
}