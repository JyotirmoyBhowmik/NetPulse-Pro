package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CyberpunkColorScheme = darkColorScheme(
    primary = CyberGreen,
    secondary = CyberCyan,
    tertiary = CyberAmber,
    background = CyberBlack,
    surface = CyberSlate,
    onBackground = Color.White,
    onSurface = Color.White,
    error = CyberRed,
    surfaceVariant = CyberCharcoal,
    onSurfaceVariant = Color.LightGray
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force dark cyberpunk look by default for absolute immersion
    dynamicColor: Boolean = false, // Disable default light system colors
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = CyberpunkColorScheme,
        typography = Typography,
        content = content
    )
}
