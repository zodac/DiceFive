package net.zodac.dicefive.ui.style

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import net.zodac.dicefive.ui.theme.MartiniGlassSwatch
import net.zodac.dicefive.ui.theme.MartiniLiquidSwatch
import net.zodac.dicefive.ui.theme.MartiniOliveHighlightSwatch
import net.zodac.dicefive.ui.theme.MartiniOliveSwatch
import net.zodac.dicefive.ui.theme.MartiniPickSwatch

/**
 * A glass tumbler: see-through [tint] glass with bright edges and a vertical highlight, the whole
 * rim outlined since the far side shows through, and a [liquid] inside that stays level and
 * sloshes as the glass is shaken and poured (see [liquidIn]).
 */
class GlassDiceCupStyle(override val id: String, private val tint: Color, private val liquid: Color) : DiceCupStyle, Swatched {
    override val swatch: Color = liquid

    @Composable
    override fun Cup(rolling: Boolean, tilted: Boolean, modifier: Modifier) {
        CupCanvas(rolling, tilted, modifier) {
            drawContactShadow(17f, 77f, alpha = 0.2f)
            val body = roundSection(20f, 10f, 17f, 77f)
            // One tint over body and rim together: laid separately, the rim oval doubled it over the
            // body's half of the opening, leaving a lighter-looking hemisphere.
            val mouth = mouthBounds(20f, 10f)
            drawPath(Path.combine(PathOperation.Union, body, Path().apply { addOval(mouth) }), tint.copy(alpha = 0.18f))
            drawLiquidInGlass(body, liquidIn(rimY = 10f, rimRadius = 20f, restLevel = 38f), liquid, surfaceRadius = taper(20f, 10f, 17f, 77f, 38f))

            val edge = Color.White.copy(alpha = 0.5f)
            drawLine(edge, Offset(gx(9f), gy(10f)), Offset(gx(12f), gy(77f)), strokeWidth = gy(1f))
            drawLine(edge, Offset(gx(49f), gy(10f)), Offset(gx(46f), gy(77f)), strokeWidth = gy(1f))
            drawPath(frontArcPath(17f, 77f), edge, style = Stroke(width = gy(1f)))
            // The thick glass base, as a second, fainter line just above the bottom edge.
            drawPath(frontArcPath(17.2f, 73.5f), Color.White.copy(alpha = 0.25f), style = Stroke(width = gy(0.8f)))
            drawLine(
                Color.White.copy(alpha = 0.45f),
                Offset(gx(15f), gy(16f)),
                Offset(gx(16.5f), gy(70f)),
                strokeWidth = gy(1.6f),
                cap = StrokeCap.Round,
            )
            drawLine(
                Color.White.copy(alpha = 0.18f),
                Offset(gx(43f), gy(16f)),
                Offset(gx(41.5f), gy(70f)),
                strokeWidth = gy(1f),
                cap = StrokeCap.Round,
            )

            drawOval(Color.White.copy(alpha = 0.75f), topLeft = mouth.topLeft, size = mouth.size, style = Stroke(width = gy(1.1f)))
        }
    }
}

/**
 * A laboratory beaker: wide, see-through [tint] glass with white measurement marks, a pouring spout
 * on the rim, and a [liquid] that stays level and sloshes as it's shaken and poured. Drawn
 * [CupShape.SQUAT], as squat as a real beaker.
 */
class BeakerDiceCupStyle(override val id: String, private val tint: Color, private val liquid: Color) : DiceCupStyle, Swatched {
    override val swatch: Color = liquid
    override val shape: CupShape = CupShape.SQUAT

