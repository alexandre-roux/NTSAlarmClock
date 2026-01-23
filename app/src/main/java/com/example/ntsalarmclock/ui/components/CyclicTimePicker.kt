package com.example.ntsalarmclock.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    onTimeChange: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
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

    Box(modifier = modifier) {

        Row(verticalAlignment = Alignment.CenterVertically) {

            WheelTextPicker(
                size = pickerSize,
                texts = hours.map { it.toString().padStart(2, '0') },
                style = MaterialTheme.typography.headlineMedium,
                rowCount = rowCount,
                startIndex = startHourIndex,
                selectorProperties = WheelPickerDefaults.selectorProperties(
                    enabled = true,
                    shape = RoundedCornerShape(0.dp),
                    color = Color.Transparent,
                    border = BorderStroke(0.dp, Color.Transparent)
                )
            ) { snappedIndex ->
                val newHour = hours[snappedIndex]
                onTimeChange(newHour, minute)
                null
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

            WheelTextPicker(
                size = pickerSize,
                texts = minutes.map { it.toString().padStart(2, '0') },
                style = MaterialTheme.typography.headlineMedium,
                rowCount = rowCount,
                startIndex = startMinuteIndex,
                selectorProperties = WheelPickerDefaults.selectorProperties(
                    enabled = true,
                    shape = RoundedCornerShape(0.dp),
                    color = Color.Transparent,
                    border = BorderStroke(0.dp, Color.Transparent)
                )
            ) { snappedIndex ->
                val newMinute = minutes[snappedIndex]
                onTimeChange(hour, newMinute)
                null
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
