package net.zodac.dicefive.ui.game.style

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Hexagonal floor tiles - to go with the Bestagon dice - each a slightly different shade, bevelled
 * along its edges, set in [MatPalette.detail] grout. Painted once.
 */
class HexTileDiceMat(id: String, palette: MatPalette) : PatternedDiceMat(id, palette) {
    override fun DrawScope.drawPattern() = drawCachedSurface("mat:$id") {
        val radius = 15.dp.toPx()
        val grout = 1.6.dp.toPx()
        val random = Random(11)
        val rowHeight = radius * sqrt(3f)
        var column = 0
        var cx = 0f
        while (cx < size.width + radius) {
            var cy = if (column % 2 == 0) 0f else rowHeight / 2f
            while (cy < size.height + rowHeight) {
                val shade = random.nextFloat()
                val tile = lerp(palette.bottom, palette.top, 0.6f + shade * 0.4f)
                val centre = Offset(cx, cy)
                val outer = hexagonPoints(centre, radius - grout / 2f)
                val inner = hexagonPoints(centre, radius - grout / 2f - 2.dp.toPx())
                drawPath(polygonPath(outer), palette.detail)
                drawPath(polygonPath(hexagonPoints(centre, radius - grout)), lerp(tile, Color.Black, 0.18f))
                // The bevel: lighter along the top-left edges, the flat top inset from it.
                drawPath(polygonPath(listOf(outer[3], outer[4], outer[5], inner[5], inner[4], inner[3])), lerp(tile, Color.White, 0.12f))
                drawPath(polygonPath(inner), Brush.linearGradient(listOf(lerp(tile, Color.White, 0.08f), tile), start = centre - Offset(radius, radius), end = centre + Offset(radius, radius)))
                cy += rowHeight
            }
            cx += radius * 1.5f
            column++
        }
    }
}

/** A flat-topped hexagon's corners round [centre], [radius] out, starting from the right and going clockwise. */
internal fun hexagonPoints(centre: Offset, radius: Float): List<Offset> = (0..5).map { corner ->
    val angle = PI.toFloat() / 3f * corner
    Offset(centre.x + radius * cos(angle), centre.y + radius * sin(angle))
}

/**
 * Desert sand - to go with the Pyramid dice - blown into ripples, each lit along its windward side
 * and shadowed on its lee, over a fine grain. Painted once.
 */
class SandDiceMat(id: String, palette: MatPalette) : PatternedDiceMat(id, palette) {
    override fun DrawScope.drawPattern() = drawCachedSurface("mat:$id") {
        val random = Random(4)
        val spacing = 9.dp.toPx()
        val step = 4.dp.toPx()
        var y = -spacing
        var row = 0
        while (y < size.height + spacing) {
            val phase = random.nextFloat() * 6f
            val wavelength = (60 + random.nextInt(40)).dp.toPx()
            fun ripple(dy: Float) = Path().apply {
                var x = -step
                moveTo(x, y + dy)
                while (x <= size.width + step) {
                    x += step
                    lineTo(x, y + dy + sin(x / wavelength * 2f * PI.toFloat() + phase) * 3.dp.toPx() + sin(x / (wavelength * 0.37f) + row) * 1.dp.toPx())
                }
            }
            drawPath(ripple(0f), lerp(palette.top, Color.White, 0.35f).copy(alpha = 0.45f), style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round))
            drawPath(ripple(1.6.dp.toPx()), palette.detail.copy(alpha = 0.3f), style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round))
            y += spacing * (0.8f + random.nextFloat() * 0.4f)
            row++
        }
        val grain = 1.dp.toPx()
        repeat((size.width * size.height / (grain * grain * 18f)).toInt()) {
            val at = Offset(random.nextFloat() * size.width, random.nextFloat() * size.height)
            drawCircle((if (random.nextBoolean()) palette.detail else Color.White).copy(alpha = 0.12f + random.nextFloat() * 0.12f), grain * (0.3f + random.nextFloat() * 0.4f), at)
        }
    }
}

