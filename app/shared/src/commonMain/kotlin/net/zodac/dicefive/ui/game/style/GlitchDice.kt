package net.zodac.dicefive.ui.game.style

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import kotlin.math.floor
import kotlin.random.Random
import net.zodac.dicefive.ui.theme.GoldAccent

private const val GLITCH_CORNER_PERCENT = 14

// A glitch's timing. Time runs in short slots; a burst lasts GLITCH_BURST_SLOTS of them at most - so the
// face is never more than GLITCH_SLOT_SECONDS * GLITCH_BURST_SLOTS (150ms) from reading cleanly, within the
// 250ms cap. Each die has one burst in every GLITCH_WINDOW seconds, somewhere in its first
// GLITCH_WINDOW_SPREAD - so bursts come 1.2 to 4.6 seconds apart, on each die's own uneven beat.
internal const val GLITCH_SLOT_SECONDS = 0.05f
internal const val GLITCH_BURST_SLOTS = 3
private const val GLITCH_WINDOW = 3f
private const val GLITCH_WINDOW_SPREAD = 1.6f

// The small glitches round the face's border: one in every GLITCH_EDGE_WINDOW seconds for each die,
// somewhere in its first GLITCH_EDGE_SPREAD, each up to GLITCH_BURST_SLOTS slots long - and never any
// further in from the edge than GLITCH_EDGE_BAND of the die, which is clear of every pip on every face.
private const val GLITCH_EDGE_WINDOW = 0.5f
private const val GLITCH_EDGE_SPREAD = 0.3f
private const val GLITCH_EDGE_BAND = 0.17f

// The colour fringes either side of the pips: always a hair apart, flung wider in a burst.
private val GlitchCyan = Color(0xFF00F0FF)
private val GlitchMagenta = Color(0xFFFF2BD6)

/**
 * How hard a die is glitching [seconds] in: 0 when it's clean, rising to 1 at a burst's worst slot.
 * Bursts come at seeded, uneven intervals for each die ([dieIndex]), so the five never glitch together.
 */
internal fun glitchStrength(dieIndex: Int, seconds: Float): Float {
    val window = floor(seconds / GLITCH_WINDOW).toInt()
    val random = Random(dieIndex * 104729 + window * 31 + 7)
    val start = window * GLITCH_WINDOW + random.nextFloat() * GLITCH_WINDOW_SPREAD
    val length = 1 + random.nextInt(GLITCH_BURST_SLOTS)
    val slot = floor((seconds - start) / GLITCH_SLOT_SECONDS).toInt()
    // Its middle slot is the worst of it; either side, a lighter jolt.
    return if (slot in 0 until length) (if (slot == length / 2) 1f else 0.55f) else 0f
}

/**
 * Which slot of a small border glitch a die ([dieIndex]) is in [seconds] in - a seed for how it looks,
 * or null when there's none. Far more often than the full bursts of [glitchStrength], each die on its own beat.
 */
internal fun edgeGlitch(dieIndex: Int, seconds: Float): Int? {
    if (seconds == 0f) return null
    val window = floor(seconds / GLITCH_EDGE_WINDOW).toInt()
    val random = Random(dieIndex * 7919 + window * 131 + 3)
    val start = window * GLITCH_EDGE_WINDOW + random.nextFloat() * GLITCH_EDGE_SPREAD
    val slot = floor((seconds - start) / GLITCH_SLOT_SECONDS).toInt()
    return if (slot in 0 until 1 + random.nextInt(GLITCH_BURST_SLOTS)) window * 8 + slot else null
}

/**
 * Glitch dice: a dark screen of a face, faint scanlines across it, its pips lit with red-blue colour
 * fringes a hair either side - and every so often, each die on its own beat, a burst of corruption:
 * the face sliced into bands that jump sideways, the fringes flung wide, stray blocks of noise. A
 * burst is over within [GLITCH_BURST_SLOTS] slots (150ms), so the roll is never hidden for longer. In
 * between, every second or so, a small glitch flickers round the face's border only - a torn strip of
 * its edge jumping sideways, a sliver of colour, a block of noise - never reaching in as far as the
 * pips (see [edgeGlitch]). Still, and clean, under reduced motion.
 */
