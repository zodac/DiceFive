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
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import net.zodac.dicefive.ui.theme.GoldAccent

/**
 * Devil dice: a [top]-to-[bottom] body lit from below by an ember glow, a pair of [horn]s curling out of
 * its two top corners - their tips a little past the die's own square - and pips that are little flames,
 * [flame] round a [flameCore]. Static: nothing moves.
 */
class DevilDiceStyle(
    override val id: String,
    private val top: Color,
    private val bottom: Color,
    private val flame: Color,
    private val flameCore: Color,
    private val horn: Color,
    private val hornLight: Color,
    private val heldRing: Color = GoldAccent,
) : DiceStyle, Swatched {
    override val swatch: Color = top
    override val bodyColor: Color = lerp(top, bottom, 0.5f)

    override fun recoloured(palette: DieColourPalette): DiceStyle = DevilDiceStyle(
        id,
        palette.diceTop,
        palette.diceBottom,
        palette.pip,
        lerp(palette.pip, Color.White, 0.5f),
        lerp(palette.diceBottom, Color.Black, 0.6f),
        palette.diceBottom,
        palette.heldRing,
    )

    @Composable
    override fun Die(value: Int, held: Boolean, modifier: Modifier) {
        Canvas(modifier = modifier.dieShadow(RoundedCornerShape(BEVELED_DIE_CORNER_PERCENT))) {
            val corner = CornerRadius(size.minDimension * BEVELED_DIE_CORNER_PERCENT / 100f)
            // Drawn first, so the body covers their roots and they grow out of the corners.
            drawHorn(mirrored = false)
            drawHorn(mirrored = true)
            drawRoundRect(Brush.verticalGradient(listOf(top, bottom)), cornerRadius = corner)
            // The ember glow, rising from the bottom edge.
            drawRoundRect(
                Brush.radialGradient(
                    listOf(flame.copy(alpha = 0.35f), Color.Transparent),
                    center = Offset(size.width / 2, size.height * 1.05f),
                    radius = size.minDimension * 0.75f,
                ),
                cornerRadius = corner,
            )
            drawRoundRect(
                if (held) heldRing else bottom.copy(alpha = 0.8f),
                cornerRadius = corner,
                style = Stroke(width = if (held) 2.dp.toPx() else 1.dp.toPx()),
            )

            val inset = size.minDimension * 0.13f
            val inner = Size(size.width - inset * 2, size.height - inset * 2)
            val radius = size.minDimension * 0.075f
            for (spot in pipLayout(value)) drawFlame(Offset(inset + spot.x * inner.width, inset + spot.y * inner.height), radius)
        }
    }

    /** A horn from the top-left corner, or the top-right one [mirrored]: its root under the body, its tip near the die's corner. */
    private fun DrawScope.drawHorn(mirrored: Boolean) {
        val s = size.minDimension
        fun x(fraction: Float) = if (mirrored) size.width - fraction * s else fraction * s
        val outline = Path().apply {
            moveTo(x(0.40f), 0.14f * s)
            quadraticTo(x(0.16f), -0.01f * s, x(-0.02f), -0.11f * s)
            quadraticTo(x(0.03f), 0.12f * s, x(0.10f), 0.36f * s)
            close()
        }
        drawPath(outline, Brush.linearGradient(listOf(hornLight, horn), start = Offset(x(0.3f), 0.2f * s), end = Offset(x(-0.02f), -0.1f * s)))
        // A thin highlight along its upper edge.
        val ridge = Path().apply {
            moveTo(x(0.32f), 0.08f * s)
            quadraticTo(x(0.14f), -0.01f * s, x(0f), -0.09f * s)
        }
        drawPath(ridge, lerp(hornLight, Color.White, 0.35f).copy(alpha = 0.6f), style = Stroke(width = 0.8.dp.toPx()))
    }

    /** A flame pip centred on [centre]: a teardrop pointing up, [radius] across its round base, a brighter one inside. */
    private fun DrawScope.drawFlame(centre: Offset, radius: Float) {
        // A teardrop's weight sits low, so it's drawn lower down the pip's spot to look centred on it.
        val base = centre + Offset(0f, radius * 0.35f)
        drawCircle(Brush.radialGradient(listOf(flame.copy(alpha = 0.45f), Color.Transparent), center = base, radius = radius * 2.2f), radius * 2.2f, base)
        drawPath(teardrop(base, radius), flame)
        scale(0.55f, pivot = base + Offset(0f, radius * 0.3f)) { drawPath(teardrop(base, radius), flameCore) }
    }

    private fun teardrop(base: Offset, r: Float): Path {
        val (cx, cy) = base
        return Path().apply {
            moveTo(cx, cy - 1.9f * r)
            cubicTo(cx + 0.25f * r, cy - 1.15f * r, cx + r, cy - 0.65f * r, cx + r, cy + 0.1f * r)
            cubicTo(cx + r, cy + 0.65f * r, cx + 0.55f * r, cy + r, cx, cy + r)
            cubicTo(cx - 0.55f * r, cy + r, cx - r, cy + 0.65f * r, cx - r, cy + 0.1f * r)
            cubicTo(cx - r, cy - 0.65f * r, cx - 0.25f * r, cy - 1.15f * r, cx, cy - 1.9f * r)
            close()
        }
    }
}
