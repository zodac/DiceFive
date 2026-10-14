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
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// Backgrounds that go with a dice, cup or mat style of the same name, and are unlocked with it. Each is kept faint - the
// scorecard sits on top and has to stay the thing that reads - and painted once, unless it moves.

/**
 * Stone flags laid in running bond - to go with the Stone mat - cut from [kind]'s stone (or, with no [kind], marble
 * veined in [vein]), set in [BackgroundPalette.detail] grout, and veiled in the background's own dark so they sit back.
 * The flags are bigger than the mat's, which they'd otherwise match. Painted once.
 */
class StoneBackground(
    override val id: String,
    private val palette: BackgroundPalette,
    private val kind: StoneKind?,
    private val vein: Color = Color.Gray,
) : TableBackground {
    override val scoreAreaBrush: Brush = Brush.verticalGradient(listOf(palette.top, palette.bottom))

    override fun DrawScope.drawScoreAreaDecoration() = drawCachedSurface("background:$id") {
        paintFlagstones(palette.top, palette.bottom, palette.detail, kind, vein, id.hashCode(), flagWidth = 124.dp.toPx(), flagHeight = 84.dp.toPx())
        drawRect(Brush.verticalGradient(listOf(palette.bottom.copy(alpha = STONE_VEIL * 0.8f), palette.bottom.copy(alpha = STONE_VEIL))))
    }
}

/** How much of the background's own dark is laid over the flagstones: enough that a scorecard box stands clear of them. */
private const val STONE_VEIL = 0.55f

/** The strength a circuit board's traces are drawn at behind a scorecard, against 1 under dice. */
private const val CIRCUIT_BACKGROUND_STRENGTH = 0.35f

/** A printed circuit board - to go with the Circuit mat: traces in [BackgroundPalette.detail] running out from a couple of chips. Painted once. */
class CircuitBackground(override val id: String, private val palette: BackgroundPalette) : TableBackground {
    override val scoreAreaBrush: Brush = Brush.verticalGradient(listOf(palette.top, palette.bottom))

    override fun DrawScope.drawScoreAreaDecoration() = drawCachedSurface("background:$id") {
        // Chips a shade lighter than the board, or on a dark one only their pins would show.
        paintCircuitBoard(
            palette.detail,
            palette.bottom,
            CIRCUIT_BACKGROUND_STRENGTH,
            chip = lerp(palette.bottom, Color.White, 0.1f),
            chipMark = lerp(palette.bottom, Color.White, 0.2f),
        )
    }
}

// Where the Neon grid's horizon sits down the table, and how many lines run across and away from it.
private const val NEON_GRID_HORIZON = 0.4f
private const val NEON_GRID_ACROSS = 11
private const val NEON_GRID_AWAY = 13

/**
 * A neon grid running off to a glowing horizon - to go with the Neon dice, cup and mat: [BackgroundPalette.detail] is
 * the grid and the glow, [inner] the thin tube along the horizon. The grid is painted once; only the horizon's glow
 * swells and fades, in step with the Neon mat's pulse, and holds steady under reduced motion.
 */
class NeonGridBackground(override val id: String, private val palette: BackgroundPalette, private val inner: Color) : TableBackground {
    override val scoreAreaBrush: Brush = Brush.verticalGradient(listOf(palette.top, palette.bottom))

    private var seconds by mutableFloatStateOf(0f)

    @Composable
    override fun Animate() {
        TwinkleClock { seconds = it }
    }

    override fun DrawScope.drawScoreAreaDecoration() {
        val horizon = size.height * NEON_GRID_HORIZON
        drawCachedSurface("background:$id") { paintGrid(horizon) }
        val swell = 0.5f + 0.5f * sin(2f * PI.toFloat() * seconds / NEON_MAT_PULSE_SECONDS)
        val reach = size.height * 0.2f
        drawRect(
            Brush.verticalGradient(
                listOf(Color.Transparent, palette.detail.copy(alpha = 0.1f + 0.12f * swell), Color.Transparent),
                startY = horizon - reach,
                endY = horizon + reach * 0.6f,
            ),
            topLeft = Offset(0f, horizon - reach),
            size = Size(size.width, reach * 1.6f),
        )
    }

