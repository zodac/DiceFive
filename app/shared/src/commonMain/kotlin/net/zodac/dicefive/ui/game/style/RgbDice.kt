package net.zodac.dicefive.ui.game.style

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import net.zodac.dicefive.ui.theme.GoldAccent

private const val RGB_CORNER_PERCENT = 14

/** Where one lamp's thrown-off patch of light falls: its [angle], how far [away] and how wide [reach] (in lens radii), and its [alpha]. */
private class Scatter(val angle: Float, val away: Float, val reach: Float, val alpha: Float)

// Every lamp of every face, seeded - made once, so a frame allocates nothing for it.
private val SCATTER: List<List<Scatter>> = List(6) { face ->
    List(6) { lamp ->
        val random = Random((face + 1) * 31 + lamp)
        Scatter(random.nextFloat() * 2f * PI.toFloat(), 2.2f + random.nextFloat() * 2.2f, 3f + random.nextFloat() * 2.5f, 0.14f + random.nextFloat() * 0.08f)
    }
}

// The colours: a full trip round the hues every RGB_CYCLE_SECONDS. Each face starts RGB_FACE_OFFSET of a
// cycle after the one before it - so every die showing a number matches, and different numbers differ.
// A wave die also has each lamp RGB_WAVE_SPREAD of a cycle behind the one to its left, like a keyboard's.
private const val RGB_CYCLE_SECONDS = 6f
private const val RGB_WAVE_SPREAD = 0.35f
private const val RGB_FACE_OFFSET = 1f / 6f

/**
 * RGB dice: a black panel with every pip a small LED in a recessed socket - a domed lens, a white-hot
 * core and a bloom on the panel round it - cycling through the colours of the rainbow like the lighting
 * on a gaming keyboard. By default the whole face changes together, with a strong glow lighting the
 * panel; with [wave], a wave of colour sweeps across the lamps instead. Each number starts its cycle
 * at its own point, the same on every die. Still, in one colour ([still]) once recoloured, and under
 * reduced motion (see [rememberArtSeconds]).
 */
