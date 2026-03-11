package com.alexroux.ntsalarmclock.ui.screens.home.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.alexroux.ntsalarmclock.R
import com.alexroux.ntsalarmclock.ui.components.NTSButton

/**
 * Button displayed on the Home screen to enable or cancel the alarm.
 *
 * The label changes depending on the current alarm state:
 * - "Set alarm" when the alarm is disabled
 * - "Cancel alarm" when the alarm is enabled
 *
 * The click action is delegated to the ViewModel through [onClick].
 */
@Composable
fun HomeEnableAlarmButton(
    isAlarmEnabled: Boolean,
    onClick: () -> Unit
) {
    NTSButton(
        text = if (isAlarmEnabled) stringResource(R.string.cancel_alarm) else stringResource(R.string.set_alarm),
        onClick = onClick
    )
}