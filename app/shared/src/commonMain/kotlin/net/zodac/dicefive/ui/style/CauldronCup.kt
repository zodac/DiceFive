package net.zodac.dicefive.ui.style

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// How long one bubble takes to rise, swell and pop, and one wisp of steam to rise and fade.
private const val CAULDRON_CYCLE_MILLIS = 2400

// The cauldron's rim and opening, and the brew's level: high enough to fill most of the opening seen
// from above, standing, with a band of the dark inner wall above it. Tipped all the way over (the
// cup's resting tilt), a pot this full would overflow - the brew drops to just under the lip on its
// low side (liquidIn) and what's lost is the drips down its side; it's full again next turn. Kept
// deep enough to hold every bit of its level down to that lip, the brew only showed as a strip
// behind the front of the rim, and the pot looked empty.
private const val BREW_RIM_Y = 18f
private const val BREW_MOUTH_RADIUS = 25f
private const val BREW_LEVEL_Y = 27f
/** A bubble in the brew: where on its surface ([u] across, [v] front to back, each -1..1), when in the cycle it starts, and how big it gets. */
private data class Bubble(val u: Float, val v: Float, val start: Float, val radius: Float)

private val CAULDRON_BUBBLES = listOf(
    Bubble(-0.5f, -0.2f, 0f, 1.6f),
    Bubble(0.3f, 0.3f, 0.23f, 1.3f),
    Bubble(0.6f, -0.4f, 0.47f, 1.1f),
    Bubble(-0.1f, 0.5f, 0.66f, 1.5f),
    Bubble(-0.7f, 0.35f, 0.81f, 1f),
    Bubble(0.1f, -0.5f, 0.12f, 1.2f),
)

/**
 * A round-bellied iron cauldron with a heavy lip and a ring handle hanging from a lug on each side,
 * holding a glowing [CupPalette.accent] brew a little below the rim - deeper [CupPalette.interior]
 * round its edge - that stays level and sloshes as the pot is shaken and tipped. The brew never stops working: bubbles rise, swell and pop, froth heaves round its edge, and
 * wisps of steam drift up off it. Drawn [CupShape.SQUAT], as wide as it is tall.
 */
class CauldronDiceCupStyle(override val id: String, private val palette: CupPalette) : DiceCupStyle {
    override val shape: CupShape = CupShape.SQUAT

    @Composable
    override fun Cup(rolling: Boolean, tilted: Boolean, modifier: Modifier) {
        // Only composed while animating: once the cup is spent the transition leaves composition
        // entirely, so it stops asking for frames, and the brew goes calm - no bubbles, froth or
        // steam - until the next turn starts it working again from the beginning.
        val brewCycle = rememberAmbientCycle(CAULDRON_CYCLE_MILLIS)
        CupCanvas(rolling, tilted, modifier, shape) {
            // Read only here, inside the draw, so each frame just repaints the cup rather than recomposing.
            val cycle = brewCycle?.value
            drawContactShadow(26f, 56f)

            // Ring handles, drawn first so the pot's edge hides where each passes behind it.
            for (x in listOf(4f, 72f)) {
                val ring = Rect(Offset(gx(x - 3.25f), gy(23.5f)), Size(gx(6.5f), gy(7.5f)))
                drawOval(palette.dark, topLeft = ring.topLeft, size = ring.size, style = Stroke(width = gy(2.8f)))
                drawOval(palette.light, topLeft = ring.topLeft, size = ring.size, style = Stroke(width = gy(0.8f)))
            }

            val belly = Path().apply {
                moveTo(gx(13f), gy(18f))
                cubicTo(gx(1f), gy(28f), gx(1f), gy(50f), gx(18f), gy(58f))
                quadraticTo(gx(38f), gy(66f), gx(58f), gy(58f))
                cubicTo(gx(75f), gy(50f), gx(75f), gy(28f), gx(63f), gy(18f))
                close()
            }
            drawPath(belly, roundShading(palette, 37f))

            // The lugs the rings hang from, riveted to the side just under the lip.
            for (x in listOf(4.5f, 66.5f)) {
                drawRoundRect(palette.mid, topLeft = Offset(gx(x), gy(19.5f)), size = Size(gx(5f), gy(6f)), cornerRadius = CornerRadius(gy(1f)))
                drawRoundRect(
                    palette.dark,
                    topLeft = Offset(gx(x), gy(19.5f)),
                    size = Size(gx(5f), gy(6f)),
                    cornerRadius = CornerRadius(gy(1f)),
                    style = Stroke(width = gy(0.6f)),
                )
                drawCircle(palette.light, radius = gy(0.8f), center = Offset(gx(x + 2.5f), gy(22.5f)))
            }

            drawBrew(cycle)
            drawDrips()
            if (cycle != null) drawSteam(cycle, belly)
        }
    }