/**
 * A circuit board - to go with the Glitch dice: copper traces in [MatPalette.detail] running across
 * the board at right angles and diagonals, ending in solder pads, with a couple of chips. Painted once.
 */
class CircuitDiceMat(id: String, palette: MatPalette) : PatternedDiceMat(id, palette) {
    override fun DrawScope.drawPattern() = drawCachedSurface("mat:$id") {
        val random = Random(8)
        val cell = 10.dp.toPx()
        val columns = (size.width / cell).toInt() + 1
        val rows = (size.height / cell).toInt() + 1
        val trace = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val copper = palette.detail.copy(alpha = 0.38f)
        // Chips first, so traces run out from under them.
        val chips = List(2) {
            val w = cell * (3 + random.nextInt(2))
            val h = cell * 2
            Offset(cell * (1 + random.nextInt(columns - 6)), cell * (1 + random.nextInt((rows - 4).coerceAtLeast(1)))) to Size(w, h)
        }
        repeat(columns * rows / 9) {
            val path = Path()
            var x = random.nextInt(columns)
            var y = random.nextInt(rows)
            path.moveTo(x * cell, y * cell)
            var dx = listOf(-1, 0, 1).random(random)
            var dy = if (dx == 0) listOf(-1, 1).random(random) else 0
            repeat(2 + random.nextInt(6)) {
                if (random.nextFloat() < 0.3f) {
                    // A turn: to a diagonal, or off one back to straight.
                    if (dx != 0 && dy != 0) { if (random.nextBoolean()) dx = 0 else dy = 0 } else if (dx == 0) dx = listOf(-1, 1).random(random) else dy = listOf(-1, 1).random(random)
                }
                x += dx
                y += dy
                path.lineTo(x * cell, y * cell)
            }
            drawPath(path, copper, style = trace)
            drawCircle(copper, 2.6.dp.toPx(), Offset(x * cell, y * cell))
            drawCircle(palette.bottom, 1.1.dp.toPx(), Offset(x * cell, y * cell))
        }
        for ((at, chip) in chips) {
            val pin = 2.dp.toPx()
            var px = at.x + pin * 2
            while (px < at.x + chip.width - pin) {
                drawLine(lerp(palette.detail, Color.White, 0.3f), Offset(px, at.y - pin * 1.5f), Offset(px, at.y + chip.height + pin * 1.5f), strokeWidth = pin * 0.7f)
                px += pin * 2.2f
            }
            drawRoundRect(Color(0xFF121416), at, chip, CornerRadius(pin))
            drawCircle(Color(0xFF2A2E32), pin * 0.7f, at + Offset(pin * 1.8f, pin * 1.8f))
        }
    }
}

// The Neon mat's pulse - see NEON_PULSE_SECONDS on the dice.
private const val NEON_MAT_PULSE_SECONDS = 2.4f

/**
 * A neon-lit wall - to go with the Neon dice: dark bricks, framed by two neon tubes running round the
 * tray just inside its edge, [MatPalette.detail] outside and [MatPalette.slotBorder] inside, their
 * glow pulsing gently and out of step. Steady under reduced motion.
 */
class NeonDiceMat(id: String, palette: MatPalette) : PatternedDiceMat(id, palette) {
    override val animated: Boolean = true

    override fun DrawScope.drawPattern() = drawPattern(seconds = 0f)

    override fun DrawScope.drawPattern(seconds: Float) {
        drawCachedSurface("mat:$id") { paintBricks(palette.detail.copy(alpha = 0f), Color.Black.copy(alpha = 0.35f), lerp(palette.top, Color.White, 0.04f), seed = 2) }
        val swell = 0.5f + 0.5f * sin(2f * PI.toFloat() * seconds / NEON_MAT_PULSE_SECONDS)
        drawNeonFrame(3.dp.toPx(), palette.detail, 0.6f + 0.4f * swell)
        drawNeonFrame(7.dp.toPx(), palette.slotBorder, 0.6f + 0.4f * (1f - swell))
    }

