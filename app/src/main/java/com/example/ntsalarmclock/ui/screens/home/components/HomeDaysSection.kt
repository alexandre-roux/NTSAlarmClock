package com.example.ntsalarmclock.ui.screens.home.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import com.example.ntsalarmclock.R
import com.example.ntsalarmclock.ui.components.DayOfWeekUi
import com.example.ntsalarmclock.ui.components.DaysOfWeekRow

/**
 * Section of the Home screen that lets the user choose
 * on which days the alarm should repeat.
 *
 * Displays a title ("Days") and a row of selectable day buttons.
 * The selection state is controlled by the ViewModel via [selectedDays].
 */
@Composable
fun HomeDaysSection(
    selectedDays: Set<DayOfWeekUi>,
    onToggleDay: (DayOfWeekUi) -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(R.string.days),
            style = MaterialTheme.typography.headlineLarge
        )

        DaysOfWeekRow(
            selectedDays = selectedDays,
            onToggleDay = onToggleDay
        )
    }
}