class RgbDiceStyle(
    override val id: String,
    private val panel: Color,
    private val panelShade: Color = panel,
    private val wave: Boolean = false,
    private val still: Color? = null,
    private val heldRing: Color = GoldAccent,
) : DiceStyle, Swatched {
    override val swatch: Color = still ?: Color(0xFFFF3B6B)
    override val bodyColor: Color = panel
    override val cornerPercent: Int = RGB_CORNER_PERCENT

    // A pale panel (the white dice) can't be lit by its lamps the way a dark one is, so their light is turned up to show.
    private val light = panel.luminance() > 0.5f

    /** Its colour is its light: the panel stays as it is, the lamps take the roll's colour and stop cycling. */
    override fun recoloured(palette: DieColourPalette): DiceStyle = RgbDiceStyle(id, panel, panelShade, wave, palette.swatch, palette.heldRing)

    @Composable
    override fun Die(value: Int, held: Boolean, modifier: Modifier) {
        val seconds = rememberArtSeconds()
        StyledDie(
            value = value,
            held = held,
            modifier = modifier,
            face = Brush.linearGradient(listOf(if (light) panel else lerp(panel, Color.White, 0.07f), panelShade)),
            edge = if (light) lerp(panelShade, Color.Black, 0.2f) else lerp(panel, Color.White, 0.2f),
            pipColor = swatch,
            cornerPercent = RGB_CORNER_PERCENT,
            pipShape = PipShape.CUSTOM,
            pipPadding = 0.dp,
            customPips = { drawLeds(it, seconds.value / RGB_CYCLE_SECONDS + value * RGB_FACE_OFFSET) },
            heldRingColor = heldRing,
        )
    }

    /**
     * The lamps for [value], the wave at [phase] (in cycles): each lamp's hue follows its position across
     * the face. Only the colour changes from frame to frame, so the light - the sockets, the blooms, the
     * scattered patches, the wash - is painted once as white and tinted as it's drawn, and a lamp's lens is
     * a few flat circles: no gradient is built on a frame. A face that cycles together (Rainbow) has all
     * its light in one image, tinted once; a wave tints each lamp's bloom its own colour.
     */
    private fun DrawScope.drawLeds(value: Int, phase: Float) {
        val lens = size.minDimension * 0.062f
        fun colourAt(position: Offset) = still ?: Color.hsv((((phase - if (wave) (position.x + position.y * 0.5f) * RGB_WAVE_SPREAD * 2f else 0f) % 1f) + 1f) % 1f * 360f, 0.9f, 1f)
        fun tint(colour: Color) = ColorFilter.tint(colour, BlendMode.SrcIn)
        val centre = Offset(size.width / 2f, size.height / 2f)
        val layout = pipLayout(value)
        // The light spilling onto the panel first, so every lamp sits on top of its neighbours' glow.
        if (wave && !light) {
            // Each lamp's bloom its own colour.
            val reach = lens * 3.4f
            for (position in layout) {
                val spot = Offset(position.x * size.width, position.y * size.height)
                val colour = colourAt(position)
                drawCircle(Brush.radialGradient(listOf(colour.copy(alpha = 0.55f), colour.copy(alpha = 0.2f), Color.Transparent), center = spot, radius = reach), reach, spot)
            }
        }
        // (What's tinted as one - all of a rainbow's light, all of a pale wave's - takes the middle's colour, so the colour wave reads in a pale wave's lenses.)
        // (A dark wave has none: every bit of its light is its own blooms.)
        if (!(wave && !light)) {
            cachedSurface(RgbLight(value, light, wave)) { paintLight(value, light, wave) }
                ?.let { drawImage(it, colorFilter = tint(colourAt(Offset(0.5f, 0.5f)))) }
        }
        for (position in layout) {
            val spot = Offset(position.x * size.width, position.y * size.height)
            val colour = colourAt(position)
            // The socket: a metal ring and the dark well the lens sits in; then the lens: its colour, white-hot at the core, a glint off-centre towards the light.
            drawCircle(Color(0xFF6B6F78), lens * 1.5f, spot)
            drawCircle(Color(0xFF0A0A0C), lens * 1.28f, spot)
            drawCircle(colour, lens, spot)
            drawCircle(lerp(colour, Color.White, 0.8f), lens * 0.45f, spot)
            drawCircle(Color.White.copy(alpha = 0.85f), lens * 0.2f, Offset(spot.x - lens * 0.38f, spot.y - lens * 0.4f))
        }
    }

    /**
     * White light, for [value]'s face, to be tinted: all the face's light that isn't a bloom of a lamp's own colour. On a
     * pale panel ([light]) a soft patch thrown off to the side of each lamp - the same every frame and on every die
     * showing this number, so the glow is lopsided and a little scattered, like light through a diffuser - and, for a
     * face that cycles together (not a [wave]), the blooms too, thinning out smoothly (more softly, and further, on a
     * pale panel, which needs it), and on a dark panel a faint wash over the whole of it.
     */
    private fun DrawScope.paintLight(value: Int, light: Boolean, wave: Boolean) {
        val lens = size.minDimension * 0.062f
        val middle = Offset(size.width / 2f, size.height / 2f)
        fun bloomAt(at: Offset) = drawCircle(
            if (light) {
                Brush.radialGradient(
                    0f to Color.White.copy(alpha = 0.42f),
                    0.22f to Color.White.copy(alpha = 0.26f),
                    0.5f to Color.White.copy(alpha = 0.1f),
                    0.78f to Color.White.copy(alpha = 0.03f),
                    1f to Color.Transparent,
                    center = at,
                    radius = lens * 5.5f,
                )
            } else {
                Brush.radialGradient(
                    0f to Color.White.copy(alpha = 0.55f),
                    0.5f to Color.White.copy(alpha = 0.2f),
                    1f to Color.Transparent,
                    center = at,
                    radius = lens * 4.2f * 1.35f,
                )
            },
            lens * if (light) 5.5f else 4.2f * 1.35f,
            at,
        )
        if (!wave && !light) {
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.16f), Color.Transparent), center = middle, radius = size.minDimension * 0.6f), size.minDimension * 0.6f, middle)
        }
        for ((index, position) in pipLayout(value).withIndex()) {
            val spot = Offset(position.x * size.width, position.y * size.height)
            if (!wave || light) bloomAt(spot)
            if (light) {
                val scatter = SCATTER[value.coerceIn(1, 6) - 1][index]
                val at = spot + Offset(cos(scatter.angle), sin(scatter.angle)) * (lens * scatter.away)
                val reach = lens * scatter.reach
                drawCircle(
                    Brush.radialGradient(
                        0f to Color.White.copy(alpha = scatter.alpha),
                        0.6f to Color.White.copy(alpha = 0.04f),
                        1f to Color.Transparent,
                        center = at,
                        radius = reach,
                    ),
                    reach,
                    at,
                )
            }
        }
    }
}

private data class RgbLight(val value: Int, val light: Boolean, val wave: Boolean)
