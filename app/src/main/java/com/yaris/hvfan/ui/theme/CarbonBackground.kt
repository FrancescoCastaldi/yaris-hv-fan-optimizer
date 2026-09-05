package com.yaris.hvfan.ui.theme

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Shader
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush

/**
 * High-performance 2x2 Micro-Twill Carbon Fiber Background for Motorsport Cockpit.
 * Uses a hardware-accelerated 8x8 repeating BitmapShader combined with a soft OLED radial vignette.
 */
fun Modifier.carbonFiberBackground(): Modifier = composed {
    val carbonShaderBrush = remember {
        val size = 8
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)

        // Micro-Twill 2x2 Diagonal Pattern Matrix
        val c1 = android.graphics.Color.rgb(9, 11, 15)   // Deep base dark (#090B0F)
        val c2 = android.graphics.Color.rgb(20, 24, 34)  // Matte carbon weave highlight (#141822)
        val c3 = android.graphics.Color.rgb(14, 17, 24)  // Mid-tone graphite shadow (#0E1118)

        val pixels = IntArray(size * size) { index ->
            val x = index % size
            val y = index / size
            // 2x2 Twill diagonal wave
            val diagonal = (x + y) % 4
            val cross = (x - y + size) % 4
            if (diagonal < 2 && cross < 2) c2 else if (diagonal < 2) c3 else c1
        }
        bitmap.setPixels(pixels, 0, size, 0, 0, size, size)

        val shader = BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        ShaderBrush(shader)
    }

    this.drawBehind {
        // 1. Draw Repeating Hardware-Accelerated 2x2 Twill Carbon Weave
        drawRect(brush = carbonShaderBrush)

        // 2. Overlay Soft Vignette: slight center illumination, fading to deep dark at edges
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0x00000000),         // Transparent center to reveal carbon texture
                    Color(0x3505070A),         // Soft mid vignette
                    Color(0xB805070A)          // Dark OLED edge vignette
                ),
                center = Offset(size.width / 2f, size.height * 0.35f),
                radius = (size.width.coerceAtLeast(size.height)) * 0.85f
            )
        )
    }
}
