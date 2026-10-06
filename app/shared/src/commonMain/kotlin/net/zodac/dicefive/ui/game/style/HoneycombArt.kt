package net.zodac.dicefive.ui.game.style

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** A honeycomb's colours: its [wax] walls and caps, and the honey in its cells, [honey] at the rim deepening to [deepHoney]. */
internal class CombColours(val wax: Color, val honey: Color, val deepHoney: Color)

/**
 * Honeycomb across the whole surface: flat-topped cells [radius] across, in [colours]' wax - most of
 * them full of glistening honey, some sealed with a pale wax cap, the odd one empty and showing its depth.
 * Seeded by [seed], so it's the same comb every time. Hundreds of cells: paint it through
 * [drawCachedSurface], never live.
 */
internal fun DrawScope.paintHoneycomb(seed: Int, colours: CombColours, radius: Float) {
    val random = Random(seed)
    val wall = radius * 0.16f
    val rowHeight = radius * sqrt(3f)
    drawRect(lerp(colours.wax, Color.Black, 0.35f))
    var column = 0
    var cx = 0f
    while (cx < size.width + radius) {
        var cy = if (column % 2 == 0) 0f else rowHeight / 2f
        while (cy < size.height + rowHeight) {
            val centre = Offset(cx, cy)
            val cell = polygonPath(hexagonPoints(centre, radius - wall * 0.5f))
            val inside = polygonPath(hexagonPoints(centre, radius - wall * 1.5f))
            // The wax wall, lit along its top.
            drawPath(cell, Brush.linearGradient(listOf(lerp(colours.wax, Color.White, 0.25f), colours.wax), start = centre - Offset(0f, radius), end = centre + Offset(0f, radius)))
            val roll = random.nextFloat()
            when {
                roll < 0.7f -> {
                    // Honey: deep in the middle, brighter towards the walls where it's shallow, a glint of light on its surface.
                    drawPath(inside, Brush.radialGradient(listOf(colours.deepHoney, colours.honey, lerp(colours.honey, Color.White, 0.2f)), center = centre, radius = radius))
                    clipPath(inside) {
                        drawRect(Color.Black.copy(alpha = 0.18f), topLeft = centre - Offset(radius, radius), size = Size(radius * 2f, wall * 1.6f))
                    }
                    drawOval(Color.White.copy(alpha = 0.45f), topLeft = centre + Offset(-radius * 0.5f, -radius * 0.55f), size = Size(radius * 0.42f, radius * 0.22f))
                    drawCircle(Color.White.copy(alpha = 0.6f), radius * 0.06f, centre + Offset(radius * 0.32f, radius * 0.3f))
                }
                roll < 0.93f -> {
                    // A wax cap, sealed over the honey: pale, softly domed.
                    drawPath(inside, Brush.radialGradient(listOf(lerp(colours.wax, Color.White, 0.55f), lerp(colours.wax, Color.White, 0.2f)), center = centre - Offset(radius * 0.2f, radius * 0.25f), radius = radius))
                    drawPath(inside, lerp(colours.wax, Color.Black, 0.15f).copy(alpha = 0.5f), style = Stroke(width = wall * 0.4f))
                }
                else -> {
                    // Empty: dark at the bottom of the cell, its far walls catching the light.
                    drawPath(inside, Brush.radialGradient(listOf(lerp(colours.deepHoney, Color.Black, 0.65f), lerp(colours.wax, Color.Black, 0.4f)), center = centre + Offset(radius * 0.1f, radius * 0.15f), radius = radius))
                    clipPath(inside) {
                        drawRect(lerp(colours.wax, Color.White, 0.1f).copy(alpha = 0.35f), topLeft = centre + Offset(-radius, radius * 0.45f), size = Size(radius * 2f, radius))
                    }
                }
            }
            cy += rowHeight
        }
        cx += radius * 1.5f
        column++
    }
}

// How long the bee waits between flicks of its wings, and how long a flick lasts, in seconds.
private const val BEE_BUZZ_EVERY = 3.6f
private const val BEE_BUZZ_FOR = 0.7f

