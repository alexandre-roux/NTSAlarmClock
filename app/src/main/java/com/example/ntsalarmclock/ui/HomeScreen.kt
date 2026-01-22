package com.example.ntsalarmclock.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ntsalarmclock.R
import com.example.ntsalarmclock.ui.theme.NTSAlarmClockTheme

@Composable
fun HomeScreen(
    viewModel: HomeScreenViewModel = viewModel()
) {
    HomeScreenContent()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreenContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            stringResource(R.string.activate_alarm),
            style = MaterialTheme.typography.headlineLarge
        )
        var checked by remember { mutableStateOf(true) }
        Switch(
            checked = checked,
            onCheckedChange = { checked = it }
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    NTSAlarmClockTheme {
        Surface(
            color = MaterialTheme.colorScheme.background
        ) {
            HomeScreenContent()
        }
    }
}
