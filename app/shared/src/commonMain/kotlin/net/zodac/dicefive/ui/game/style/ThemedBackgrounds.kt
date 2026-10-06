package net.zodac.dicefive.ui.game.style

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

/** A brick wall - to go with the Neon dice - lit faintly from above by a sign in [BackgroundPalette.detail]. */
class BrickWallBackground(override val id: String, private val palette: BackgroundPalette) : TableBackground {
    override val scoreAreaBrush: Brush = Brush.verticalGradient(listOf(palette.top, palette.bottom))

    override fun DrawScope.drawScoreAreaDecoration() {
        drawCachedSurface("background:$id") {
            paintBricks(Color.Transparent, Color.Black.copy(alpha = 0.4f), Color.White.copy(alpha = 0.07f), seed = 6, rowHeight = 16.dp.toPx())
            val glow = Offset(size.width * 0.5f, -size.height * 0.05f)
            drawCircle(Brush.radialGradient(listOf(palette.detail.copy(alpha = 0.16f), Color.Transparent), center = glow, radius = size.width * 0.9f), size.width * 0.9f, glow)
        }
    }
}

// The Glitch background's rare jolt: one burst every GLITCH_BACKGROUND_WINDOW seconds, a couple of slots long.
private const val GLITCH_BACKGROUND_WINDOW = 5f

/**
 * An old monitor's screen - to go with the Glitch dice: fine scanlines, a faint bar of brighter
 * signal rolling slowly down it, and, every few seconds, a jolt of a couple of torn bands of colour
 * for a moment. Still under reduced motion.
 */
class GlitchBackground(override val id: String, private val palette: BackgroundPalette) : TableBackground {
    override val scoreAreaBrush: Brush = Brush.verticalGradient(listOf(palette.top, palette.bottom))

    private var seconds by mutableFloatStateOf(0f)

    @Composable
    override fun Animate() {
        TwinkleClock { seconds = it }
    }

    override fun DrawScope.drawScoreAreaDecoration() {
        drawCachedSurface("background:$id") {
            val pitch = 3.dp.toPx()
            var y = 0f
            while (y < size.height) {
                drawRect(Color.Black.copy(alpha = 0.22f), topLeft = Offset(0f, y), size = Size(size.width, pitch * 0.45f))
                y += pitch
            }
        }
        val t = seconds
        // The roll bar: a soft band of brighter signal drifting down the screen every 7 seconds.
        val barY = (t / 7f % 1f) * (size.height * 1.4f) - size.height * 0.2f
        val bar = size.height * 0.12f
        drawRect(
            Brush.verticalGradient(listOf(Color.Transparent, palette.detail.copy(alpha = 0.05f), Color.Transparent), startY = barY - bar, endY = barY + bar),
            topLeft = Offset(0f, barY - bar),
            size = Size(size.width, bar * 2f),
        )
        val window = floor(t / GLITCH_BACKGROUND_WINDOW).toInt()
        val random = Random(window * 31 + 5)
        val start = window * GLITCH_BACKGROUND_WINDOW + random.nextFloat() * 2f
        if (t > 0f && t - start in 0f..0.12f) {
            repeat(2 + random.nextInt(2)) {
                val y = random.nextFloat() * size.height
                val h = (2 + random.nextInt(6)).dp.toPx()
                val shift = (random.nextFloat() * 2f - 1f) * 18.dp.toPx()
                drawRect(Color(0xFF00F0FF).copy(alpha = 0.12f), topLeft = Offset(shift, y), size = Size(size.width, h))
                drawRect(Color(0xFFFF2BD6).copy(alpha = 0.12f), topLeft = Offset(-shift, y + h * 0.4f), size = Size(size.width, h))
            }
        }
    }
}

/**
 * The desert by night - to go with the Pyramid dice: a deep sky over rolling dunes, each ridge a little
 * lighter than the one behind it, and pyramids far off on the horizon. Painted once.
 */
class DesertBackground(override val id: String, private val palette: BackgroundPalette) : TableBackground {
    override val scoreAreaBrush: Brush = Brush.verticalGradient(listOf(palette.top, palette.bottom))

