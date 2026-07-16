package com.alexroux.ntsalarmclock.ui.components

import androidx.annotation.StringRes
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alexroux.ntsalarmclock.R
import java.time.DayOfWeek

/**
 * Row displaying the seven selectable days of the week.
 *
 * Each day is rendered as a square button that can be toggled on or off.
 * The parent screen owns the selected state and provides the current set
 * of selected days.
 */
@Composable
fun DaysOfWeekRow(
    selectedDays: Set<DayOfWeek>,
    onToggleDay: (DayOfWeek) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DayOfWeek.entries.forEach { day ->
            val isSelected = day in selectedDays
            val labels = day.labels

            DayButton(
                label = stringResource(labels.shortName),
                dayName = stringResource(labels.fullName),
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
    val selectedDescription = stringResource(R.string.day_selected)
    val notSelectedDescription = stringResource(R.string.day_not_selected)

    Row(
        modifier = modifier
            .size(40.dp)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = dayName
                stateDescription = if (selected) selectedDescription else notSelectedDescription
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

private data class DayLabels(
    @param:StringRes val shortName: Int,
    @param:StringRes val fullName: Int
)

private val DayOfWeek.labels: DayLabels
    get() = when (this) {
        DayOfWeek.MONDAY -> DayLabels(R.string.monday_short, R.string.monday)
        DayOfWeek.TUESDAY -> DayLabels(R.string.tuesday_short, R.string.tuesday)
        DayOfWeek.WEDNESDAY -> DayLabels(R.string.wednesday_short, R.string.wednesday)
        DayOfWeek.THURSDAY -> DayLabels(R.string.thursday_short, R.string.thursday)
        DayOfWeek.FRIDAY -> DayLabels(R.string.friday_short, R.string.friday)
        DayOfWeek.SATURDAY -> DayLabels(R.string.saturday_short, R.string.saturday)
        DayOfWeek.SUNDAY -> DayLabels(R.string.sunday_short, R.string.sunday)
    }
