package com.yaris.hvfan.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// --- Slate & Carbon Precision Motorsport Palette ---
val DarkBackground = Color(0xFF090A0E)       // Deep Obsidian Matte (Anti-glare OLED)
val SurfaceDark = Color(0xFF11141C)          // Technical Slate Surface
val CardBackground = Color(0xFF151A24)       // Solid Modular Telemetry Panel
val CardBorder = Color(0xFF222B3D)           // 1px Hairline Technical Border
val CardBorderActive = Color(0xFFE11D48)     // Active Border Accent (No glow)

val GrRedPrimary = Color(0xFFE11D48)         // Matte Gazoo Racing Corsa Red
val GrRedDark = Color(0xFF9F1239)            // Deep Racing Crimson
val GrRedGlow = Color(0xFFE11D48)            // Solid Matte Red (Glow removed)

val AccentCyan = Color(0xFF38BDF8)           // Technical Sky/Cyan Telemetry (Non-neon)
val AccentBlue = Color(0xFF3B82F6)           // Precision Engineering Blue
val WarningOrange = Color(0xFFF59E0B)        // Solid Racing Amber
val DangerRed = Color(0xFFDC2626)            // Thermal Critical Red
val SuccessGreen = Color(0xFF10B981)         // Optimal Ready Emerald Green

val TextPrimary = Color(0xFFF8FAFC)          // Crisp High-Contrast Pure White
val TextSecondary = Color(0xFF94A3B8)        // Brushed Titanium / Slate Label
val TextMuted = Color(0xFF475569)            // Muted Engineering Accent
val DividerColor = Color(0xFF1E2638)         // Clean Sub-surface Divider

// --- Functional Motorsport Brushes (Subtle & Matte) ---
val GrSpeedGradient = Brush.verticalGradient(
    colors = listOf(Color(0xFFF8FAFC), Color(0xFFCBD5E1))
)

val GrCardBrush = Brush.linearGradient(
    colors = listOf(Color(0xFF151A24), Color(0xFF11141C))
)

val GrHeaderBrush = Brush.horizontalGradient(
    colors = listOf(Color(0xFF141822), Color(0xFF0F1218))
)

val GrRedAccentBrush = Brush.horizontalGradient(
    colors = listOf(Color(0xFFE11D48), Color(0xFFBE123C))
)

val GrCoolCyanBrush = Brush.horizontalGradient(
    colors = listOf(Color(0xFF38BDF8), Color(0xFF0284C7))
)
