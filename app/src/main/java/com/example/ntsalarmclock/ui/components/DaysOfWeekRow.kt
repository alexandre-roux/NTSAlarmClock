package com.example.ntsalarmclock.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun DaysOfWeekRow(
    selectedDays: Set<DayOfWeekUi>,
    onToggleDay: (DayOfWeekUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DayOfWeekUi.entries.forEach { day ->
            val isSelected = selectedDays.contains(day)
            DayButton(
                label = day.shortLabel,
                selected = isSelected,
                onClick = { onToggleDay(day) },
            )
        }
    }
}

@Composable
private fun DayButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RectangleShape

    val backgroundColor = if (selected) Color.White else Color.Black
    val textColor = if (selected) Color.Black else Color.White
    val border = if (selected) null else BorderStroke(1.dp, Color.White)

    Row(
        modifier = modifier
            .size(40.dp)
            .clip(shape)
            .background(backgroundColor)
            .then(
                if (border != null) Modifier.border(border, shape) else Modifier
            )
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = label,
            color = textColor,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}