    /**
     * The brew, a little below the rim, seen through the opening: the dark inner wall above it, then
     * its surface - deep round the edge, glowing in the middle, and, while it's working ([cycle]),
     * froth heaving round the glow and bubbles swelling and popping.
     *
     * It sloshes like any liquid in an opaque pot (see [liquidIn]), the froth and bubbles riding
     * on its surface. A null [cycle] is a spent cup's calm brew, just the glow.
     */
    private fun CupDrawScope.drawBrew(cycle: Float?) {
        val mouth = mouthBounds(BREW_MOUTH_RADIUS, BREW_RIM_Y)
        val brew = liquidIn(BREW_RIM_Y, BREW_MOUTH_RADIUS, BREW_LEVEL_Y)
        val twoPi = 2f * PI.toFloat()
        val froth = lerp(palette.accent, Color.White, 0.35f)
        val bubble = lerp(palette.accent, Color.White, 0.5f)

        // Everything in pixels up front: the cup grid can't be reached from inside clipPath.
        val volume = liquidVolume(brew, BREW_MOUTH_RADIUS)
        val glow = liquidRing(brew, 20f, drop = 0.6f)
        val frothDots = if (cycle == null) emptyList() else (0 until 12).map { i ->
            val angle = twoPi * i / 12
            val wobble = sin(twoPi * (cycle * 2f + i * 0.37f))
            onLiquid(brew, centreX + 19f * cos(angle), brew.level + 19f * CUP_VIEW_SQUASH * sin(angle)) to gy(1.6f + 0.6f * wobble)
        }
        val bubbles = if (cycle == null) emptyList() else CAULDRON_BUBBLES.map { b ->
            val t = (cycle + b.start) % 1f
            Triple(onLiquid(brew, centreX + b.u * 14f, brew.level + b.v * 14f * CUP_VIEW_SQUASH), gy(b.radius), t)
        }

        // Each bubble's highlight sits towards the light, up and to the left on screen, however the pot tilts.
        val glint = screenwards(-1f, -1f)
        clipPath(Path().apply { addOval(mouth) }) {
            // The far inner wall, showing above the brew.
            drawOval(lerp(palette.dark, palette.mid, 0.5f), topLeft = mouth.topLeft, size = mouth.size)
            drawPath(volume, palette.interior)
            drawPath(glow, palette.accent)
            for ((dot, radius) in frothDots) {
                drawCircle(froth.copy(alpha = 0.8f), radius = radius, center = dot)
            }
            for ((at, radius, t) in bubbles) {
                when {
                    // Swelling up...
                    t < 0.7f -> {
                        val r = radius * (t / 0.7f)
                        drawCircle(bubble, radius = r, center = at)
                        drawCircle(Color.White.copy(alpha = 0.7f), radius = r * 0.35f, center = at + glint * (r * 0.35f))
                    }
                    // ...then popping into a spreading, fading ring.
                    t < 0.85f -> {
                        val burst = (t - 0.7f) / 0.15f
                        drawCircle(
                            bubble.copy(alpha = 1f - burst),
                            radius = radius * (1f + burst * 1.5f),
                            center = at,
                            style = Stroke(width = radius * 0.3f),
                        )
                    }
                }
            }
        }
        drawOval(palette.dark, topLeft = mouth.topLeft, size = mouth.size, style = Stroke(width = gy(2.6f)))
        drawOval(palette.light, topLeft = mouth.topLeft, size = mouth.size, style = Stroke(width = gy(0.7f)))
    }