private val BeeYellow = Color(0xFFF2B630)
private val BeeBlack = Color(0xFF231A10)
private val BeeFuzz = Color(0xFFB8862A)

/**
 * A honeybee seen from above, [length] long from head to sting, at [centre], heading [degrees]
 * clockwise from straight up the screen: striped abdomen, fuzzy thorax, dark head with big eyes and
 * feelers, six legs and see-through wings. At rest its wings lie folded back over it; [seconds] in,
 * every few seconds, it flicks them open and buzzes them in a blur for a moment. Still at 0.
 */
internal fun DrawScope.drawHoneybee(centre: Offset, length: Float, degrees: Float, seconds: Float) {
    val into = seconds % BEE_BUZZ_EVERY
    val buzzing = seconds > 0f && into < BEE_BUZZ_FOR
    // How far its wings are spread, 0 folded to 1 open, easing open and shut either side of the buzz.
    val spread = if (buzzing) sin(PI.toFloat() * into / BEE_BUZZ_FOR).coerceIn(0f, 1f).let { minOf(1f, it * 1.6f) } else 0f
    inUnit(centre, length / 2f, degrees) {
        val thin = 0.03f
        // Its shadow on the comb.
        drawOval(Color.Black.copy(alpha = 0.28f), topLeft = Offset(-0.22f, -0.55f), size = Size(0.62f, 1.25f))
        // Legs: three a side, from under the thorax.
        for (side in listOf(-1f, 1f)) {
            for ((from, to) in listOf(Offset(0.12f, -0.22f) to Offset(0.42f, -0.42f), Offset(0.14f, -0.12f) to Offset(0.48f, -0.04f), Offset(0.12f, -0.02f) to Offset(0.4f, 0.32f))) {
                val knee = Offset(to.x * 0.75f, (from.y + to.y) / 2f - 0.04f)
                val leg = Path().apply {
                    moveTo(from.x * side, from.y)
                    lineTo(knee.x * side, knee.y)
                    lineTo(to.x * side, to.y)
                }
                drawPath(leg, BeeBlack, style = Stroke(width = thin * 1.2f, cap = StrokeCap.Round))
            }
        }
        // The abdomen: banded yellow and black, darker to its sting.
        val abdomen = Path().apply { addOval(Rect(Offset(-0.26f, -0.08f), Size(0.52f, 0.82f))) }
        drawPath(abdomen, Brush.radialGradient(listOf(lerp(BeeYellow, Color.White, 0.25f), BeeYellow, lerp(BeeYellow, Color.Black, 0.25f)), center = Offset(-0.08f, 0.2f), radius = 0.55f))
        clipPath(abdomen) {
            for (y in listOf(0.2f, 0.38f, 0.56f)) drawRect(BeeBlack, topLeft = Offset(-0.3f, y), size = Size(0.6f, 0.085f))
            drawRect(BeeBlack, topLeft = Offset(-0.3f, 0.68f), size = Size(0.6f, 0.1f))
        }
        // The thorax, fuzzy: a ring of short hairs round it.
        for (k in 0 until 18) {
            val a = 2f * PI.toFloat() * k / 18f
            drawLine(BeeFuzz, Offset(cos(a) * 0.17f, -0.18f + sin(a) * 0.17f), Offset(cos(a) * 0.23f, -0.18f + sin(a) * 0.23f), strokeWidth = thin, cap = StrokeCap.Round)
        }
        drawCircle(Brush.radialGradient(listOf(lerp(BeeFuzz, Color.White, 0.2f), lerp(BeeFuzz, BeeBlack, 0.4f)), center = Offset(-0.05f, -0.24f), radius = 0.24f), 0.2f, Offset(0f, -0.18f))
        // The head, its two big eyes, and its feelers.
        drawCircle(BeeBlack, 0.15f, Offset(0f, -0.48f))
        for (side in listOf(-1f, 1f)) {
            drawOval(Color(0xFF3A3028), topLeft = Offset(side * 0.11f - 0.055f, -0.58f), size = Size(0.11f, 0.17f))
            drawCircle(Color.White.copy(alpha = 0.5f), 0.022f, Offset(side * 0.11f - 0.015f, -0.54f))
            val feeler = Path().apply {
                moveTo(side * 0.05f, -0.6f)
                quadraticTo(side * 0.08f, -0.82f, side * 0.24f, -0.9f)
            }
            drawPath(feeler, BeeBlack, style = Stroke(width = thin, cap = StrokeCap.Round))
        }
        // Wings: folded back over the abdomen at rest; opened and blurred while buzzing.
        val ghosts = if (buzzing) 3 else 1
        for (ghost in 0 until ghosts) {
            val beat = if (buzzing) (ghost - 1) * 16f else 0f
            for (side in listOf(-1f, 1f)) {
                val fore = (12f + spread * 48f + beat) * side
                val hind = (6f + spread * 36f + beat) * side
                val alpha = if (buzzing) 0.3f else 0.55f
                rotate(fore, pivot = Offset(side * 0.05f, -0.2f)) {
                    drawOval(Color.White.copy(alpha = alpha), topLeft = Offset(side * 0.05f - 0.1f, -0.2f), size = Size(0.2f, 0.62f))
                    drawOval(Color(0xFF8A7A66).copy(alpha = alpha), topLeft = Offset(side * 0.05f - 0.1f, -0.2f), size = Size(0.2f, 0.62f), style = Stroke(width = thin * 0.6f))
                    drawLine(Color(0xFF8A7A66).copy(alpha = alpha * 0.8f), Offset(side * 0.05f, -0.15f), Offset(side * 0.05f, 0.35f), strokeWidth = thin * 0.5f)
                }
                rotate(hind, pivot = Offset(side * 0.04f, -0.08f)) {
                    drawOval(Color.White.copy(alpha = alpha * 0.85f), topLeft = Offset(side * 0.04f - 0.07f, -0.08f), size = Size(0.14f, 0.42f))
                    drawOval(Color(0xFF8A7A66).copy(alpha = alpha), topLeft = Offset(side * 0.04f - 0.07f, -0.08f), size = Size(0.14f, 0.42f), style = Stroke(width = thin * 0.6f))
                }
            }
        }
    }
}