    override fun DrawScope.drawScoreAreaDecoration() {
        drawCachedSurface("background:$id") {
            val horizon = size.height * 0.3f
            // Pyramids on the horizon, faint.
            for ((x, w) in listOf(0.68f to 0.2f, 0.84f to 0.13f, 0.56f to 0.09f)) {
                val base = horizon + size.height * 0.02f
                val half = size.width * w / 2f
                val apex = Offset(size.width * x, base - half * 0.95f)
                drawPath(polygonPath(listOf(apex, Offset(apex.x - half, base), Offset(apex.x + half, base))), palette.detail.copy(alpha = 0.07f))
                drawPath(polygonPath(listOf(apex, Offset(apex.x + half * 0.25f, base), Offset(apex.x + half, base))), Color.Black.copy(alpha = 0.08f))
            }
            // Dunes, back to front.
            val random = Random(3)
            for (layer in 0 until 4) {
                val top = horizon + size.height * 0.17f * layer
                val wavelength = size.width * (0.7f + random.nextFloat() * 0.6f)
                val phase = random.nextFloat() * 6f
                val dune = Path().apply {
                    moveTo(0f, size.height)
                    var x = 0f
                    while (x <= size.width) {
                        lineTo(x, top + sin(x / wavelength * 2f * PI.toFloat() + phase) * size.height * 0.035f)
                        x += 6.dp.toPx()
                    }
                    lineTo(size.width, size.height)
                    close()
                }
                drawPath(dune, palette.detail.copy(alpha = 0.035f + layer * 0.012f))
            }
        }
    }
}

/** Wrapping paper - to go with the Ribbon dice: faint diagonal stripes, scattered with little dots. Painted once. */
class GiftWrapBackground(override val id: String, private val palette: BackgroundPalette) : TableBackground {
    override val scoreAreaBrush: Brush = Brush.verticalGradient(listOf(palette.top, palette.bottom))

    override fun DrawScope.drawScoreAreaDecoration() {
        drawCachedSurface("background:$id") {
            val band = 10.dp.toPx()
            var x = -size.height
            while (x < size.width) {
                drawLine(palette.detail.copy(alpha = 0.06f), Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = band)
                x += band * 3f
            }
            val gap = 22.dp.toPx()
            var row = 0
            var y = gap / 2f
            while (y < size.height) {
                var dx = if (row % 2 == 0) gap / 2f else gap
                while (dx < size.width) {
                    drawCircle(palette.detail.copy(alpha = 0.09f), 2.dp.toPx(), Offset(dx, y))
                    dx += gap
                }
                y += gap / 2f
                row++
            }
        }
    }
}

// How many sparkles the Glitter background can show at once.
private const val GLITTER_BACKGROUND_SPARKLES = 5

/**
 * Glitter on dark card - to go with the Glitter dice: a faint dusting of [BackgroundPalette.detail]
 * flakes, painted once, and a few sparkles flashing across it. None under reduced motion.
 */
class GlitterBackground(override val id: String, private val palette: BackgroundPalette) : TableBackground {
    override val scoreAreaBrush: Brush = Brush.verticalGradient(listOf(palette.top, palette.bottom))

    private var seconds by mutableFloatStateOf(0f)

    @Composable
    override fun Animate() {
        TwinkleClock { seconds = it }
    }

    override fun DrawScope.drawScoreAreaDecoration() {
        drawCachedSurface("background:$id") {
            val random = Random(17)
            val pitch = 3.dp.toPx()
            repeat((size.width * size.height / (pitch * pitch) * 0.4f).toInt()) {
                val at = Offset(random.nextFloat() * size.width, random.nextFloat() * size.height)
                val flake = pitch * (0.2f + random.nextFloat() * 0.3f)
                drawRect(lerp(palette.detail, Color.White, random.nextFloat() * 0.5f).copy(alpha = 0.06f + random.nextFloat() * random.nextFloat() * 0.4f), at, Size(flake, flake))
            }
        }
        for (slot in 0 until GLITTER_BACKGROUND_SPARKLES) {
            val (at, grow) = sparkle(id.hashCode() + 1, slot, seconds, period = 2.8f, flash = 0.6f) ?: continue
            drawSparkle(Offset(at.x * size.width, at.y * size.height), 8.dp.toPx() * grow, palette.detail, alpha = 0.7f)
        }
    }
}