    /**
     * Three faint glowing puffs in the brew's colour, swelling and fading as they rise. Each starts
     * on the brew's own surface, so tipped over they come off wherever the brew has pooled, and rise
     * straight up on screen from there (see [screenwards]) - hidden wherever they'd be over the
     * pot's iron rather than above it.
     */
    private fun CupDrawScope.drawSteam(cycle: Float, belly: Path) {
        val brew = liquidIn(BREW_RIM_Y, BREW_MOUTH_RADIUS, BREW_LEVEL_Y)
        // Everything in pixels up front: the cup grid can't be reached from inside clipPath.
        val puffs = (0 until 3).map { i ->
            val t = (cycle + i / 3f) % 1f
            // Rising off the brew's own surface - so off wherever it has pooled when the pot is
            // tipped - then straight up on screen.
            val source = onLiquid(brew, centreX + (i - 1) * 10f, brew.level - 3f)
            val centre = source + screenwards(gx(sin(t * 2f * PI.toFloat()) * 3f), -gy(8f + t * 22f))
            Triple(centre, gy(3f + t * 4f), (1f - t) * 0.25f)
        }
        // Hidden over the pot's iron, showing only above it or through its opening: a puff still
        // inside the pot is behind its wall, not stuck to the outside of it.
        val opening = Path().apply { addOval(mouthBounds(BREW_MOUTH_RADIUS, BREW_RIM_Y)) }
        clipPath(Path.combine(PathOperation.Difference, belly, opening), clipOp = ClipOp.Difference) {
            for ((centre, radius, alpha) in puffs) {
                drawCircle(palette.accent.copy(alpha = alpha), radius = radius, center = centre)
            }
        }
    }

    /**
     * A few old drips of brew down the front of the pot from the lip - fixed, not part of the
     * sloshing, just there to say the cauldron's been used. Each a streak ending in a drop.
     */
    private fun CupDrawScope.drawDrips() {
        // Duller than the live brew - dried on the iron rather than glowing.
        val stain = lerp(palette.accent, palette.interior, 0.6f).copy(alpha = 0.9f)
        val sheen = lerp(palette.accent, Color.White, 0.2f).copy(alpha = 0.25f)
        for ((x, length, width) in listOf(Triple(24f, 6f, 1.6f), Triple(31f, 9f, 2f), Triple(50f, 4.5f, 1.4f))) {
            val across = (x - centreX) / BREW_MOUTH_RADIUS
            // Where the lip's front edge is at this point, so the drip starts right on it.
            val lip = BREW_RIM_Y + BREW_MOUTH_RADIUS * CUP_VIEW_SQUASH * sqrt(1f - across * across)
            val bottom = lip + length
            // Wide where it came over the lip, narrowing as it ran, ending in a bead.
            val drip = Path().apply {
                moveTo(gx(x - width), gy(lip - 0.6f))
                cubicTo(gx(x - width * 0.5f), gy(lip + length * 0.3f), gx(x - width * 0.3f), gy(lip + length * 0.7f), gx(x - width * 0.28f), gy(bottom))
                lineTo(gx(x + width * 0.28f), gy(bottom))
                cubicTo(gx(x + width * 0.3f), gy(lip + length * 0.7f), gx(x + width * 0.5f), gy(lip + length * 0.3f), gx(x + width), gy(lip - 0.6f))
                close()
            }
            drawPath(drip, stain)
            drawCircle(stain, radius = gx(width * 0.5f), center = Offset(gx(x), gy(bottom)))
            drawLine(sheen, Offset(gx(x - width * 0.2f), gy(lip + 0.5f)), Offset(gx(x - width * 0.15f), gy(lip + length * 0.6f)), strokeWidth = gx(width * 0.25f), cap = StrokeCap.Round)
        }
    }
}
