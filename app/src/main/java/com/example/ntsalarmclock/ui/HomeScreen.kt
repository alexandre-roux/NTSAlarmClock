package com.example.ntsalarmclock.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    vm: HomeScreenViewModel = viewModel()
) {
    Column(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(16.dp)
    ) {
        Text("Feature 1: schedule alarm")

        Button(
            onClick = { vm.schedule(hour = 7, minute = 30) },
            modifier = Modifier.padding(top = 12.dp)
        ) {
            Text("Schedule 07:30")
        }

        Button(
            onClick = { vm.cancel() },
            modifier = Modifier.padding(top = 12.dp)
        ) {
            Text("Cancel alarm")
        }
    }
}
