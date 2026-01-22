package com.example.ntsalarmclock.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ntsalarmclock.ui.theme.NTSAlarmClockTheme

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    vm: HomeScreenViewModel = viewModel()
) {
    HomeScreenContent(
        modifier = modifier,
        onSchedule0730 = { vm.schedule(hour = 7, minute = 30) },
        onCancel = { vm.cancel() }
    )
}

@Composable
private fun HomeScreenContent(
    modifier: Modifier = Modifier,
    onSchedule0730: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(16.dp)
    ) {
        Text("Feature 1: schedule alarm")

        Button(
            onClick = onSchedule0730,
            modifier = Modifier.padding(top = 12.dp)
        ) {
            Text("Schedule 07:30")
        }

        Button(
            onClick = onCancel,
            modifier = Modifier.padding(top = 12.dp)
        ) {
            Text("Cancel alarm")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    NTSAlarmClockTheme {
        Surface(
            color = MaterialTheme.colorScheme.background
        ) {
            HomeScreenContent(
                onSchedule0730 = {},
                onCancel = {}
            )
        }
    }
}