    @Composable
    override fun Cup(rolling: Boolean, tilted: Boolean, modifier: Modifier) {
        CupCanvas(rolling, tilted, modifier, shape) {
            drawContactShadow(23f, 56f, alpha = 0.2f)
            val body = roundSection(23f, 12f, 23f, 56f)
            val rim = mouthBounds(23f, 12f)
            drawPath(Path.combine(PathOperation.Union, body, Path().apply { addOval(rim) }), tint.copy(alpha = 0.22f))
            drawLiquidInGlass(body, liquidIn(rimY = 12f, rimRadius = 23f, restLevel = 36f), liquid, surfaceRadius = 23f)

            val glass = Color.White.copy(alpha = 0.55f)
            drawLine(glass, Offset(gx(15f), gy(12f)), Offset(gx(15f), gy(56f)), strokeWidth = gy(1f))
            drawLine(glass, Offset(gx(61f), gy(12f)), Offset(gx(61f), gy(56f)), strokeWidth = gy(1f))
            drawPath(frontArcPath(23f, 56f), glass, style = Stroke(width = gy(1f)))
            for ((index, y) in listOf(22f, 29f, 36f, 43f, 50f).withIndex()) {
                val length = if (index % 2 == 0) 8f else 5f
                drawLine(Color.White.copy(alpha = 0.75f), Offset(gx(20f), gy(y)), Offset(gx(20f + length), gy(y)), strokeWidth = gy(0.8f))
            }

            drawOval(Color.White.copy(alpha = 0.8f), topLeft = rim.topLeft, size = rim.size, style = Stroke(width = gy(1.1f)))
            val spout = Path().apply {
                moveTo(gx(16.5f), gy(9f))
                quadraticTo(gx(11f), gy(8f), gx(10f), gy(10.5f))
                quadraticTo(gx(12.5f), gy(11f), gx(16.2f), gy(13.5f))
            }
            drawPath(spout, Color.White.copy(alpha = 0.8f), style = Stroke(width = gy(1f)))
        }
    }
}

// The martini glass, on the TALL grid: a wide conical bowl narrowing to a point, a thin stem and a
// round foot. The drink rests a little below the rim; the olive floats on it.
private const val MARTINI_RIM_Y = 14f
private const val MARTINI_RIM_RADIUS = 25f
private const val MARTINI_BOWL_TIP_Y = 48f
private const val MARTINI_BOWL_TIP_RADIUS = 1.6f
private const val MARTINI_FOOT_Y = 73f
private const val MARTINI_FOOT_RADIUS = 15f
private const val MARTINI_LEVEL_Y = 25f
private const val OLIVE_RADIUS = 3.6f
// The olive rides on rails: a short stretch of the drink's surface, centred a little left of the
// centre line, that it can't leave however hard the glass is shaken. It's this far (grid units) either
// way from the middle of its rail; the slosh nudges it by at most OLIVE_SWING_MAX of that, and leans
// its pick by at most PICK_LEAN_MAX.
private const val OLIVE_RAIL_CENTRE = -3f
private const val OLIVE_RAIL_HALF_LENGTH = 5f
private const val OLIVE_SWING_PER_DEGREE = 0.25f
private const val OLIVE_SWING_MAX = 1.5f
private const val PICK_LEAN_BASE = 6f
private const val PICK_LEAN_PER_DEGREE = 0.15f
private const val PICK_LEAN_MAX = 2f

/**
 * A martini glass, like the Shaken, Not Tapped icon's: a see-through [glass] bowl on a thin stem
 * and foot, with a [drink] that stays level and sloshes as the glass is shaken and poured (see
 * [liquidIn]) and an olive on a cocktail pick floating on it. The olive rides the drink's surface -
 * washing towards the low side as the glass tips, and swinging past where it's going and back as it
 * is shaken - and its pick with it, but only along a short rail: it moves a few units and no more,
 * however the glass is thrown about. Drawn [CupShape.TALL].
 */