    private fun DrawScope.drawNeonFrame(inset: Float, colour: Color, glow: Float) {
        val topLeft = Offset(inset, inset)
        val box = Size(size.width - inset * 2, size.height - inset * 2)
        val corner = CornerRadius(14.dp.toPx() - inset * 0.5f)
        for ((width, alpha) in listOf(7f to 0.1f, 4.5f to 0.2f, 2.5f to 0.4f)) {
            drawRoundRect(colour.copy(alpha = alpha * glow), topLeft, box, corner, style = Stroke(width = width.dp.toPx()))
        }
        drawRoundRect(lerp(colour, Color.White, 0.25f), topLeft, box, corner, style = Stroke(width = 1.6.dp.toPx()))
        drawRoundRect(lerp(colour, Color.White, 0.8f).copy(alpha = 0.8f), topLeft, box, corner, style = Stroke(width = 0.6.dp.toPx()))
    }
}

/**
 * Bricks laid in running bond, [mortar] between them, each brick its own slight shade of [brick]
 * (or of whatever's under them, where [brick] is see-through). Rows about 13dp tall.
 */
internal fun DrawScope.paintBricks(brick: Color, mortar: Color, light: Color, seed: Int, rowHeight: Float = 13.dp.toPx()) {
    val random = Random(seed)
    val length = rowHeight * 2.4f
    val joint = rowHeight * 0.12f
    var row = 0
    var y = 0f
    while (y < size.height) {
        drawRect(mortar, topLeft = Offset(0f, y), size = Size(size.width, joint))
        var x = if (row % 2 == 0) 0f else -length / 2f
        while (x < size.width) {
            val shade = random.nextFloat()
            drawRect(
                lerp(light.copy(alpha = 0f), light, shade * 0.6f),
                topLeft = Offset(x + joint, y + joint),
                size = Size(length - joint, rowHeight - joint),
            )
            drawRect(brick, topLeft = Offset(x + joint, y + joint), size = Size(length - joint, rowHeight - joint))
            drawRect(mortar, topLeft = Offset(x, y), size = Size(joint, rowHeight))
            x += length
        }
        y += rowHeight
        row++
    }
}

/**
 * Wrapping paper - to go with the Ribbon dice: polka dots in [MatPalette.detail], and a satin ribbon
 * in [MatPalette.slotBorder] tied round it, across and down, with a bow where the two cross.
 */
class GiftWrapDiceMat(id: String, palette: MatPalette) : PatternedDiceMat(id, palette) {
    override fun DrawScope.drawPattern() = drawCachedSurface("mat:$id") {
        val gap = 18.dp.toPx()
        var row = 0
        var y = gap / 2f
        while (y < size.height + gap) {
            var x = if (row % 2 == 0) gap / 2f else gap
            while (x < size.width + gap) {
                drawCircle(palette.detail.copy(alpha = 0.5f), 2.6.dp.toPx(), Offset(x, y))
                x += gap
            }
            y += gap / 2f
            row++
        }
        val ribbon = palette.slotBorder
        val band = 12.dp.toPx()
        val acrossY = size.height * 0.78f
        val downX = size.width * 0.86f
        drawRibbonBand(Offset(0f, acrossY), Offset(size.width, acrossY), band, ribbon)
        drawRibbonBand(Offset(downX, 0f), Offset(downX, size.height), band, ribbon)
        drawBow(Offset(downX, acrossY), band * 1.2f, ribbon)
    }
}

/** A flat satin ribbon [width] wide from [from] to [to]: a soft shadow, darker edges and a sheen along it. */
internal fun DrawScope.drawRibbonBand(from: Offset, to: Offset, width: Float, colour: Color) {
    drawLine(Color.Black.copy(alpha = 0.2f), from + Offset(1.dp.toPx(), 1.5.dp.toPx()), to + Offset(1.dp.toPx(), 1.5.dp.toPx()), strokeWidth = width)
    drawLine(lerp(colour, Color.Black, 0.3f), from, to, strokeWidth = width)
    drawLine(colour, from, to, strokeWidth = width * 0.82f)
    drawLine(lerp(colour, Color.White, 0.5f).copy(alpha = 0.5f), from, to, strokeWidth = width * 0.22f)
}

