package com.example.ntsalarmclock.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.example.ntsalarmclock.R

val RobotoBold = FontFamily(
    Font(R.font.roboto_bold, FontWeight.Bold)
)

val Typography = Typography(
    displayLarge = TextStyle(fontFamily = RobotoBold),
    displayMedium = TextStyle(fontFamily = RobotoBold),
    displaySmall = TextStyle(fontFamily = RobotoBold),

    headlineLarge = TextStyle(fontFamily = RobotoBold),
    headlineMedium = TextStyle(fontFamily = RobotoBold),
    headlineSmall = TextStyle(fontFamily = RobotoBold),

    titleLarge = TextStyle(fontFamily = RobotoBold),
    titleMedium = TextStyle(fontFamily = RobotoBold),
    titleSmall = TextStyle(fontFamily = RobotoBold),

    bodyLarge = TextStyle(fontFamily = RobotoBold),
    bodyMedium = TextStyle(fontFamily = RobotoBold),
    bodySmall = TextStyle(fontFamily = RobotoBold),

    labelLarge = TextStyle(fontFamily = RobotoBold),
    labelMedium = TextStyle(fontFamily = RobotoBold),
    labelSmall = TextStyle(fontFamily = RobotoBold),
)