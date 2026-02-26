package com.example.ntsalarmclock.ui.components

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
    val hours = remember { List(24 * 400) { it % 24 } }
    val minutes = remember { List(60 * 400) { it % 60 } }

    val baseHoursIndex = (hours.size / 2) - ((hours.size / 2) % 24)
    val baseMinutesIndex = (minutes.size / 2) - ((minutes.size / 2) % 60)

    val startHourIndex = remember(hour) { baseHoursIndex + hour }
    val startMinuteIndex = remember(minute) { baseMinutesIndex + minute }

    val pickerSize = DpSize(90.dp, 180.dp)
    val rowCount = 3
    val centerRowHeight = pickerSize.height / rowCount

    // What the UI currently displays
    var selectedHour by remember { mutableIntStateOf(hour) }
    var selectedMinute by remember { mutableIntStateOf(minute) }

    // Last value that we actually persisted (prevents duplicates)
    var lastCommittedHour by remember { mutableIntStateOf(hour) }
    var lastCommittedMinute by remember { mutableIntStateOf(minute) }

    // Ignore the first callback after (re)initialization / programmatic sync
    var ignoreNextHourCallback by remember { mutableStateOf(true) }
    var ignoreNextMinuteCallback by remember { mutableStateOf(true) }

    // Whenever the persisted value changes (DataStore), update what we display
    // and ignore the next callback that the picker library may emit.
    LaunchedEffect(hour) {
        selectedHour = hour
        lastCommittedHour = hour
        ignoreNextHourCallback = true
    }

    LaunchedEffect(minute) {
        selectedMinute = minute
        lastCommittedMinute = minute
        ignoreNextMinuteCallback = true
    }

    Box {
        Row(verticalAlignment = Alignment.CenterVertically) {

            // Key forces the picker to re-create when the startIndex changes,
            // ensuring it visually snaps to the persisted hour.
            key(startHourIndex) {
                WheelTextPicker(
                    size = pickerSize,
                    texts = hours.map { it.toString().padStart(2, '0') },
                    style = MaterialTheme.typography.headlineMedium,
                    rowCount = rowCount,
                    startIndex = startHourIndex,
                    selectorProperties = WheelPickerDefaults.selectorProperties(enabled = false)
                ) { snappedIndex ->
                    val newHour = hours[snappedIndex]
                    selectedHour = newHour

                    if (ignoreNextHourCallback) {
                        ignoreNextHourCallback = false
                        return@WheelTextPicker null
                    }

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

            // Same idea for minutes
            key(startMinuteIndex) {
                WheelTextPicker(
                    size = pickerSize,
                    texts = minutes.map { it.toString().padStart(2, '0') },
                    style = MaterialTheme.typography.headlineMedium,
                    rowCount = rowCount,
                    startIndex = startMinuteIndex,
                    selectorProperties = WheelPickerDefaults.selectorProperties(enabled = false)
                ) { snappedIndex ->
                    val newMinute = minutes[snappedIndex]
                    selectedMinute = newMinute

                    if (ignoreNextMinuteCallback) {
                        ignoreNextMinuteCallback = false
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