/**
 * A honeycomb mat - to go with the Bestagon dice: honeycomb right across the tray, painted once, with
 * a honeybee resting on it near one corner, flicking its wings every few seconds. Its colours are the
 * [MatPalette]'s: [MatPalette.detail] the wax, [MatPalette.top] the honey, [MatPalette.bottom] the honey
 * at its deepest. Only the bee is drawn live; it doesn't buzz under reduced motion.
 */
class HoneycombDiceMat(id: String, palette: MatPalette) : PatternedDiceMat(id, palette) {
    override val animated: Boolean = true

    override fun DrawScope.drawPattern() = drawPattern(seconds = 0f)

    override fun DrawScope.drawPattern(seconds: Float) {
        drawCachedSurface("mat:$id") { paintHoneycomb(31, CombColours(palette.detail, palette.top, palette.bottom), 12.dp.toPx()) }
        drawHoneybee(Offset(size.width * 0.13f, size.height - 40.dp.toPx()), 42.dp.toPx(), 35f, seconds)
    }
}

/**
 * A honeycomb background - to go with the Bestagon dice: the honeycomb of the mat, larger and dimmed
 * well back so the scorecard reads over it. Painted once. [BackgroundPalette.detail] is its wax.
 */
class HoneycombCombBackground(override val id: String, private val palette: BackgroundPalette) : TableBackground {
    override val scoreAreaBrush: Brush = Brush.verticalGradient(listOf(palette.top, palette.bottom))

    override fun DrawScope.drawScoreAreaDecoration() {
        drawCachedSurface("background:$id") {
            paintHoneycomb(37, CombColours(palette.detail, lerp(palette.detail, palette.top, 0.3f), palette.bottom), 16.dp.toPx())
            drawRect(Brush.verticalGradient(listOf(palette.top.copy(alpha = 0.72f), palette.bottom.copy(alpha = 0.8f))))
        }
    }
}
