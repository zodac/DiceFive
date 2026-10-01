package net.zodac.dicefive.ui.game.style

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import net.zodac.dicefive.ui.game.style.MeadowDiceStyle.Companion.RIVERS
import net.zodac.dicefive.ui.theme.GoldAccent

// How wide the river runs, its banks and its water, as fractions of the face.
private const val BANK_WIDTH = 0.2f
private const val WATER_WIDTH = 0.135f

/**
 * A green meadow seen from above, with a river winding across it in the shape of the die's number -
 * every river rising at one edge of the face and running out at another (see [RIVERS]). The grass is
 * tufted and dotted with wildflowers, the river has sandy banks, deeper water down its middle and
 * ripples running along it, and every face of every die is a little different. On a coloured roll
 * the meadow takes the roll's face colours and the river its pip colour.
 */
class MeadowDiceStyle(
    override val id: String,
    private val grass: Color,
    private val grassDark: Color,
    private val water: Shades = RiverWater,
    private val bank: Color = RiverBank,
    private val flowers: List<Color> = Wildflowers,
    private val heldRing: Color = GoldAccent,
) : DiceStyle, Swatched {
    override val swatch: Color = grass

    override fun recoloured(palette: DieColourPalette): DiceStyle = MeadowDiceStyle(
        id,
        palette.diceTop,
        palette.diceBottom,
        Shades.of(palette.pip),
        lerp(palette.diceBottom, palette.pip, 0.3f),
        listOf(lerp(palette.diceTop, Color.White, 0.6f)),
        palette.heldRing,
    )

    override val cornerPercent: Int = STYLED_DIE_CORNER_PERCENT

    @Composable
    override fun Die(value: Int, held: Boolean, modifier: Modifier) {
        val pattern = naturalPatternSeed(value)
        StyledDie(
            value = value,
            held = held,
            modifier = modifier,
            face = Brush.linearGradient(listOf(grass, grassDark)),
            edge = grassDark,
            pipColor = water.base,
            pipShape = PipShape.CUSTOM,
            heldRingColor = heldRing,
        ) {
            // All of it on the face itself, edge to edge - the rivers run off its sides.
            drawCachedSurface(Meadow(value, grass, grassDark, water, bank, flowers, pattern)) { paintMeadow(value, pattern) }
        }
    }

    private data class Meadow(
        val value: Int,
        val grass: Color,
        val grassDark: Color,
        val water: Shades,
        val bank: Color,
        val flowers: List<Color>,
        val pattern: Int,
    )

    private fun DrawScope.paintMeadow(value: Int, pattern: Int) {
        val random = Random(pattern * 7 + 11)
        val m = size.minDimension
        paintGrass(random)
        val rivers = RIVERS.getValue(value).map { course -> course.map { Offset(it.x * size.width, it.y * size.height) } }
        // A gentle meander on each bend, so no two rivers quite match - but never at a river's mouth,
        // which must stay on the face's edge.
        val wandering = rivers.map { course ->
            course.mapIndexed { i, p ->
                if (i == 0 || i == course.lastIndex) p else p + Offset((random.nextFloat() - 0.5f) * m * 0.03f, (random.nextFloat() - 0.5f) * m * 0.03f)
            }
        }
        val paths = wandering.map { smoothPath(it) }
        val round = { width: Float -> Stroke(width = width, cap = StrokeCap.Butt, join = StrokeJoin.Round) }
        // Banks first for every branch, so where two meet the water flows together: a soft dark
        // margin of damp earth and rushes, then a thin edge of mud right at the water.
        for (path in paths) drawPath(path, lerp(grassDark, Color.Black, 0.35f).copy(alpha = 0.35f), style = round(m * (BANK_WIDTH + 0.04f)))
        for (path in paths) drawPath(path, lerp(grassDark, bank, 0.35f), style = round(m * BANK_WIDTH))
        for (path in paths) drawPath(path, bank, style = round(m * (WATER_WIDTH + 0.025f)))
        for (path in paths) drawPath(path, water.deep, style = round(m * WATER_WIDTH))
        for (path in paths) drawPath(path, water.base, style = round(m * WATER_WIDTH * 0.62f))
        // Ripples: little arcs of light on the current, scattered along it, never in a line.
        for (path in paths) {
            val measure = PathMeasure().apply { setPath(path, false) }
            val count = (measure.length / (m * 0.06f)).toInt()
            repeat(count) {
                val distance = random.nextFloat() * measure.length
                val at = measure.getPosition(distance)
                val dir = measure.getTangent(distance)
                val normal = Offset(-dir.y, dir.x)
                val side = normal * ((random.nextFloat() - 0.5f) * m * WATER_WIDTH * 0.5f)
                val half = dir * (m * (0.02f + random.nextFloat() * 0.015f))
                val bow = normal * (m * 0.008f)
                val from = at + side - half
                val to = at + side + half
                val ripple = Path().apply {
                    moveTo(from.x, from.y)
                    quadraticTo(at.x + side.x - bow.x, at.y + side.y - bow.y, to.x, to.y)
                }
                drawPath(ripple, water.light.copy(alpha = 0.55f + random.nextFloat() * 0.3f), style = Stroke(width = m * 0.011f, cap = StrokeCap.Round))
            }
        }
        // A few reeds and flowers along the banks, then the flowers out in the meadow.
        paintFlowers(random)
    }

    /** Grass: mottled light and dark patches, then many small tufts of blades. */
    private fun DrawScope.paintGrass(random: Random) {
        val w = size.width
        val h = size.height
        val m = size.minDimension
        repeat(7) {
            val at = Offset(random.nextFloat() * w, random.nextFloat() * h)
            val r = m * (0.15f + random.nextFloat() * 0.25f)
            val tint = if (random.nextBoolean()) lerp(grass, Color(0xFFE8F27A), 0.35f) else grassDark
            drawCircle(Brush.radialGradient(listOf(tint.copy(alpha = 0.4f), Color.Transparent), at, r), r, at)
        }
        val light = lerp(grass, Color.White, 0.25f)
        val dark = lerp(grassDark, Color.Black, 0.2f)
        repeat(90) {
            val at = Offset(random.nextFloat() * w, random.nextFloat() * h)
            val blade = m * (0.025f + random.nextFloat() * 0.025f)
            val colour = (if (random.nextBoolean()) light else dark).copy(alpha = 0.55f)
            for (k in -1..1) {
                val lean = k * 0.45f + (random.nextFloat() - 0.5f) * 0.3f
                drawLine(colour, at, at + Offset(sin(lean) * blade, -cos(lean) * blade), strokeWidth = m * 0.008f, cap = StrokeCap.Round)
            }
        }
    }

    /** Wildflowers: tiny five-dot blooms with a yellow eye, scattered thinly. */
    private fun DrawScope.paintFlowers(random: Random) {
        val m = size.minDimension
        repeat(9) {
            val at = Offset(random.nextFloat() * size.width, random.nextFloat() * size.height)
            val petal = m * 0.012f
            val colour = flowers[random.nextInt(flowers.size)]
            for (k in 0 until 5) {
                val a = k * 1.2566f
                drawCircle(colour, petal, at + Offset(cos(a) * petal * 1.2f, sin(a) * petal * 1.2f))
            }
            drawCircle(Color(0xFFFFD54F), petal * 0.8f, at)
        }
    }

    companion object {
        /**
         * Each value's river (or rivers, for a number with a branch), as points across the face from
         * 0 to 1, each starting and ending on the face's edge: the number is the river's course.
         */
        val RIVERS: Map<Int, List<List<Offset>>> = mapOf(
            // A 1: in from the left edge, up to the top of its stroke, then straight down and out.
            1 to listOf(listOf(Offset(0f, 0.42f), Offset(0.3f, 0.27f), Offset(0.55f, 0.13f), Offset(0.56f, 0.45f), Offset(0.55f, 0.75f), Offset(0.54f, 1f))),
            // A 2: in from the left, over its arch, down the diagonal and out along the bottom.
            2 to listOf(
                listOf(
                    Offset(0f, 0.3f), Offset(0.27f, 0.17f), Offset(0.52f, 0.12f), Offset(0.73f, 0.22f), Offset(0.72f, 0.43f),
                    Offset(0.5f, 0.62f), Offset(0.28f, 0.8f), Offset(0.52f, 0.83f), Offset(0.78f, 0.81f), Offset(1f, 0.83f),
                ),
            ),
            // A 3: in from the left, round its two bowls, and back out on the left.
            3 to listOf(
                listOf(
                    Offset(0f, 0.18f), Offset(0.3f, 0.13f), Offset(0.6f, 0.12f), Offset(0.76f, 0.27f), Offset(0.62f, 0.45f),
                    Offset(0.45f, 0.5f), Offset(0.66f, 0.57f), Offset(0.78f, 0.73f), Offset(0.6f, 0.88f), Offset(0.3f, 0.87f), Offset(0f, 0.8f),
                ),
            ),
            // A 4: the upright runs top to bottom, and a branch leaves it near its head, runs down
            // the diagonal and out across the right edge.
            4 to listOf(
                listOf(Offset(0.67f, 0f), Offset(0.67f, 0.35f), Offset(0.66f, 0.7f), Offset(0.67f, 1f)),
                listOf(Offset(0.67f, 0.12f), Offset(0.45f, 0.36f), Offset(0.22f, 0.62f), Offset(0.5f, 0.64f), Offset(0.8f, 0.62f), Offset(1f, 0.63f)),
            ),
            // A 5: in from the right along its top bar, down its upright, round its bowl and out left.
            5 to listOf(
                listOf(
                    Offset(1f, 0.15f), Offset(0.7f, 0.14f), Offset(0.32f, 0.15f), Offset(0.29f, 0.45f), Offset(0.55f, 0.42f),
                    Offset(0.77f, 0.6f), Offset(0.62f, 0.85f), Offset(0.3f, 0.86f), Offset(0f, 0.78f),
                ),
            ),
            // A 6: down from the top edge, round its loop, then out through the left edge.
            6 to listOf(
                listOf(
                    Offset(0.72f, 0f), Offset(0.48f, 0.15f), Offset(0.3f, 0.42f), Offset(0.3f, 0.7f), Offset(0.5f, 0.87f),
                    Offset(0.73f, 0.72f), Offset(0.62f, 0.5f), Offset(0.38f, 0.52f), Offset(0.15f, 0.62f), Offset(0f, 0.66f),
                ),
            ),
        )
    }
}

private val RiverWater = Shades(Color(0xFF1C5C9E), Color(0xFF3D8FD6), Color(0xFFC4E8FF))
private val RiverBank = Color(0xFF8A7448)
private val Wildflowers = listOf(Color.White, Color(0xFFFFF59D), Color(0xFFF8BBD0), Color(0xFFB39DDB))
