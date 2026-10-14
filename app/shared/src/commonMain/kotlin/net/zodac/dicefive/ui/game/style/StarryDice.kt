package net.zodac.dicefive.ui.game.style

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import net.zodac.dicefive.ui.theme.GoldAccent

/** How many faint specks of sky each face of a Starry die has, between its pips. */
private const val STARRY_SPECKS = 7

/** How far a five-pointed star's inner corners sit from its centre, as a fraction of its points' reach. */
private const val STAR_INNER = 0.45f

/**
 * Starry dice: a [top]-to-[bottom] night sky, a few faint specks of distant stars scattered across it - different on
 * every face of every die, and kept clear of the pips - and pips that are five-pointed [star]s with a soft glow. Static:
 * nothing moves.
 */
class StarryDiceStyle(
    override val id: String,
    private val top: Color,
    private val bottom: Color,
    private val star: Color,
    private val heldRing: Color = GoldAccent,
) : DiceStyle, Swatched {
    override val swatch: Color = top
    override val bodyColor: Color = bottom

    override fun recoloured(palette: DieColourPalette): DiceStyle = StarryDiceStyle(id, palette.diceTop, palette.diceBottom, palette.pip, palette.heldRing)

    @Composable
    override fun Die(value: Int, held: Boolean, modifier: Modifier) {
        val seed = naturalPatternSeed(value)
        Canvas(modifier = modifier.dieShadow(RoundedCornerShape(BEVELED_DIE_CORNER_PERCENT))) {
            val corner = CornerRadius(size.minDimension * BEVELED_DIE_CORNER_PERCENT / 100f)
            drawRoundRect(Brush.verticalGradient(listOf(top, bottom)), cornerRadius = corner)

            val inset = size.minDimension * 0.13f
            val inner = Size(size.width - inset * 2, size.height - inset * 2)
            val radius = size.minDimension * 0.105f
            val pips = pipLayout(value).map { Offset(inset + it.x * inner.width, inset + it.y * inner.height) }
            drawSpecks(seed, pips, clearance = radius * 1.6f)
            for (pip in pips) drawStarPip(pip, radius)

            drawRoundRect(
                if (held) heldRing else bottom.copy(alpha = 0.8f),
                cornerRadius = corner,
                style = Stroke(width = if (held) 2.dp.toPx() else 1.dp.toPx()),
            )
        }
    }

    /** Tiny four-pointed specks of sky, seeded by [seed], none within [clearance] of a pip in [pips]. */
    private fun DrawScope.drawSpecks(seed: Int, pips: List<Offset>, clearance: Float) {
        val random = Random(seed)
        val margin = size.minDimension * 0.1f
        repeat(STARRY_SPECKS) {
            val at = Offset(margin + random.nextFloat() * (size.width - margin * 2), margin + random.nextFloat() * (size.height - margin * 2))
            val reach = size.minDimension * (0.018f + random.nextFloat() * 0.022f)
            val alpha = 0.25f + random.nextFloat() * 0.35f
            if (pips.any { (it - at).getDistance() < clearance }) return@repeat
            val waist = reach * 0.3f
            val speck = Path().apply {
                moveTo(at.x, at.y - reach)
                lineTo(at.x + waist, at.y - waist)
                lineTo(at.x + reach, at.y)
                lineTo(at.x + waist, at.y + waist)
                lineTo(at.x, at.y + reach)
                lineTo(at.x - waist, at.y + waist)
                lineTo(at.x - reach, at.y)
                lineTo(at.x - waist, at.y - waist)
                close()
            }
            drawPath(speck, Color.White.copy(alpha = alpha))
        }
    }

    /** A five-pointed star pip at [centre], its points [radius] out, pointing up, in a soft glow. */
    private fun DrawScope.drawStarPip(centre: Offset, radius: Float) {
        drawCircle(Brush.radialGradient(listOf(star.copy(alpha = 0.35f), Color.Transparent), center = centre, radius = radius * 1.9f), radius * 1.9f, centre)
        val path = Path()
        for (i in 0 until 10) {
            val angle = -PI / 2 + i * PI / 5
            val reach = if (i % 2 == 0) radius else radius * STAR_INNER
            val x = centre.x + (cos(angle) * reach).toFloat()
            // A star's points reach further up than its feet reach down: nudged down a touch to sit centred on its spot.
            val y = centre.y + radius * 0.1f + (sin(angle) * reach).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        drawPath(path, star)
    }
}