    private fun DrawScope.paintGrid(horizon: Float) {
        val line = 1.dp.toPx()
        val centre = size.width / 2f
        // Lines running away to the vanishing point, evenly spread along the bottom edge and out past either side of it.
        for (i in -NEON_GRID_ACROSS..NEON_GRID_ACROSS) {
            drawLine(palette.detail.copy(alpha = 0.3f), Offset(centre, horizon), Offset(centre + i * size.width / 6f, size.height), line)
        }
        // Lines across, bunching up towards the horizon as a floor seen at a slant does.
        for (k in 1..NEON_GRID_AWAY) {
            val t = k / NEON_GRID_AWAY.toFloat()
            val y = horizon + (size.height - horizon) * t * t
            drawLine(palette.detail.copy(alpha = 0.18f + 0.22f * t), Offset(0f, y), Offset(size.width, y), line)
        }
        // The lines crowd together at the horizon, so the table's own colour is laid back over them there.
        val ground = lerp(palette.top, palette.bottom, NEON_GRID_HORIZON)
        drawRect(
            Brush.verticalGradient(listOf(ground.copy(alpha = 0.95f), Color.Transparent), startY = horizon, endY = horizon + (size.height - horizon) * 0.3f),
            topLeft = Offset(0f, horizon),
            size = Size(size.width, (size.height - horizon) * 0.3f),
        )
        // The tube along the horizon, with a wider soft glow round it.
        drawLine(inner.copy(alpha = 0.16f), Offset(0f, horizon), Offset(size.width, horizon), 6.dp.toPx())
        drawLine(inner.copy(alpha = 0.55f), Offset(0f, horizon), Offset(size.width, horizon), 1.6.dp.toPx())
        drawLine(lerp(inner, Color.White, 0.6f).copy(alpha = 0.7f), Offset(0f, horizon), Offset(size.width, horizon), 0.6.dp.toPx())
    }
}

/**
 * A meadow seen from above - to go with the Meadow dice: mottled grass in tufts, wildflowers kept off the banks, and a
 * river winding down the table with sandy banks, deeper water down its middle and ripples along it. [palette] runs from
 * the grass's light to its dark. Painted once.
 */
class MeadowBackground(override val id: String, private val palette: BackgroundPalette) : TableBackground {
    override val scoreAreaBrush: Brush = Brush.verticalGradient(listOf(palette.top, palette.bottom))

