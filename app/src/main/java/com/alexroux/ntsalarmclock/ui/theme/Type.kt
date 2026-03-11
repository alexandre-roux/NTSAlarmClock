package com.alexroux.ntsalarmclock.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.alexroux.ntsalarmclock.R

// Roboto Bold as the default font for the app
val RobotoBold = FontFamily(
    Font(R.font.roboto_bold, FontWeight.Bold)
)

val Typography = Typography(

    // Display styles (very large titles)
    displayLarge = TextStyle(
        fontFamily = RobotoBold,
        fontSize = 57.sp,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontFamily = RobotoBold,
        fontSize = 45.sp,
        letterSpacing = 0.sp
    ),
    displaySmall = TextStyle(
        fontFamily = RobotoBold,
        fontSize = 36.sp,
        letterSpacing = 0.sp
    ),

    // Headline styles (screen titles)
    headlineLarge = TextStyle(
        fontFamily = RobotoBold,
        fontSize = 32.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = RobotoBold,
        fontSize = 28.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = RobotoBold,
        fontSize = 24.sp,
        letterSpacing = 0.sp
    ),

    // Title styles (section titles)
    titleLarge = TextStyle(
        fontFamily = RobotoBold,
        fontSize = 22.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = RobotoBold,
        fontSize = 16.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = RobotoBold,
        fontSize = 14.sp,
        letterSpacing = 0.1.sp
    ),

    // Body text
    bodyLarge = TextStyle(
        fontFamily = RobotoBold,
        fontSize = 16.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = RobotoBold,
        fontSize = 14.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = RobotoBold,
        fontSize = 12.sp,
        letterSpacing = 0.4.sp
    ),

    // Labels (buttons, switches, small UI text)
    labelLarge = TextStyle(
        fontFamily = RobotoBold,
        fontSize = 14.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = RobotoBold,
        fontSize = 12.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = RobotoBold,
        fontSize = 11.sp,
        letterSpacing = 0.5.sp
    )
)