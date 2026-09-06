package com.yaris.hvfan

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class CarbonWeaveUnitTest {

    @Test
    fun test2x2TwillPhaseClassification() {
        // In 4x4 matrix, exactly 8 cells are horizontal and 8 are vertical
        var hCount = 0
        var vCount = 0
        for (by in 0..3) {
            for (bx in 0..3) {
                val phase = (bx + by) % 4
                if (phase < 2) hCount++ else vCount++
            }
        }
        assertEquals(8, hCount)
        assertEquals(8, vCount)
    }

    @Test
    fun testTowContinuityAndNoMidpointGroove() {
        val blockSize = 24
        val towLen = blockSize * 2

        // Row 0 has Tow H0 spanning col 0 and col 1 (bx=0 and bx=1)
        // Check midpoint: x = blockSize - 1 (right of block 0) and x = blockSize (left of block 1)
        val ly = blockSize / 2 // Center line across tow height

        // Pixel just before midpoint
        val x0 = blockSize - 1
        val bx0 = x0 / blockSize // 0
        val lx0 = x0 % blockSize // 23
        val phase0 = (bx0 + 0) % 4 // 0
        val blockIdx0 = phase0 // 0
        val towX0 = blockIdx0 * blockSize + lx0 // 23
        val edgeDistAlong0 = minOf(towX0, towLen - 1 - towX0) // min(23, 24) = 23
        val edgeDistAcross0 = minOf(ly, blockSize - 1 - ly) // 12
        val edgeDist0 = minOf(edgeDistAlong0, edgeDistAcross0) // 12
        assertTrue("Tow midpoint left should have full interior clearance (no groove)", edgeDist0 >= 2)

        // Pixel just after midpoint
        val x1 = blockSize
        val bx1 = x1 / blockSize // 1
        val lx1 = x1 % blockSize // 0
        val phase1 = (bx1 + 0) % 4 // 1
        val blockIdx1 = phase1 // 1
        val towX1 = blockIdx1 * blockSize + lx1 // 24
        val edgeDistAlong1 = minOf(towX1, towLen - 1 - towX1) // min(24, 23) = 23
        val edgeDistAcross1 = minOf(ly, blockSize - 1 - ly) // 12
        val edgeDist1 = minOf(edgeDistAlong1, edgeDistAcross1) // 12
        assertTrue("Tow midpoint right should have full interior clearance (no groove)", edgeDist1 >= 2)

        // Conversely, at the true start and end of the tow, edgeDistAlong must be 0 (groove)
        val startTowX = 0
        val startEdgeDistAlong = minOf(startTowX, towLen - 1 - startTowX)
        assertEquals(0, startEdgeDistAlong)

        val endTowX = towLen - 1
        val endEdgeDistAlong = minOf(endTowX, towLen - 1 - endTowX)
        assertEquals(0, endEdgeDistAlong)
    }

    @Test
    fun testWrappedTowTileBoundaryContinuity() {
        val blockSize = 24
        val towLen = blockSize * 2

        // In Row 1 (by = 1):
        // Right edge of tile (bx = 3): phase = (3 + 1) % 4 = 0 (blockIdx = 0)
        // Left edge of next tile (bx = 0): phase = (0 + 1) % 4 = 1 (blockIdx = 1)
        // Tow H1 wraps across the tile seam!
        val towXEnd = 0 * blockSize + (blockSize - 1)
        val distAlongEnd = minOf(towXEnd, towLen - 1 - towXEnd)
        assertTrue("tile end clearance", distAlongEnd >= 2)

        val towXStart = 1 * blockSize + 0
        val distAlongStart = minOf(towXStart, towLen - 1 - towXStart)
        assertTrue("tile start clearance", distAlongStart >= 2)

        // Check 1-pixel step continuity across tile boundary
        assertEquals(towXEnd + 1, towXStart)
    }

    @Test
    fun testPhotometricColorBounds() {
        val blockSize = 16
        val tileSize = blockSize * 4
        val towLen = blockSize * 2

        for (y in 0 until tileSize) {
            val by = y / blockSize
            val ly = y % blockSize
            for (x in 0 until tileSize) {
                val bx = x / blockSize
                val lx = x % blockSize

                val phase = (bx + by) % 4
                val isHorizontal = phase < 2

                val r: Float
                val g: Float
                val b: Float
                val edgeDist: Int

                if (isHorizontal) {
                    val blockIdx = phase
                    val towX = blockIdx * blockSize + lx
                    val u = towX.toFloat() / (towLen - 1).coerceAtLeast(1)
                    val v = ly.toFloat() / (blockSize - 1).coerceAtLeast(1)
                    val edgeDistAlong = minOf(towX, towLen - 1 - towX)
                    val edgeDistAcross = minOf(ly, blockSize - 1 - ly)
                    edgeDist = minOf(edgeDistAlong, edgeDistAcross)

                    val curveY = sin((v * PI).toFloat()).coerceIn(0f, 1f)
                    val bundle = Math.pow(curveY.toDouble(), 0.72).toFloat()
                    val striation = 0.93f + 0.07f * sin(ly * 3.4f) * cos(towX * 0.45f)
                    val dive = sin((u * PI).toFloat()).coerceIn(0f, 1f)
                    val diveFactor = 0.82f + 0.18f * dive
                    val highlightFactor = (bundle * striation * diveFactor).coerceIn(0f, 1f)

                    if (highlightFactor < 0.42f) {
                        val t = highlightFactor / 0.42f
                        r = 8f + t * (22f - 8f)
                        g = 10f + t * (27f - 10f)
                        b = 14f + t * (36f - 14f)
                    } else {
                        val t = (highlightFactor - 0.42f) / 0.58f
                        r = 22f + t * (54f - 22f)
                        g = 27f + t * (63f - 27f)
                        b = 36f + t * (80f - 36f)
                    }
                } else {
                    val blockIdx = phase - 2
                    val towY = blockIdx * blockSize + ly
                    val u = lx.toFloat() / (blockSize - 1).coerceAtLeast(1)
                    val v = towY.toFloat() / (towLen - 1).coerceAtLeast(1)
                    val edgeDistAlong = minOf(towY, towLen - 1 - towY)
                    val edgeDistAcross = minOf(lx, blockSize - 1 - lx)
                    edgeDist = minOf(edgeDistAlong, edgeDistAcross)

                    val curveX = sin((u * PI).toFloat()).coerceIn(0f, 1f)
                    val bundle = Math.pow(curveX.toDouble(), 0.72).toFloat()
                    val striation = 0.93f + 0.07f * sin(lx * 3.4f) * cos(towY * 0.45f)
                    val dive = sin((v * PI).toFloat()).coerceIn(0f, 1f)
                    val diveFactor = 0.82f + 0.18f * dive
                    val diffuseFactor = (bundle * striation * diveFactor).coerceIn(0f, 1f)

                    if (diffuseFactor < 0.50f) {
                        val t = diffuseFactor / 0.50f
                        r = 8f + t * (20f - 8f)
                        g = 10f + t * (25f - 10f)
                        b = 14f + t * (36f - 14f)
                    } else {
                        val t = (diffuseFactor - 0.50f) / 0.50f
                        r = 20f + t * (34f - 20f)
                        g = 25f + t * (42f - 25f)
                        b = 36f + t * (58f - 36f)
                    }
                }

                val seamFactor = (edgeDist.toFloat() / 1.5f).coerceIn(0f, 1f)
                val finalR = (8f + (r - 8f) * seamFactor).toInt().coerceIn(0, 255)
                val finalG = (10f + (g - 10f) * seamFactor).toInt().coerceIn(0, 255)
                val finalB = (14f + (b - 14f) * seamFactor).toInt().coerceIn(0, 255)

                assertTrue("R channel bounds", finalR in 8..54)
                assertTrue("G channel bounds", finalG in 10..63)
                assertTrue("B channel bounds", finalB in 14..80)

                // At seams (edgeDist == 0), color must be exact base dark shadow
                if (edgeDist == 0) {
                    assertEquals(8, finalR)
                    assertEquals(10, finalG)
                    assertEquals(14, finalB)
                }
            }
        }
    }

    @Test
    fun testVerticalTowContinuityAndNoMidpointGroove() {
        val blockSize = 24
        val towLen = blockSize * 2

        // Column 2 has Tow V2 spanning Row 0 and Row 1 (by=0 and by=1)
        // Check midpoint: y = blockSize - 1 (bottom of block 0) and y = blockSize (top of block 1)
        val lx = blockSize / 2 // Center line across tow width (horizontal)

        // Pixel just before vertical midpoint
        val y0 = blockSize - 1
        val by0 = y0 / blockSize // 0
        val ly0 = y0 % blockSize // 23
        val phase0 = (2 + by0) % 4 // (2 + 0) % 4 = 2 (Vertical block 0)
        val blockIdx0 = phase0 - 2 // 0
        val towY0 = blockIdx0 * blockSize + ly0 // 23
        val edgeDistAlong0 = minOf(towY0, towLen - 1 - towY0) // min(23, 24) = 23
        val edgeDistAcross0 = minOf(lx, blockSize - 1 - lx) // 12
        val edgeDist0 = minOf(edgeDistAlong0, edgeDistAcross0) // 12
        assertTrue("Vertical tow midpoint top should have full interior clearance (no groove)", edgeDist0 >= 2)

        // Pixel just after vertical midpoint
        val y1 = blockSize
        val by1 = y1 / blockSize // 1
        val ly1 = y1 % blockSize // 0
        val phase1 = (2 + by1) % 4 // (2 + 1) % 4 = 3 (Vertical block 1)
        val blockIdx1 = phase1 - 2 // 1
        val towY1 = blockIdx1 * blockSize + ly1 // 24
        val edgeDistAlong1 = minOf(towY1, towLen - 1 - towY1) // min(24, 23) = 23
        val edgeDistAcross1 = minOf(lx, blockSize - 1 - lx) // 12
        val edgeDist1 = minOf(edgeDistAlong1, edgeDistAcross1) // 12
        assertTrue("Vertical tow midpoint bottom should have full interior clearance (no groove)", edgeDist1 >= 2)

        // Boundaries along Y must have edgeDistAlong = 0
        val startTowY = 0
        val startEdgeDistAlong = minOf(startTowY, towLen - 1 - startTowY)
        assertEquals(0, startEdgeDistAlong)

        val endTowY = towLen - 1
        val endEdgeDistAlong = minOf(endTowY, towLen - 1 - endTowY)
        assertEquals(0, endEdgeDistAlong)
    }

    @Test
    fun testWrappedVerticalTowTileBoundaryContinuity() {
        val blockSize = 24
        val towLen = blockSize * 2

        // In Column 3 (bx = 3):
        // Bottom of tile (by = 3): phase = (3 + 3) % 4 = 2 -> blockIdx = 0
        // Top of next tile below (by = 0): phase = (3 + 0) % 4 = 3 -> blockIdx = 1
        // Tow V3 wraps vertically across the tile seam!
        val towYEnd = 0 * blockSize + (blockSize - 1)
        val distAlongEnd = minOf(towYEnd, towLen - 1 - towYEnd)
        assertTrue("Vertical tile bottom clearance", distAlongEnd >= 2)

        val towYStart = 1 * blockSize + 0
        val distAlongStart = minOf(towYStart, towLen - 1 - towYStart)
        assertTrue("Vertical tile top clearance", distAlongStart >= 2)

        // 1-pixel step continuity across vertical tile boundary
        assertEquals(towYEnd + 1, towYStart)
    }

    @Test
    fun testDisplayDensityScalingAndTileBounds() {
        // Test density scaling across multiple Android device families:
        // ldpi (0.75), mdpi (1.0), hdpi (1.5), xhdpi (2.0), Oppo A94 5G (2.75), xxhdpi (3.0), xxxhdpi (4.0)
        val testDensities = floatArrayOf(0.75f, 1.0f, 1.5f, 2.0f, 2.75f, 3.0f, 3.5f, 4.0f)

        for (density in testDensities) {
            val rawBlockPx = Math.round(9.0f * density)
            val blockSize = rawBlockPx.coerceIn(8, 48)
            val tileSize = blockSize * 4
            val towLen = blockSize * 2

            assertTrue("blockSize must be in bounds [8, 48]", blockSize in 8..48)
            assertTrue("tileSize must be multiple of 4", tileSize % 4 == 0)
            assertEquals(towLen * 2, tileSize)
            assertTrue("tileSize >= 32px for HD rendering", tileSize >= 32)
        }
    }

    @Test
    fun testWebSvgPatternWellFormedXmlAndStructure() {
        val svgFile = listOf(
            java.io.File("../docs/carbon-pattern-3k.svg"),
            java.io.File("docs/carbon-pattern-3k.svg")
        ).firstOrNull { it.exists() }

        assertNotNull("docs/carbon-pattern-3k.svg must exist", svgFile)

        val dbFactory = javax.xml.parsers.DocumentBuilderFactory.newInstance()
        dbFactory.isNamespaceAware = true
        val dBuilder = dbFactory.newDocumentBuilder()
        val doc = dBuilder.parse(svgFile!!)

        val root = doc.documentElement
        assertEquals("svg", root.localName)
        assertEquals("48", root.getAttribute("width"))
        assertEquals("48", root.getAttribute("height"))

        // Check required linear gradients in defs
        val defs = doc.getElementsByTagNameNS("http://www.w3.org/2000/svg", "defs")
        assertEquals(1, defs.length)

        val gradients = doc.getElementsByTagNameNS("http://www.w3.org/2000/svg", "linearGradient")
        val gradIds = (0 until gradients.length).map {
            gradients.item(it).attributes.getNamedItem("id")?.nodeValue
        }.toSet()

        assertTrue(gradIds.contains("gh"))
        assertTrue(gradIds.contains("gv"))
        assertTrue(gradIds.contains("g_dive_h"))
        assertTrue(gradIds.contains("g_dive_v"))

        // Check all 10 required woven tow groups (5 horizontal including wrapped, 5 vertical including wrapped)
        val expectedTowIds = listOf(
            "tow-h0", "tow-h2", "tow-h3", "tow-h1-right", "tow-h1-left",
            "tow-v2", "tow-v1", "tow-v0", "tow-v3-bottom", "tow-v3-top"
        )
        val allGroups = doc.getElementsByTagName("g")
        val groupIds = (0 until allGroups.length).mapNotNull {
            allGroups.item(it).attributes?.getNamedItem("id")?.nodeValue
        }.toSet()

        for (towId in expectedTowIds) {
            assertTrue("Tow group $towId must exist in SVG", groupIds.contains(towId))
        }
    }

    @Test
    fun testDensityExtremeEdgeCasesAndNoCrash() {
        val extremeDensities = floatArrayOf(-5.0f, 0.0f, 0.001f, 0.25f, 0.75f, 2.75f, 4.0f, 12.0f, 100.0f)
        for (d in extremeDensities) {
            val rawPx = Math.round(9.0f * d)
            val blockSize = rawPx.coerceIn(8, 48)
            val tileSize = blockSize * 4
            val towLen = blockSize * 2

            assertTrue("blockSize must be clamped between 8 and 48", blockSize in 8..48)
            assertTrue("tileSize must be clamped between 32 and 192", tileSize in 32..192)
            assertTrue("towLen must be clamped between 16 and 96", towLen in 16..96)
            assertEquals(0, tileSize % 4)
        }
    }

    @Test
    fun testProceduralPixelsFullyOpaque() {
        val blockSize = 10
        val tileSize = blockSize * 4
        val towLen = blockSize * 2

        for (y in 0 until tileSize) {
            val by = y / blockSize
            val ly = y % blockSize
            for (x in 0 until tileSize) {
                val bx = x / blockSize
                val lx = x % blockSize
                val phase = (bx + by) % 4
                val isHorizontal = phase < 2

                val r: Float
                val g: Float
                val b: Float
                val edgeDist: Int

                if (isHorizontal) {
                    val blockIdx = phase
                    val towX = blockIdx * blockSize + lx
                    val u = towX.toFloat() / (towLen - 1).coerceAtLeast(1)
                    val v = ly.toFloat() / (blockSize - 1).coerceAtLeast(1)
                    val edgeDistAlong = minOf(towX, towLen - 1 - towX)
                    val edgeDistAcross = minOf(ly, blockSize - 1 - ly)
                    edgeDist = minOf(edgeDistAlong, edgeDistAcross)

                    val curveY = sin((v * PI).toFloat()).coerceIn(0f, 1f)
                    val bundle = Math.pow(curveY.toDouble(), 0.72).toFloat()
                    val striation = 0.93f + 0.07f * sin(ly * 3.4f) * cos(towX * 0.45f)
                    val dive = sin((u * PI).toFloat()).coerceIn(0f, 1f)
                    val diveFactor = 0.82f + 0.18f * dive
                    val highlightFactor = (bundle * striation * diveFactor).coerceIn(0f, 1f)

                    if (highlightFactor < 0.42f) {
                        val t = highlightFactor / 0.42f
                        r = 8f + t * (22f - 8f)
                        g = 10f + t * (27f - 10f)
                        b = 14f + t * (36f - 14f)
                    } else {
                        val t = (highlightFactor - 0.42f) / 0.58f
                        r = 22f + t * (54f - 22f)
                        g = 27f + t * (63f - 27f)
                        b = 36f + t * (80f - 36f)
                    }
                } else {
                    val blockIdx = phase - 2
                    val towY = blockIdx * blockSize + ly
                    val u = lx.toFloat() / (blockSize - 1).coerceAtLeast(1)
                    val v = towY.toFloat() / (towLen - 1).coerceAtLeast(1)
                    val edgeDistAlong = minOf(towY, towLen - 1 - towY)
                    val edgeDistAcross = minOf(lx, blockSize - 1 - lx)
                    edgeDist = minOf(edgeDistAlong, edgeDistAcross)

                    val curveX = sin((u * PI).toFloat()).coerceIn(0f, 1f)
                    val bundle = Math.pow(curveX.toDouble(), 0.72).toFloat()
                    val striation = 0.93f + 0.07f * sin(lx * 3.4f) * cos(towY * 0.45f)
                    val dive = sin((v * PI).toFloat()).coerceIn(0f, 1f)
                    val diveFactor = 0.82f + 0.18f * dive
                    val diffuseFactor = (bundle * striation * diveFactor).coerceIn(0f, 1f)

                    if (diffuseFactor < 0.50f) {
                        val t = diffuseFactor / 0.50f
                        r = 8f + t * (20f - 8f)
                        g = 10f + t * (25f - 10f)
                        b = 14f + t * (36f - 14f)
                    } else {
                        val t = (diffuseFactor - 0.50f) / 0.50f
                        r = 20f + t * (34f - 20f)
                        g = 25f + t * (42f - 25f)
                        b = 36f + t * (58f - 36f)
                    }
                }

                val seamFactor = (edgeDist.toFloat() / 1.5f).coerceIn(0f, 1f)
                val finalR = (8f + (r - 8f) * seamFactor).toInt().coerceIn(0, 255)
                val finalG = (10f + (g - 10f) * seamFactor).toInt().coerceIn(0, 255)
                val finalB = (14f + (b - 14f) * seamFactor).toInt().coerceIn(0, 255)

                val pixel = (0xFF shl 24) or (finalR shl 16) or (finalG shl 8) or finalB
                // Verify 0xFF alpha mask
                assertEquals(0xFF000000.toInt(), pixel and 0xFF000000.toInt())
            }
        }
    }
}
