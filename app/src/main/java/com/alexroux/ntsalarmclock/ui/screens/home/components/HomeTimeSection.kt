package com.alexroux.ntsalarmclock.ui.screens.home.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alexroux.ntsalarmclock.R
import com.alexroux.ntsalarmclock.ui.components.CyclicTimePicker

/**
 * Section of the Home screen responsible for configuring the alarm time.
 *
 * It displays:
 * - a title ("Time")
 * - a cyclic time picker allowing the user to select hour and minute
 * - an optional informational text indicating when the alarm will trigger
 *   (for example "Alarm scheduled in 7h 32m").
 *
 * The selected time is controlled by the ViewModel and propagated
 * through the [hour], [minute], and [onTimeChange] parameters.
 */
@Composable
fun HomeTimeSection(
    hour: Int,
    minute: Int,
    scheduledInText: String,
    onTimeChange: (Int, Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.time),
            style = MaterialTheme.typography.headlineLarge
        )

        CyclicTimePicker(
            hour = hour,
            minute = minute,
            onTimeChange = onTimeChange
        )

        // Reserve vertical space so the layout does not jump when the text disappears.
        // Use fillMaxWidth to keep the Column width stable and avoid subtle horizontal shifting.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            if (scheduledInText.isNotBlank()) {
                Text(
                    text = scheduledInText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}