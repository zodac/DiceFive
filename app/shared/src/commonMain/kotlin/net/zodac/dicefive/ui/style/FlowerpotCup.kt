package net.zodac.dicefive.ui.style

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import net.zodac.dicefive.model.FLOWERPOT_FULL_BLOOM
import net.zodac.dicefive.ui.common.LocalReduceMotion
import net.zodac.dicefive.ui.theme.FlowerpotLeaf
import net.zodac.dicefive.ui.theme.FlowerpotLeafDark
import net.zodac.dicefive.ui.theme.FlowerpotStem
import net.zodac.dicefive.ui.theme.SunflowerDisc
import net.zodac.dicefive.ui.theme.SunflowerPetal
import net.zodac.dicefive.ui.theme.SunflowerPetalShade
import net.zodac.dicefive.ui.theme.SunflowerSeed

// The flowerpot's soil (its plant's colours are in ui.theme, shared with Greenfingers' icon).
private val SoilTop = Color(0xFF5A3D28)
private val SoilBottom = Color(0xFF3E2819)
private val SoilGrainDark = Color(0xFF2A1A0F)
private val SoilGrainLight = Color(0xFF7A5738)

// The flowerpot's plant: where its stem leaves the soil, how long it takes to grow into its next
// stage, and how many sepals round its bud and petals in each of its two rings.
private const val PLANT_BASE_Y = 13.8f
private const val PLANT_GROW_MILLIS = 1_200
// The highest the plant may reach outside a game, where there's less room above the pot than on the
// board: enough for a Styles tile to show all of the Sunflower's bloom.
private const val PLANT_TOP_OUTSIDE_GAME = -26f
private const val SEPALS = 8
private const val PETALS_PER_RING = 12
// How the plant moves: how much of the pot's tilt it bends back against once settled (1 would stand
// it dead upright), how far past that a shake or a pour swings it on top of trailing behind, the most
// its tip ever bends either way, and how many straight pieces its curving stem is drawn in.
private const val PLANT_UPRIGHT_PULL = 0.8f
private const val PLANT_WOBBLE = 1f
private const val PLANT_MAX_BEND = 70f
private const val STEM_STEPS = 16

/**
 * A terracotta flowerpot: short and stout, tapering towards the base, with a thick collar round
 * the top whose rim is a flat ring round the soil - and a plant growing out of it, which wiggles as
 * the pot is shaken. Drawn [CupShape.SQUAT].
 *
 * An easter egg: in a game the plant grows with the current player's rolls ([LocalFlowerpotGrowth]),
 * from bare soil through a seedling, a stalk with a bud and the bud opening, to a sunflower in full
 * bloom for a player who uses every roll of every turn - which is what earns Greenfingers, and with
 * it the secret pots that hold the plant at each stage: one with a [fixedStage] never grows, and is
 * just a pot with that plant in it. Only the one with no [fixedStage] - the empty pot that grows -
 * does the game's Flowerpot tricks (the plant, standing back up to show off its bloom).
 */
