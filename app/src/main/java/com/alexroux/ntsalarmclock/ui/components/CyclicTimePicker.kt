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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.alexroux.ntsalarmclock.R
import com.commandiron.wheel_picker_compose.core.WheelPickerDefaults
import com.commandiron.wheel_picker_compose.core.WheelTextPicker

private const val HOURS_PER_DAY = 24
private const val MINUTES_PER_HOUR = 60
private const val CYCLE_REPETITIONS = 100
private const val VISIBLE_ROW_COUNT = 3
private val PICKER_SIZE = DpSize(90.dp, 180.dp)

private data class TimeSelection(val hour: Int, val minute: Int)

@Composable
fun CyclicTimePicker(
    hour: Int,
    minute: Int,
    onTimeChange: (Int, Int) -> Unit
) {
    var selectedHour by remember(hour) { mutableIntStateOf(hour) }
    var selectedMinute by remember(minute) { mutableIntStateOf(minute) }
    var lastCommittedTime by remember(hour, minute) {
        mutableStateOf(TimeSelection(hour, minute))
    }
    val alarmTimeDescription = stringResource(R.string.alarm_time)

    fun selectTime(newHour: Int, newMinute: Int) {
        selectedHour = newHour
        selectedMinute = newMinute

        val newTime = TimeSelection(newHour, newMinute)
        if (newTime != lastCommittedTime) {
            lastCommittedTime = newTime
            onTimeChange(newHour, newMinute)
        }
    }

    Box(
        modifier = Modifier.semantics {
            contentDescription = alarmTimeDescription
            stateDescription = formatTime(selectedHour, selectedMinute)
        }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CyclicNumberPicker(
                value = hour,
                valueCount = HOURS_PER_DAY,
                onValueChange = { newHour -> selectTime(newHour, selectedMinute) }
            )

            // A zero-width container places the colon between the two adjacent wheels.
            Box(
                modifier = Modifier
                    .width(0.dp)
                    .height(PICKER_SIZE.height),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = ":",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )
            }

            CyclicNumberPicker(
                value = minute,
                valueCount = MINUTES_PER_HOUR,
                onValueChange = { newMinute -> selectTime(selectedHour, newMinute) }
            )
        }

        val selectedRowHeight = PICKER_SIZE.height / VISIBLE_ROW_COUNT
        Box(
            modifier = Modifier
                .zIndex(1f)
                .width(PICKER_SIZE.width * 2)
                .height(selectedRowHeight)
                .align(Alignment.Center)
                .border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(0.dp)
                )
        )
    }
}

@Composable
private fun CyclicNumberPicker(
    value: Int,
    valueCount: Int,
    onValueChange: (Int) -> Unit
) {
    val values = remember(valueCount) {
        List(valueCount * CYCLE_REPETITIONS) { index -> index % valueCount }
    }
    val labels = remember(values) { values.map(::formatTwoDigits) }
    val startIndex = remember(value, values) {
        middleCycleStart(values.size, valueCount) + value
    }
    var ignoreInitialCallback by remember(value) { mutableStateOf(true) }

    key(startIndex) {
        WheelTextPicker(
            size = PICKER_SIZE,
            texts = labels,
            style = MaterialTheme.typography.headlineMedium,
            rowCount = VISIBLE_ROW_COUNT,
            startIndex = startIndex,
            selectorProperties = WheelPickerDefaults.selectorProperties(enabled = false)
        ) { snappedIndex ->
            if (ignoreInitialCallback) {
                ignoreInitialCallback = false
            } else {
                onValueChange(values[snappedIndex])
            }
            null
        }
    }
}

private fun middleCycleStart(itemCount: Int, valueCount: Int): Int {
    val middleIndex = itemCount / 2
    return middleIndex - (middleIndex % valueCount)
}

private fun formatTime(hour: Int, minute: Int): String {
    return "${formatTwoDigits(hour)}:${formatTwoDigits(minute)}"
}

private fun formatTwoDigits(value: Int): String = value.toString().padStart(2, '0')
