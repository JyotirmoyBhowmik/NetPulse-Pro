package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Pure pitch-black OLED background
val CyberBlack = Color(0xFF000000)
val CyberSlate = Color(0xFF0A0A0C)
val CyberCharcoal = Color(0xFF121214)
val CyberCardGlow = Color(0xFF1E1E22)

// Premium neon feedback indicators
val CyberGreen = Color(0xFF00FF66)  // Normal/Optimal
val CyberCyan = Color(0xFF00E5FF)   // Streams/Download Speed
val CyberAmber = Color(0xFFFFB300)  // Warn Jitter/Pings
val CyberRed = Color(0xFFFF3333)    // Server/Dropped links

// Retrofit standard mappings to keep standard theme compiler happy
val Purple80 = CyberGreen
val PurpleGrey80 = CyberCyan
val Pink80 = CyberAmber

val Purple40 = CyberGreen
val PurpleGrey40 = CyberCyan
val Pink40 = CyberAmber
