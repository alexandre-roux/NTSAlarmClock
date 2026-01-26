package com.example.ntsalarmclock.ui.screens.home.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.ntsalarmclock.R
import com.example.ntsalarmclock.ui.components.DayOfWeekUi
import com.example.ntsalarmclock.ui.components.DaysOfWeekRow

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

        Spacer(modifier = Modifier.height(16.dp))

        DaysOfWeekRow(
            selectedDays = selectedDays,
            onToggleDay = onToggleDay
        )
    }
}