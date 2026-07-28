package com.alexroux.ntsalarmclock.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.alexroux.ntsalarmclock.ui.theme.NTSAlarmClockTheme
import kotlin.math.ceil

/**
 * Simple reusable button styled to match the visual identity of the NTS app.
 *
 * This composable renders a rectangular button with:
 * - white background
 * - black bold text
 * - centered content
 *
 * The caller provides the button label, an optional [modifier],
 * and the click action.
 */
@Composable
fun NTSButton(
    text: String,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.headlineMedium,
    onClick: () -> Unit
) {
    val density = LocalDensity.current
    var widestLineWidthPx by remember(text, textStyle) { mutableIntStateOf(0) }

    Box(
        modifier = modifier
            .semantics {
                role = Role.Button
            }
            .clip(RectangleShape)
            .background(Color.White)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            modifier = if (widestLineWidthPx == 0) {
                Modifier
            } else {
                Modifier.width(with(density) { widestLineWidthPx.toDp() })
            },
            text = text,
            color = Color.Black,
            style = textStyle,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            onTextLayout = { layout ->
                val measuredWidth = layout.widestLineWidthPx()
                if (measuredWidth != widestLineWidthPx) {
                    widestLineWidthPx = measuredWidth
                }
            }
        )
    }
}

/** Keeps a wrapped button only as wide as its widest rendered line. */
private fun TextLayoutResult.widestLineWidthPx(): Int {
    val widestLine = (0 until lineCount).maxOfOrNull { lineIndex ->
        getLineRight(lineIndex) - getLineLeft(lineIndex)
    } ?: 0f

    return ceil(widestLine).toInt()
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, name = "NTS Button")
@Composable
private fun NTSButtonPreview() {
    NTSAlarmClockTheme {
        NTSButton(
            text = "SET ALARM",
            onClick = {}
        )
    }
}

@Preview(
    showBackground = true,
    backgroundColor = 0xFF000000,
    widthDp = 180,
    name = "NTS Button - Multiline"
)
@Composable
private fun NTSButtonMultilinePreview() {
    NTSAlarmClockTheme {
        NTSButton(
            text = "ALLOW NOTIFICATIONS",
            onClick = {}
        )
    }
}
