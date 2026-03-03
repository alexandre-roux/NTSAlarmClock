package com.example.ntsalarmclock.ui.screens.ring

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun RingScreen(
    onDismiss: () -> Unit,
    viewModel: RingScreenViewModel = viewModel()
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Alarm ringing")

        Button(
            modifier = Modifier.padding(top = 24.dp),
            onClick = {
                viewModel.stopAlarm()
                onDismiss()
            }
        ) {
            Text(text = "Stop")
        }
    }
}