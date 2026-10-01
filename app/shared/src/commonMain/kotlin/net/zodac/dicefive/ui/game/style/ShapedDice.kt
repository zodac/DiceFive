package net.zodac.dicefive.ui.game.style

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import net.zodac.dicefive.ui.theme.GoldAccent

// How rounded a Misprint die's corners are, as a percentage of its size - for drawing it and its shadow alike.
private const val MISPRINT_CORNER_PERCENT = 16

/**
 * A misprinted die: a wobbly, doubled outline in [ink] on [paper], and every pip printed well off
 * its proper spot - at least a whole pip's width away, in its own random direction - though still
 * roughly where it belongs, and never overlapping another, so the count still reads. Seeded by
 * face, so each face is always misprinted the same way - and differently for each colour ([seed]).
 */
class MisprintDiceStyle(
    override val id: String,
    private val paper: Color,
    private val ink: Color,
    private val seed: Int,
    private val heldRing: Color = GoldAccent,
) : DiceStyle, Swatched {
    override val swatch: Color = paper

    override fun recoloured(palette: DieColourPalette): DiceStyle = MisprintDiceStyle(id, palette.diceTop, palette.pip, seed, palette.heldRing)

    override val cornerPercent: Int = MISPRINT_CORNER_PERCENT

    @Composable
    override fun Die(value: Int, held: Boolean, modifier: Modifier) = StyledDie(
        value = value,
        held = held,
        modifier = modifier,
        face = Brush.linearGradient(listOf(paper, paper)),
        edge = Color.Transparent,
        pipColor = ink,
        cornerPercent = MISPRINT_CORNER_PERCENT,
        pipShape = PipShape.CUSTOM,
        customPips = { face -> drawMisprintedPips(face) },
        heldRingColor = heldRing,
    ) {
        val line = Stroke(width = 1.4.dp.toPx())
        val inset = 2.dp.toPx()
        val corner = CornerRadius(size.minDimension * 0.16f)
        drawRoundRect(ink.copy(alpha = 0.85f), Offset(inset, inset), Size(size.width - inset * 2, size.height - inset * 2), corner, style = line)
        drawRoundRect(
            ink.copy(alpha = 0.5f),
            Offset(inset + 0.8.dp.toPx(), inset - 0.6.dp.toPx()),
            Size(size.width - inset * 2 - 1.dp.toPx(), size.height - inset * 2 + 0.8.dp.toPx()),
            corner,
            style = Stroke(width = 1.dp.toPx()),
        )
    }

    private fun DrawScope.drawMisprintedPips(face: Int) {
        val radius = size.minDimension * 0.1f
        for (centre in misprintedPips(face, size.minDimension, radius, seed)) {
            drawCircle(ink.copy(alpha = 0.9f), radius, centre)
        }
    }
}

/**
 * Where [face]'s pips land on a misprinted die [side] across: each one moved between one and one
 * and a half pip-spacings from its proper spot, in a random direction, staying on the face and
 * clear of the pips already placed. If no such spot turns up for a pip, it takes the one that came
 * closest to fitting.
 */
private fun misprintedPips(face: Int, side: Float, radius: Float, styleSeed: Int): List<Offset> {
    val random = Random(face * 31 + styleSeed * 9973)
    val spacing = side * 0.24f
    val low = radius * 1.1f
    val high = side - radius * 1.1f
    val placed = mutableListOf<Offset>()
    for (proper in pipLayout(face)) {
        val home = Offset(proper.x * side, proper.y * side)
        var best = home
        var bestClearance = Float.NEGATIVE_INFINITY
        for (attempt in 0 until 80) {
            val angle = random.nextFloat() * 2f * PI.toFloat()
            val distance = spacing * (1f + random.nextFloat() * 0.5f)
            val candidate = home + Offset(cos(angle) * distance, sin(angle) * distance)
            if (candidate.x !in low..high || candidate.y !in low..high) continue
            val clearance = placed.minOfOrNull { (it - candidate).getDistance() } ?: Float.MAX_VALUE
            if (clearance > bestClearance) {
                best = candidate
                bestClearance = clearance
            }
            if (clearance >= radius * 2.3f) break
        }
        placed += best
    }
    return placed
}