class GlitchDiceStyle(
    override val id: String,
    private val face: Color,
    private val pip: Color,
    private val scanline: Color,
    private val heldRing: Color = GoldAccent,
) : DiceStyle, Swatched {
    override val swatch: Color = face
    override val cornerPercent: Int = GLITCH_CORNER_PERCENT

    override fun recoloured(palette: DieColourPalette): DiceStyle = GlitchDiceStyle(id, palette.diceTop, palette.pip, palette.diceBottom, palette.heldRing)

    @Composable
    override fun Die(value: Int, held: Boolean, modifier: Modifier) {
        val dieIndex = LocalDieIndex.current
        val seconds = rememberArtSeconds()
        Canvas(modifier = modifier.dieShadow(RoundedCornerShape(GLITCH_CORNER_PERCENT))) {
            val strength = glitchStrength(dieIndex, seconds.value)
            val slot = floor(seconds.value / GLITCH_SLOT_SECONDS).toInt()
            val edge = if (strength == 0f) edgeGlitch(dieIndex, seconds.value) else null
            if (strength == 0f) {
                drawGlitchFace(value, fringe = 0.7.dp.toPx())
                if (edge != null) drawEdgeGlitch(value, Random(dieIndex * 389 + edge))
            } else {
                // The face cut into bands, each drawn shifted its own way - the clean face never shows whole.
                val random = Random(dieIndex * 977 + slot)
                val bands = 3 + random.nextInt(3)
                var top = 0f
                for (band in 0 until bands) {
                    val bottom = if (band == bands - 1) size.height else top + size.height * (0.12f + random.nextFloat() * 0.3f)
                    val shift = (random.nextFloat() * 2f - 1f) * size.width * 0.16f * strength
                    clipRect(top = top, bottom = bottom.coerceAtMost(size.height)) {
                        translate(left = shift) { drawGlitchFace(value, fringe = (1.2f + 2.4f * strength).dp.toPx()) }
                    }
                    top = bottom
                    if (top >= size.height) break
                }
                // Stray blocks of noise, a few, small enough that the pips still show between them.
                repeat(if (strength == 1f) 3 + random.nextInt(2) else 1 + random.nextInt(2)) {
                    val w = size.width * (0.1f + random.nextFloat() * 0.3f)
                    val h = size.height * (0.03f + random.nextFloat() * 0.06f)
                    drawRect(
                        if (random.nextBoolean()) GlitchCyan.copy(alpha = 0.55f) else GlitchMagenta.copy(alpha = 0.55f),
                        topLeft = Offset(random.nextFloat() * (size.width - w), random.nextFloat() * (size.height - h)),
                        size = Size(w, h),
                    )
                }
            }
            val corner = CornerRadius(size.minDimension * GLITCH_CORNER_PERCENT / 100f)
            drawRoundRect(
                if (held) heldRing else scanline.copy(alpha = 0.9f),
                cornerRadius = corner,
                style = Stroke(width = if (held) 2.dp.toPx() else 1.dp.toPx()),
            )
        }
    }

    /**
     * A small glitch round the border, over the clean face: a strip or two of the face's edge torn and
     * shifted sideways, tinted, and a block of noise - all within [GLITCH_EDGE_BAND] of the edge.
     */
    private fun DrawScope.drawEdgeGlitch(value: Int, random: Random) {
        val band = size.minDimension * GLITCH_EDGE_BAND
        repeat(1 + random.nextInt(2)) {
            val tint = if (random.nextBoolean()) GlitchCyan else GlitchMagenta
            val shift = (random.nextFloat() * 2f - 1f) * size.minDimension * 0.06f
            // A strip along one side: left or right runs up the edge, top or bottom along it.
            val along = size.minDimension * (0.12f + random.nextFloat() * 0.3f)
            val strip = when (random.nextInt(4)) {
                0 -> Rect(0f, random.nextFloat() * (size.height - along), band, 0f).let { it.copy(bottom = it.top + along) }
                1 -> Rect(size.width - band, random.nextFloat() * (size.height - along), size.width, 0f).let { it.copy(bottom = it.top + along) }
                2 -> Rect(random.nextFloat() * (size.width - along), 0f, 0f, band * 0.6f).let { it.copy(right = it.left + along) }
                else -> Rect(random.nextFloat() * (size.width - along), size.height - band * 0.6f, 0f, size.height).let { it.copy(right = it.left + along) }
            }
            clipRect(strip.left, strip.top, strip.right, strip.bottom) {
                translate(left = shift) { drawGlitchFace(value, fringe = 0.7.dp.toPx()) }
                drawRect(tint.copy(alpha = 0.22f))
            }
            drawLine(tint.copy(alpha = 0.8f), Offset(strip.left, strip.top), Offset(strip.right, strip.top), strokeWidth = 1.dp.toPx())
        }
        if (random.nextBoolean()) {
            val w = band * (0.4f + random.nextFloat() * 0.5f)
            val h = size.height * (0.03f + random.nextFloat() * 0.05f)
            val x = if (random.nextBoolean()) random.nextFloat() * (band - w) else size.width - band + random.nextFloat() * (band - w)
            drawRect(if (random.nextBoolean()) GlitchCyan.copy(alpha = 0.6f) else GlitchMagenta.copy(alpha = 0.6f), Offset(x, random.nextFloat() * (size.height - h)), Size(w, h))
        }
    }

    /** The clean face: the screen, its scanlines, and its pips with colour fringes [fringe] either side. */
    private fun DrawScope.drawGlitchFace(value: Int, fringe: Float) {
        val corner = CornerRadius(size.minDimension * GLITCH_CORNER_PERCENT / 100f)
        val screen = Path().apply { addRoundRect(RoundRect(Rect(Offset.Zero, size), corner)) }
        val pitch = 2.dp.toPx()
        clipPath(screen) {
            drawRect(face)
            var y = 0f
            while (y < size.height) {
                drawLine(scanline.copy(alpha = 0.35f), Offset(0f, y), Offset(size.width, y), strokeWidth = pitch * 0.4f)
                y += pitch
            }
        }
        val inset = size.minDimension * 0.12f
        val inner = Size(size.width - inset * 2, size.height - inset * 2)
        val radius = size.minDimension * 0.085f
        fun pips(colour: Color, dx: Float, mode: BlendMode) = translate(inset + dx, inset) {
            for (spot in pipLayout(value)) drawCircle(colour, radius, Offset(spot.x * inner.width, spot.y * inner.height), blendMode = mode)
        }
        pips(GlitchMagenta.copy(alpha = 0.85f), fringe, BlendMode.Screen)
        pips(GlitchCyan.copy(alpha = 0.85f), -fringe, BlendMode.Screen)
        pips(pip, 0f, BlendMode.SrcOver)
    }
}
