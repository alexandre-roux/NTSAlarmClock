package com.example.ntsalarmclock.ui.screens.home.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import com.example.ntsalarmclock.R
import com.example.ntsalarmclock.ui.components.CyclicTimePicker

@Composable
fun HomeTimeSection(
    hour: Int,
    minute: Int,
    onTimeChange: (Int, Int) -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(R.string.time),
            style = MaterialTheme.typography.headlineLarge
        )

        CyclicTimePicker(
            hour = hour,
            minute = minute,
            onTimeChange = onTimeChange
        )
    }
}
