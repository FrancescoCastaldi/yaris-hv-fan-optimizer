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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * High-performance 3K Motorsport High-Definition 2x2 Twill Carbon Fiber Background.
 * Procedurally generated and density-scaled for modern high-PPI AMOLED displays (400+ PPI, e.g. Oppo A94 5G).
 * Features photometric separation of horizontal titanium/graphite specular highlights and vertical
 * mid-tone graphite tows with deep carbon shadow grooves and hardware-accelerated radial vignette.
 */
fun Modifier.carbonFiberBackground(): Modifier = composed {
    val density = LocalDensity.current

    val carbonShaderBrush = remember(density.density) {
        // Tile scaled to 36dp equivalent: each tow bundle is 9dp
        // On Oppo A94 5G (~409 PPI, density ~2.75x): blockSize ~25px -> tileSize = 100px
        // On 3.0x (xxhdpi 1080p): blockSize = 27px -> tileSize = 108px
        val rawBlockPx = with(density) { 9.dp.roundToPx() }
        val blockSize = rawBlockPx.coerceIn(8, 48)
        val tileSize = blockSize * 4
        val towLen = blockSize * 2
        val bitmap = Bitmap.createBitmap(tileSize, tileSize, Bitmap.Config.ARGB_8888)

        val pixels = IntArray(tileSize * tileSize)

        for (y in 0 until tileSize) {
            val by = y / blockSize
            val ly = y % blockSize
            val rowOffset = y * tileSize

            for (x in 0 until tileSize) {
                val bx = x / blockSize
                val lx = x % blockSize

                // 2x2 Twill Weave 4x4 Phase:
                // phase = (bx + by) % 4
                // phase 0: Horizontal tow block 0 (Row 0 col 0, Row 1 col 3, Row 2 col 2, Row 3 col 1)
                // phase 1: Horizontal tow block 1 (Row 0 col 1, Row 1 col 0, Row 2 col 3, Row 3 col 2)
                // phase 2: Vertical tow block 0 (Col 2 row 0, Col 1 row 1, Col 0 row 2, Col 3 row 3)
                // phase 3: Vertical tow block 1 (Col 2 row 1, Col 1 row 2, Col 0 row 3, Col 3 row 0)
                val phase = (bx + by) % 4
                val isHorizontal = phase < 2

                val r: Float
                val g: Float
                val b: Float
                val edgeDist: Int

                if (isHorizontal) {
                    val blockIdx = phase // 0 or 1
                    val towX = blockIdx * blockSize + lx

                    val u = towX.toFloat() / (towLen - 1).coerceAtLeast(1)
                    val v = ly.toFloat() / (blockSize - 1).coerceAtLeast(1)

                    val edgeDistAlong = minOf(towX, towLen - 1 - towX)
                    val edgeDistAcross = minOf(ly, blockSize - 1 - ly)
                    edgeDist = minOf(edgeDistAlong, edgeDistAcross)

                    // --- Horizontal Tow (Titanium / Graphite Specular Reflection) ---
                    // Convex cylindrical bundle profile across Y
                    val curveY = sin((v * PI).toFloat()).coerceIn(0f, 1f)
                    val bundle = Math.pow(curveY.toDouble(), 0.72).toFloat()
                    // Micro-filaments along fiber length (X)
                    val striation = 0.93f + 0.07f * sin(ly * 3.4f) * cos(towX * 0.45f)
                    // Interlacing dive shadow only at bundle ends (X)
                    val dive = sin((u * PI).toFloat()).coerceIn(0f, 1f)
                    val diveFactor = 0.82f + 0.18f * dive
                    val highlightFactor = (bundle * striation * diveFactor).coerceIn(0f, 1f)

                    if (highlightFactor < 0.42f) {
                        // Shadow (#080A0E) to Graphite Midtone (#161B24)
                        val t = highlightFactor / 0.42f
                        r = 8f + t * (22f - 8f)
                        g = 10f + t * (27f - 10f)
                        b = 14f + t * (36f - 14f)
                    } else {
                        // Midtone (#161B24) to Titanium Specular Highlight (#363F50 / #2A303E)
                        val t = (highlightFactor - 0.42f) / 0.58f
                        r = 22f + t * (54f - 22f)
                        g = 27f + t * (63f - 27f)
                        b = 36f + t * (80f - 36f)
                    }
                } else {
                    val blockIdx = phase - 2 // 0 or 1
                    val towY = blockIdx * blockSize + ly

                    val u = lx.toFloat() / (blockSize - 1).coerceAtLeast(1)
                    val v = towY.toFloat() / (towLen - 1).coerceAtLeast(1)

                    val edgeDistAlong = minOf(towY, towLen - 1 - towY)
                    val edgeDistAcross = minOf(lx, blockSize - 1 - lx)
                    edgeDist = minOf(edgeDistAlong, edgeDistAcross)

                    // --- Vertical Tow (Graphite Mid-Tone Reflection) ---
                    // Convex cylindrical bundle profile across X
                    val curveX = sin((u * PI).toFloat()).coerceIn(0f, 1f)
                    val bundle = Math.pow(curveX.toDouble(), 0.72).toFloat()
                    // Micro-filaments along fiber length (Y)
                    val striation = 0.93f + 0.07f * sin(lx * 3.4f) * cos(towY * 0.45f)
                    // Interlacing dive shadow only at bundle ends (Y)
                    val dive = sin((v * PI).toFloat()).coerceIn(0f, 1f)
                    val diveFactor = 0.82f + 0.18f * dive
                    val diffuseFactor = (bundle * striation * diveFactor).coerceIn(0f, 1f)

                    if (diffuseFactor < 0.50f) {
                        // Shadow (#080A0E) to Technical Slate (#141924)
                        val t = diffuseFactor / 0.50f
                        r = 8f + t * (20f - 8f)
                        g = 10f + t * (25f - 10f)
                        b = 14f + t * (36f - 14f)
                    } else {
                        // Technical Slate (#141924) to Graphite Weave Highlight (#222A3A)
                        val t = (diffuseFactor - 0.50f) / 0.50f
                        r = 20f + t * (34f - 20f)
                        g = 25f + t * (42f - 25f)
                        b = 36f + t * (58f - 36f)
                    }
                }

                // Smoothly engrave pure carbon shadow groove (#080A0E) at tow seams
                val seamFactor = (edgeDist.toFloat() / 1.5f).coerceIn(0f, 1f)
                val finalR = (8f + (r - 8f) * seamFactor).toInt().coerceIn(0, 255)
                val finalG = (10f + (g - 10f) * seamFactor).toInt().coerceIn(0, 255)
                val finalB = (14f + (b - 14f) * seamFactor).toInt().coerceIn(0, 255)

                pixels[rowOffset + x] = (0xFF shl 24) or (finalR shl 16) or (finalG shl 8) or finalB
            }
        }

        bitmap.setPixels(pixels, 0, tileSize, 0, 0, tileSize, tileSize)
        val shader = BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        ShaderBrush(shader)
    }

    val vignetteBrushCache = remember {
        object {
            var lastWidth = -1f
            var lastHeight = -1f
            var brush: Brush? = null

            fun getBrush(width: Float, height: Float): Brush {
                val currentBrush = brush
                if (currentBrush != null && width == lastWidth && height == lastHeight) {
                    return currentBrush
                }
                lastWidth = width
                lastHeight = height
                val safeRadius = (maxOf(width, height) * 0.85f).coerceAtLeast(1f)
                val newBrush = Brush.radialGradient(
                    colors = VignetteColors,
                    center = Offset(width / 2f, height * 0.35f),
                    radius = safeRadius
                )
                brush = newBrush
                return newBrush
            }
        }
    }

    this.drawBehind {
        if (size.width <= 0f || size.height <= 0f) return@drawBehind

        // 1. Draw Repeating Hardware-Accelerated 3K 2x2 Twill Carbon Weave
        drawRect(brush = carbonShaderBrush)

        // 2. Soft Motorsport Cockpit Radial Vignette
        // Cached brush prevents allocations and GC churn during 60/120fps dashboard scroll
        drawRect(brush = vignetteBrushCache.getBrush(size.width, size.height))
    }
}

private val VignetteColors = listOf(
    Color(0x00000000), // Pure transparent center to reveal 3K carbon weave
    Color(0x1805070A), // Soft transitional mid vignette
    Color(0x6005070A)  // Soft OLED edge vignette (preserves rich 3K carbon texture)
)