    override fun DrawScope.drawScoreAreaDecoration() = drawCachedSurface("background:$id") {
        val random = Random(id.hashCode())
        val unit = 1.dp.toPx()
        val grassDark = palette.bottom
        val light = lerp(palette.top, Color.White, 0.22f)
        val dark = lerp(grassDark, Color.Black, 0.25f)

        // Mottled patches of lighter and darker grass.
        repeat(16) {
            val at = Offset(random.nextFloat() * size.width, random.nextFloat() * size.height)
            val r = (70f + random.nextFloat() * 110f) * unit
            val tint = if (random.nextBoolean()) lerp(palette.top, Color(0xFFE8F27A), 0.3f) else grassDark
            drawSoftSpot(tint.copy(alpha = 0.35f), at, r)
        }
        // Tufts of blades.
        repeat((size.width * size.height / (22 * unit * 22 * unit)).toInt()) {
            val at = Offset(random.nextFloat() * size.width, random.nextFloat() * size.height)
            val blade = (7f + random.nextFloat() * 6f) * unit
            val colour = (if (random.nextBoolean()) light else dark).copy(alpha = 0.4f)
            for (k in -1..1) {
                val lean = k * 0.45f + (random.nextFloat() - 0.5f) * 0.3f
                drawLine(colour, at, at + Offset(sin(lean) * blade, -cos(lean) * blade), strokeWidth = 1.6f * unit, cap = StrokeCap.Round)
            }
        }

        // The river: in at the top, out at the bottom, bending between.
        val course = listOf(
            Offset(0.72f, -0.03f), Offset(0.54f, 0.13f), Offset(0.3f, 0.28f), Offset(0.5f, 0.47f),
            Offset(0.76f, 0.64f), Offset(0.5f, 0.83f), Offset(0.4f, 1.03f),
        ).map { Offset(it.x * size.width, it.y * size.height) }
        val river = smoothPath(course)
        val round = { width: Float -> Stroke(width = width * unit, cap = StrokeCap.Butt, join = StrokeJoin.Round) }
        drawPath(river, lerp(grassDark, Color.Black, 0.35f).copy(alpha = 0.35f), style = round(RIVER_BANK_DP + 8f))
        drawPath(river, lerp(grassDark, RiverBank, 0.35f), style = round(RIVER_BANK_DP))
        drawPath(river, RiverBank, style = round(RIVER_WATER_DP + 5f))
        drawPath(river, RiverWater.deep, style = round(RIVER_WATER_DP))
        drawPath(river, RiverWater.base, style = round(RIVER_WATER_DP * 0.62f))
        val measure = PathMeasure().apply { setPath(river, false) }
        // Ripples: little arcs of light on the current, scattered along it, never in a line.
        repeat((measure.length / (13 * unit)).toInt()) {
            val distance = random.nextFloat() * measure.length
            val at = measure.getPosition(distance)
            val dir = measure.getTangent(distance)
            val normal = Offset(-dir.y, dir.x)
            val side = normal * ((random.nextFloat() - 0.5f) * RIVER_WATER_DP * 0.5f * unit)
            val half = dir * ((4f + random.nextFloat() * 3f) * unit)
            val bow = normal * (1.6f * unit)
            val ripple = Path().apply {
                moveTo(at.x + side.x - half.x, at.y + side.y - half.y)
                quadraticTo(at.x + side.x - bow.x, at.y + side.y - bow.y, at.x + side.x + half.x, at.y + side.y + half.y)
            }
            drawPath(ripple, RiverWater.light.copy(alpha = 0.3f + random.nextFloat() * 0.2f), style = Stroke(width = 2.2f * unit, cap = StrokeCap.Round))
        }

        // Wildflowers: tiny five-petal blooms with a yellow eye, thinly scattered and never on the river or its banks.
        val bank = measure.let { m -> List(80) { m.getPosition(it / 79f * m.length) } }
        val keepClear = (RIVER_BANK_DP / 2f + 6f) * unit
        repeat((size.width * size.height / (64 * unit * 64 * unit)).toInt()) {
            val at = Offset(random.nextFloat() * size.width, random.nextFloat() * size.height)
            val colour = Wildflowers[random.nextInt(Wildflowers.size)]
            if (bank.any { (it - at).getDistance() < keepClear }) return@repeat
            val petal = 2.1f * unit
            for (k in 0 until 5) {
                val a = k * 1.2566f
                drawCircle(colour.copy(alpha = 0.65f), petal, at + Offset(cos(a) * petal * 1.2f, sin(a) * petal * 1.2f))
            }
            drawCircle(Color(0xFFFFD54F), petal * 0.8f, at)
        }
        // Laid back a little, so the scorecard on top stays the thing that reads.
        drawRect(grassDark.copy(alpha = MEADOW_VEIL))
    }
}

/** How much of the grass's own dark is laid back over the meadow. */
private const val MEADOW_VEIL = 0.55f

// The river's widths in dp: its banks, and the water between them.
private const val RIVER_BANK_DP = 54f
private const val RIVER_WATER_DP = 32f
