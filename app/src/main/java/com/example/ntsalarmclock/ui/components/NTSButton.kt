package com.example.ntsalarmclock.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Simple reusable button styled to match the visual identity of the NTS app.
 *
 * This composable renders a rectangular button with:
 * - white background
 * - black bold text
 * - centered content
 *
 * The caller provides the button label and the click action.
 */
@Composable
fun NTSButton(
    text: String,
    textStyle: TextStyle = MaterialTheme.typography.headlineMedium,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RectangleShape)
            .background(Color.White)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.Black,
            style = textStyle,
            fontWeight = FontWeight.Bold
        )
    }
}