class MartiniDiceCupStyle(
    override val id: String,
    private val glass: Color = MartiniGlassSwatch,
    private val drink: Color = MartiniLiquidSwatch,
) : DiceCupStyle, Swatched {
    override val swatch: Color = drink

    @Composable
    override fun Cup(rolling: Boolean, tilted: Boolean, modifier: Modifier) {
        CupCanvas(rolling, tilted, modifier) {
            drawContactShadow(MARTINI_FOOT_RADIUS, MARTINI_FOOT_Y, alpha = 0.2f)
            val metal = CupPalette(
                dark = lerp(glass, Color.Black, 0.45f),
                light = Color.White,
                mid = glass,
                accent = glass,
                interior = glass,
            )
            // Foot, then stem: solid enough to read against any table.
            drawPath(roundSection(MARTINI_FOOT_RADIUS, MARTINI_FOOT_Y - 2.4f, MARTINI_FOOT_RADIUS, MARTINI_FOOT_Y), roundShading(metal, MARTINI_FOOT_RADIUS).let { it })
            drawPath(roundSection(1.9f, MARTINI_BOWL_TIP_Y, 2.3f, MARTINI_FOOT_Y - 2.4f), roundShading(metal, 2.3f))
            val foot = mouthBounds(MARTINI_FOOT_RADIUS, MARTINI_FOOT_Y - 2.4f)
            drawOval(glass.copy(alpha = 0.9f), topLeft = foot.topLeft, size = foot.size)

            val bowl = roundSection(MARTINI_RIM_RADIUS, MARTINI_RIM_Y, MARTINI_BOWL_TIP_RADIUS, MARTINI_BOWL_TIP_Y)
            val rim = mouthBounds(MARTINI_RIM_RADIUS, MARTINI_RIM_Y)
            drawPath(Path.combine(PathOperation.Union, bowl, Path().apply { addOval(rim) }), glass.copy(alpha = 0.2f))
            val liquid = liquidIn(MARTINI_RIM_Y, MARTINI_RIM_RADIUS, MARTINI_LEVEL_Y)
            drawLiquidInGlass(
                bowl,
                liquid,
                drink,
                surfaceRadius = taper(MARTINI_RIM_RADIUS, MARTINI_RIM_Y, MARTINI_BOWL_TIP_RADIUS, MARTINI_BOWL_TIP_Y, liquid.level),
            )

            val edge = Color.White.copy(alpha = 0.55f)
            drawLine(edge, Offset(gx(centreX - MARTINI_RIM_RADIUS), gy(MARTINI_RIM_Y)), Offset(gx(centreX - MARTINI_BOWL_TIP_RADIUS), gy(MARTINI_BOWL_TIP_Y)), strokeWidth = gy(1f))
            drawLine(edge, Offset(gx(centreX + MARTINI_RIM_RADIUS), gy(MARTINI_RIM_Y)), Offset(gx(centreX + MARTINI_BOWL_TIP_RADIUS), gy(MARTINI_BOWL_TIP_Y)), strokeWidth = gy(1f))
            drawOval(Color.White.copy(alpha = 0.8f), topLeft = rim.topLeft, size = rim.size, style = Stroke(width = gy(1.1f)))
            // A glint down the bowl's lit side.
            drawLine(
                Color.White.copy(alpha = 0.4f),
                Offset(gx(centreX - 17f), gy(MARTINI_RIM_Y + 3f)),
                Offset(gx(centreX - 6f), gy(MARTINI_RIM_Y + 21f)),
                strokeWidth = gy(1.3f),
                cap = StrokeCap.Round,
            )
            drawOlive(liquid)
        }
    }

    /**
     * The olive on its pick, floating on the drink: it drifts to the low side of a tilted surface and
     * sways a little with the slosh, but stays on its rail (and inside the drink's edge). The pick
     * leans out of it a little with the slosh, always standing up on screen.
     */
    private fun CupDrawScope.drawOlive(liquid: Liquid) {
        val surfaceRadius = taper(MARTINI_RIM_RADIUS, MARTINI_RIM_Y, MARTINI_BOWL_TIP_RADIUS, MARTINI_BOWL_TIP_Y, liquid.level)
        val edge = (surfaceRadius - OLIVE_RADIUS - 1.5f).coerceAtLeast(0f)
        val halfLength = minOf(OLIVE_RAIL_HALF_LENGTH, edge)
        val tilt = (liquid.slope * 40f).coerceIn(-1f, 1f) * halfLength
        val sway = (-pose.slosh * OLIVE_SWING_PER_DEGREE).coerceIn(-OLIVE_SWING_MAX, OLIVE_SWING_MAX)
        val x = centreX + OLIVE_RAIL_CENTRE + (tilt + sway).coerceIn(-halfLength, halfLength)
        // Half sunk: its centre a little above the surface.
        val centre = onLiquid(liquid, x, liquid.level - OLIVE_RADIUS * 0.45f)
        val lean = PICK_LEAN_BASE + (pose.slosh * PICK_LEAN_PER_DEGREE).coerceIn(-PICK_LEAN_MAX, PICK_LEAN_MAX)
        val tip = centre + screenwards(gx(lean), -gy(15f))
        drawLine(MartiniPickSwatch, centre, tip, strokeWidth = gy(0.9f), cap = StrokeCap.Round)
        drawCircle(MartiniPickSwatch, radius = gy(1.1f), center = tip)
        val radius = gy(OLIVE_RADIUS)
        drawCircle(MartiniOliveSwatch, radius = radius, center = centre)
        // The pimento, and the highlight on its lit side.
        drawCircle(Color(0xFFB3261E), radius = radius * 0.35f, center = centre + Offset(radius * 0.25f, radius * 0.1f))
        drawCircle(MartiniOliveHighlightSwatch, radius = radius * 0.3f, center = centre - Offset(radius * 0.4f, radius * 0.4f))
    }
}
