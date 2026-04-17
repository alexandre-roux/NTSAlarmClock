package com.alexroux.ntsalarmclock.ui.components

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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Row displaying the seven selectable days of the week.
 *
 * Each day is rendered as a square button that can be toggled on or off.
 * The parent screen owns the selected state and provides the current set
 * of selected days.
 */
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
        // Render one button for each day of the week.
        DayOfWeekUi.entries.forEach { day ->
            val isSelected = day in selectedDays

            DayButton(
                label = day.shortLabel,
                dayName = day.accessibilityLabel,
                selected = isSelected,
                onClick = { onToggleDay(day) },
            )
        }
    }
}

/**
 * Single square button representing one day of the week.
 *
 * The visual style changes depending on whether the day is selected:
 * - Selected: white background with black text
 * - Not selected: black background with white text and white border
 */
@Composable
private fun DayButton(
    label: String,
    dayName: String,
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
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = dayName
                stateDescription = if (selected) "Selected" else "Not selected"
            }
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

private val DayOfWeekUi.accessibilityLabel: String
    get() = when (this) {
        DayOfWeekUi.MO -> "Monday"
        DayOfWeekUi.TU -> "Tuesday"
        DayOfWeekUi.WE -> "Wednesday"
        DayOfWeekUi.TH -> "Thursday"
        DayOfWeekUi.FR -> "Friday"
        DayOfWeekUi.SA -> "Saturday"
        DayOfWeekUi.SU -> "Sunday"
    }