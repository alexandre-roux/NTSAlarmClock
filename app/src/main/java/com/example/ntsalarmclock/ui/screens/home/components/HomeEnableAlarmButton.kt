package com.example.ntsalarmclock.ui.screens.home.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.ntsalarmclock.R
import com.example.ntsalarmclock.ui.components.NTSButton

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