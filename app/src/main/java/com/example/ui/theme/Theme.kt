package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NetPulseLightColorScheme = lightColorScheme(
    primary = CyberGreen,
    secondary = CyberCyan,
    tertiary = CyberAmber,
    background = CyberBlack,
    surface = CyberSlate,
    onBackground = Color(0xFF0F172A), // Dark slate text/content
    onSurface = Color(0xFF0F172A),
    error = CyberRed,
    surfaceVariant = CyberCharcoal,
    onSurfaceVariant = Color(0xFF475569) // Stable slate gray
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false, // Always light theme for a crisp, high-end white-based layout
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = NetPulseLightColorScheme,
        typography = Typography,
        content = content
    )
}

