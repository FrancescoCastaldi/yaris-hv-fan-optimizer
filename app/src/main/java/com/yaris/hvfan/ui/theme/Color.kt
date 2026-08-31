package com.yaris.hvfan.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// --- Gazoo Racing Cyber-Motorsport Palette (v2.3.1) ---
val DarkBackground = Color(0xFF06080D)       // True Deep OLED Obsidian
val SurfaceDark = Color(0xFF0E121B)          // Titanium Dark Glass Surface
val CardBackground = Color(0xFF131824)       // Glassmorphic Layered Dark Slate
val CardBorder = Color(0xFF222B3D)           // Crisp Sub-surface Titanium Edge
val CardBorderActive = Color(0xFFFF1801)     // Neon Racing Red Glowing Edge

val GrRedPrimary = Color(0xFFFF1801)         // Official Gazoo Racing Corsa Red
val GrRedDark = Color(0xFFB30E00)            // Deep Racing Crimson
val GrRedGlow = Color(0xFFFF3B29)            // Vibrant Neon Red Glow

val AccentCyan = Color(0xFF00E5FF)           // Hyper-Cyan Telemetry Cool
val AccentBlue = Color(0xFF3D82F6)           // Dynamic Tech Blue
val WarningOrange = Color(0xFFFF9E0B)        // Amber Telemetry Warning
val DangerRed = Color(0xFFFF2A2A)            // Critical Thermal Red
val SuccessGreen = Color(0xFF00E676)         // Optimal Ready Neon Green

val TextPrimary = Color(0xFFFFFFFF)          // Pure Luminous White
val TextSecondary = Color(0xFF94A3B8)        // Brushed Silver Text
val TextMuted = Color(0xFF4C5B72)            // Subdued Technical Accent
val DividerColor = Color(0xFF1C2436)

// --- Gradient Brushes for Motorsport Elevation ---
val GrSpeedGradient = Brush.verticalGradient(
    colors = listOf(Color(0xFFFFFFFF), Color(0xFFFFE0DC), Color(0xFFFF2A2A))
)

val GrCardBrush = Brush.linearGradient(
    colors = listOf(Color(0xFF161D2D), Color(0xFF0C1018))
)

val GrHeaderBrush = Brush.horizontalGradient(
    colors = listOf(Color(0xFF182030), Color(0xFF0D121C))
)

val GrRedAccentBrush = Brush.horizontalGradient(
    colors = listOf(Color(0xFFFF1801), Color(0xFFFF4834))
)

val GrCoolCyanBrush = Brush.horizontalGradient(
    colors = listOf(Color(0xFF00E5FF), Color(0xFF0091EA))
)