class FlowerpotDiceCupStyle(
    override val id: String,
    private val palette: CupPalette,
    private val fixedStage: Int? = null,
) : DiceCupStyle {
    override val shape: CupShape = CupShape.SQUAT

    /** Whether this is the empty pot that grows its plant through a game, not one that holds a fixed stage. */
    val growsPlant: Boolean get() = fixedStage == null

    // A sunflower grown this game, not one the pot always has.
    override fun showsOffWhenSpent(growth: FlowerpotGrowth): Boolean = fixedStage == null && growth.stage == FLOWERPOT_FULL_BLOOM

    @Composable
    override fun Cup(rolling: Boolean, tilted: Boolean, modifier: Modifier) {
        val growth = LocalFlowerpotGrowth.current
        val stage = fixedStage ?: growth.stage
        // The same grower's plant grows into its next stage; another's replaces it outright.
        val plant = remember(growth.grower) { Animatable(stage.toFloat()) }
        // Under reduced motion it's simply at its new stage, with no growing into it.
        val reduceMotion = LocalReduceMotion.current
        LaunchedEffect(plant, stage) {
            if (reduceMotion) plant.snapTo(stage.toFloat()) else plant.animateTo(stage.toFloat(), tween(PLANT_GROW_MILLIS, easing = FastOutSlowInEasing))
        }
        CupCanvas(rolling, tilted, modifier, shape) {
            drawContactShadow(19f, 56f)
            drawPath(roundSection(24f, 19f, 18f, 56f), roundShading(palette, 24f))
            drawPath(roundSection(27f, 11f, 27f, 20f), roundShading(palette, 27f))
            drawPath(frontArcPath(27f, 20f), palette.dark, style = Stroke(width = gy(0.8f)))
            val rim = mouthBounds(27f, 11f)
            drawOval(palette.light, topLeft = rim.topLeft, size = rim.size)
            drawOpenMouth(23.5f, 11.6f, palette.dark, palette.interior, palette.dark, 0.6f)
            drawSoil()
            // Read only here, inside the draw, so growing just repaints the pot.
            drawPlant(plant.value, fitOutsideGame = growth.grower == null)
        }
    }

    /** Soil filling the pot to just below its rim, with a scatter of darker and lighter grains. */
    private fun CupDrawScope.drawSoil() {
        val opening = Path().apply { addOval(mouthBounds(23.5f, 11.6f)) }
        val soil = mouthBounds(23f, 13.2f)
        val random = Random(7)
        // Worked out here: the cup grid isn't reachable from inside clipPath's own DrawScope.
        val grains = List(40) {
            val angle = random.nextFloat() * 2f * PI.toFloat()
            val reach = sqrt(random.nextFloat())
            Triple(
                Offset(gx(centreX + cos(angle) * reach * 21f), gy(13.2f + sin(angle) * reach * 21f * CUP_VIEW_SQUASH)),
                gy(0.35f + random.nextFloat() * 0.5f),
                if (random.nextBoolean()) SoilGrainDark else SoilGrainLight,
            )
        }
        clipPath(opening) {
            drawOval(Brush.verticalGradient(listOf(SoilTop, SoilBottom), startY = soil.top, endY = soil.bottom), topLeft = soil.topLeft, size = soil.size)
            for ((centre, radius, colour) in grains) drawCircle(colour, radius, centre)
        }
    }

    /**
     * The plant, [growth] of the way from bare soil (0) to full bloom ([FLOWERPOT_FULL_BLOOM]) - in
     * between two stages it's part-way from one to the next, so a new stage grows into place. Up to
     * the seedling (1) it swells up out of the soil; past it, the stem climbs, a second pair of
     * leaves and a bud appear (2), still-green petals push out round the bud (3), and it opens into
     * a yellow sunflower facing out of the pot (4).
     *
     * It tries to stand upright: tip the pot and the stem bends back up towards vertical, more the
     * further along it is, so the head ends up nearly level (see [PLANT_UPRIGHT_PULL]). It's no stiffer
     * than a liquid, either - it follows the pot on the same springy lag ([CupPose.liquidRotation]),
     * so a shake sets it wobbling and a pour or a stand-up has it overshoot and settle. In full bloom it
     * stands well above the pot - on the board there's room over the cup - so with
     * [fitOutsideGame] it's shrunk to reach no higher than [PLANT_TOP_OUTSIDE_GAME].
     */
    private fun CupDrawScope.drawPlant(growth: Float, fitOutsideGame: Boolean) {
        if (growth <= 0f) return
        val g = growth.coerceIn(1f, FLOWERPOT_FULL_BLOOM.toFloat())
        // How far through each stage after the seedling: the stalk and bud, the bud opening, the bloom.
        val stalk = (g - 1f).coerceIn(0f, 1f)
        val opening = (g - 2f).coerceIn(0f, 1f)
        val bloom = (g - 3f).coerceIn(0f, 1f)

        // Everything in pixels up front: the cup grid can't be reached from inside rotate's own DrawScope.
        val baseY = PLANT_BASE_Y
        val topX = centreX + 0.5f * (1f - stalk)
        val topY = 4.5f - 8.5f * stalk - 8f * opening - 12f * bloom
        val bendX = centreX - 1.5f
        // The bend sits halfway up, so height along the stem is linear in the curve's parameter.
        fun stemAt(y: Float): Offset {
            val t = (baseY - y) / (baseY - topY)
            val x = (1 - t) * (1 - t) * centreX + 2 * t * (1 - t) * bendX + t * t * topX
            return Offset(x, y)
        }
        // How far the stem bends, in degrees, at its tip - each point along it bends in proportion to
        // how far up it is, so it curves rather than leaning as one stiff rod. Settled, it undoes most
        // of the pot's tilt; while that tilt is changing it trails behind, and swings past and back.
        val bend = (pose.liquidRotation * (1f - PLANT_UPRIGHT_PULL) - pose.rotation - pose.slosh * PLANT_WOBBLE)
            .coerceIn(-PLANT_MAX_BEND, PLANT_MAX_BEND)
        val pivot = Offset(gx(centreX), gy(baseY))
        /** How far up the stem grid height [y] is, 0 at the soil to 1 at its tip - what share of [bend] it takes. */
        fun along(y: Float) = ((baseY - y) / (baseY - topY)).coerceIn(0f, 1f)
        val stem = Path().apply {
            for (step in 0..STEM_STEPS) {
                val t = step / STEM_STEPS.toFloat()
                val x = (1 - t) * (1 - t) * centreX + 2 * t * (1 - t) * bendX + t * t * topX
                val point = p(Offset(x, baseY + (topY - baseY) * t)).rotatedAbout(pivot, bend * t)
                if (step == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
            }
        }
        val stemWidth = gy(1.1f + 0.8f * stalk + 0.5f * opening + 1.2f * bloom)

        // Each shape with how far up the stem it grows from, so it bends with its own part of the stem.
        val shapes = mutableListOf<Triple<Path, Color, Float>>()
        fun MutableList<Triple<Path, Color, Float>>.add(shape: Path, colour: Color, from: Offset) = add(Triple(shape, colour, along(from.y)))
        // The seedling's own two leaves, growing and left lower down as the stem climbs past them.
        val lowerNode = stemAt(5f + 5.5f * stalk)
        val lowerSize = 1f + 0.35f * stalk + 0.25f * opening + 0.6f * bloom
        shapes.add(leaf(lowerNode, -7f, -1.5f, 7.2f * lowerSize, 1.6f * lowerSize), FlowerpotLeafDark, lowerNode)
        shapes.add(leaf(lowerNode, 6f, -3.5f, 7f * lowerSize, 1.6f * lowerSize), FlowerpotLeaf, lowerNode)
        // A second, smaller pair higher up, sprouting as the stalk grows.
        val upperSize = ((stalk - 0.3f) / 0.7f).coerceIn(0f, 1f) * (1f + 0.3f * opening + 0.6f * bloom)
        if (upperSize > 0f) {
            val upperNode = stemAt(2f + 0.5f * opening + 0.5f * bloom)
            shapes.add(leaf(upperNode, 6.5f, -1f, 6.5f * upperSize, 1.4f * upperSize), FlowerpotLeafDark, upperNode)
            shapes.add(leaf(upperNode, -6f, -3f, 6f * upperSize, 1.3f * upperSize), FlowerpotLeaf, upperNode)
        }

        // The head, at the top of the stem: a bud that swells, then petals push out round it and it
        // opens into a sunflower.
        val budRadius = 3f * ((stalk - 0.5f) / 0.5f).coerceIn(0f, 1f) + 2f * opening + 6f * bloom
        val petalLength = 4f * opening + 9f * bloom
        if (budRadius > 0f) {
            val centre = Offset(topX, topY)
            // Green sepals behind everything, the points of the bud's casing: barely past it while
            // it's closed, then folding back to let the petals out.
            for (i in 0 until SEPALS) {
                val angle = 2f * PI.toFloat() * (i + 0.5f) / SEPALS - PI.toFloat() / 2f
                val reach = budRadius * (1.2f + 0.3f * opening)
                shapes.add(petal(centre, angle, budRadius * 0.5f, reach, budRadius * 0.4f), FlowerpotLeafDark, centre)
            }
            if (petalLength > 0f) {
                // Two rings, the back one a shade darker and half a petal round, so they fan out full.
                // Still green while they push out of the bud, turning yellow only as it opens fully.
                val rings = listOf(
                    0.5f to lerp(FlowerpotLeafDark, SunflowerPetalShade, bloom),
                    0f to lerp(FlowerpotLeaf, SunflowerPetal, bloom),
                )
                for ((offset, colour) in rings) {
                    for (i in 0 until PETALS_PER_RING) {
                        val angle = 2f * PI.toFloat() * (i + offset) / PETALS_PER_RING - PI.toFloat() / 2f
                        val width = minOf(3.8f, 0.3f + petalLength * 0.3f)
                        shapes.add(petal(centre, angle, budRadius * 0.8f, budRadius + petalLength, width), colour, centre)
                    }
                }
            }
            shapes.add(
                Path().apply { addOval(Rect(p(centre) - Offset(gx(budRadius), gy(budRadius)), Size(gx(2f * budRadius), gy(2f * budRadius)))) },
                lerp(FlowerpotLeaf, SunflowerDisc, bloom),
                centre,
            )
            // The seeds, in rings round the disc, darkening in as it opens.
            if (bloom > 0f) {
                for ((ring, count) in listOf(0.25f to 6, 0.5f to 12, 0.72f to 18, 0.9f to 24)) {
                    for (i in 0 until count) {
                        val angle = 2f * PI.toFloat() * (i + ring) / count
                        val seed = centre + Offset(cos(angle), sin(angle)) * (budRadius * ring)
                        val r = budRadius * 0.055f
                        shapes.add(
                            Path().apply { addOval(Rect(p(seed) - Offset(gx(r), gy(r)), Size(gx(2f * r), gy(2f * r)))) },
                            SunflowerSeed.copy(alpha = bloom),
                            centre,
                        )
                    }
                }
            }
        }

        // In a game the plant can stand well above the pot, into the room over the cup. Anywhere else
        // (a Styles tile, the menu's logo) there's far less, so a tall one is shrunk to fit it.
        val plantTop = topY - (if (budRadius > 0f) budRadius + petalLength else 0f)
        val fit = if (fitOutsideGame) ((baseY - PLANT_TOP_OUTSIDE_GAME) / (baseY - plantTop)).coerceAtMost(1f) else 1f
        scale(scale = growth.coerceAtMost(1f) * fit, pivot = pivot) {
            drawPath(stem, FlowerpotStem, style = Stroke(width = stemWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
            // Turned about the stem's foot by its own share of the bend, which puts it back on the stem
            // wherever the bend has taken that point.
            for ((shape, colour, height) in shapes) {
                rotate(degrees = bend * height, pivot = pivot) { drawPath(shape, colour) }
            }
        }
    }

    /** A grid point in pixels. */
    private fun CupDrawScope.p(point: Offset) = Offset(gx(point.x), gy(point.y))

    /** This point turned [degrees] clockwise about [pivot], as [rotate] would draw it. */
    private fun Offset.rotatedAbout(pivot: Offset, degrees: Float): Offset {
        val radians = degrees * PI.toFloat() / 180f
        val (dx, dy) = this - pivot
        return pivot + Offset(dx * cos(radians) - dy * sin(radians), dx * sin(radians) + dy * cos(radians))
    }

    /**
     * A leaf growing from [node] towards ([dx], [dy]), a direction: [length] long and [halfWidth]
     * either side of its midrib at its widest, all in grid units.
     */
    private fun CupDrawScope.leaf(node: Offset, dx: Float, dy: Float, length: Float, halfWidth: Float): Path {
        val along = Offset(dx, dy) / sqrt(dx * dx + dy * dy)
        val across = Offset(-along.y, along.x)
        val tip = node + along * length
        // A little lift towards the tip, so it curls up rather than sticking out straight.
        val middle = node + along * (length * 0.45f)
        return Path().apply {
            moveTo(p(node).x, p(node).y)
            (middle + across * (halfWidth * 2f)).let { quadraticTo(p(it).x, p(it).y, p(tip).x, p(tip).y) }
            (middle - across * (halfWidth * 2f)).let { quadraticTo(p(it).x, p(it).y, p(node).x, p(node).y) }
            close()
        }
    }

    /**
     * A petal (or sepal) pointing out from [centre] at [angle] radians: from [inner] to [outer] grid
     * units out, [halfWidth] either side at its widest.
     */
    private fun CupDrawScope.petal(centre: Offset, angle: Float, inner: Float, outer: Float, halfWidth: Float): Path {
        val along = Offset(cos(angle), sin(angle))
        val across = Offset(-along.y, along.x)
        val base = centre + along * inner
        val tip = centre + along * outer
        val middle = centre + along * (inner + (outer - inner) * 0.45f)
        return Path().apply {
            moveTo(p(base).x, p(base).y)
            (middle + across * (halfWidth * 2f)).let { quadraticTo(p(it).x, p(it).y, p(tip).x, p(tip).y) }
            (middle - across * (halfWidth * 2f)).let { quadraticTo(p(it).x, p(it).y, p(base).x, p(base).y) }
            close()
        }
    }
}