/** A bow of [colour] ribbon tied at [centre], its loops [size] out either side, its tails hanging below. */
internal fun DrawScope.drawBow(centre: Offset, size: Float, colour: Color) {
    val deep = lerp(colour, Color.Black, 0.3f)
    val sheen = lerp(colour, Color.White, 0.4f)
    for (side in listOf(-1f, 1f)) {
        // A tail, cut into a swallowtail at its end.
        val tail = Path().apply {
            moveTo(centre.x, centre.y)
            lineTo(centre.x + side * size * 0.75f, centre.y + size * 1.15f)
            lineTo(centre.x + side * size * 0.45f, centre.y + size * 1.0f)
            lineTo(centre.x + side * size * 0.3f, centre.y + size * 1.2f)
            close()
        }
        drawPath(tail, deep)
        // A loop: a teardrop out to the side.
        val loop = Path().apply {
            moveTo(centre.x, centre.y)
            cubicTo(centre.x + side * size * 0.6f, centre.y - size * 0.9f, centre.x + side * size * 1.3f, centre.y - size * 0.5f, centre.x + side * size * 1.05f, centre.y + size * 0.05f)
            cubicTo(centre.x + side * size * 0.85f, centre.y + size * 0.45f, centre.x + side * size * 0.35f, centre.y + size * 0.25f, centre.x, centre.y)
            close()
        }
        drawPath(loop, Color.Black.copy(alpha = 0.2f), style = Stroke(width = size * 0.12f))
        drawPath(loop, colour)
        drawPath(loop, deep, style = Stroke(width = size * 0.06f))
        drawLine(sheen.copy(alpha = 0.6f), centre + Offset(side * size * 0.25f, -size * 0.25f), centre + Offset(side * size * 0.8f, -size * 0.4f), strokeWidth = size * 0.1f, cap = StrokeCap.Round)
    }
    drawCircle(deep, size * 0.26f, centre)
    drawCircle(colour, size * 0.2f, centre)
    drawCircle(sheen.copy(alpha = 0.6f), size * 0.07f, centre - Offset(size * 0.06f, size * 0.06f))
}

// How many sparkles the Glitter mat can show at once.
private const val GLITTER_MAT_SPARKLES = 6

/**
 * Glitter-strewn felt - to go with the Glitter dice: fine glitter in [MatPalette.detail] over the
 * tray's colour, painted once, with up to [GLITTER_MAT_SPARKLES] sparkles flashing across it at a
 * time. None under reduced motion.
 */
class GlitterDiceMat(id: String, palette: MatPalette) : PatternedDiceMat(id, palette) {
    override val animated: Boolean = true

    override fun DrawScope.drawPattern() = drawPattern(seconds = 0f)

    override fun DrawScope.drawPattern(seconds: Float) {
        drawCachedSurface("mat:$id") {
            val random = Random(21)
            val pitch = 2.2.dp.toPx()
            repeat((size.width * size.height / (pitch * pitch) * 0.5f).toInt()) {
                val at = Offset(random.nextFloat() * size.width, random.nextFloat() * size.height)
                val flake = pitch * (0.25f + random.nextFloat() * 0.35f)
                drawRect(palette.detail.copy(alpha = 0.15f + random.nextFloat() * random.nextFloat() * 0.85f), at, Size(flake, flake))
            }
        }
        clipRect {
            for (slot in 0 until GLITTER_MAT_SPARKLES) {
                val (at, grow) = sparkle(id.hashCode(), slot, seconds, period = 2.2f) ?: continue
                drawSparkle(Offset(at.x * size.width, at.y * size.height), 7.dp.toPx() * grow, palette.detail)
            }
        }
    }
}
