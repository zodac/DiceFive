package net.zodac.dicefive.ui.game.style

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.launch
import net.zodac.dicefive.ui.common.LocalReduceMotion

/** A volcano's colours: its rock, light to dark, the lava it pours, crust to white-hot core, and its ash. */
class VolcanoPalette(
    val rockLight: Color,
    val rock: Color,
    val rockDark: Color,
    val lava: Lava,
    val ash: Color,
)

// The cone, on the squat grid (76 x 66): its crater's rim and its base, both rings seen from the
// cups' usual raised angle. Its slopes are concave - steep under the crater, spreading out at the foot.
private const val CRATER_Y = 17f
private const val CRATER_RADIUS = 10.5f
private const val BASE_Y = 50f
private const val BASE_RADIUS = 31f
private const val SLOPE_CURVE = 1.6f

// The rumble while shaken: gentle, a smooth wave of the top half from side to side, one loop carrying it all.
private const val RUMBLE_LOOP_MILLIS = 1000
private const val RUMBLE_SWAY = 0.9f
private const val RUMBLE_FADE_MILLIS = 120
// How far down the cone the rumble reaches (it's all at the top, none from here down), and how many
// bands the cached cone is drawn in while it's moving.
private const val SWAY_DEPTH = 0.5f
private const val SWAY_BANDS = 24
// How many rocks ring its foot.
private const val ROCKS = 40

// The eruption: how long the burst lasts, how long the lava takes to run down, and how quickly it
// cools away when the next shake starts.
private const val ERUPTION_MILLIS = 1500
private const val FLOW_MILLIS = 1300
private const val COOL_MILLIS = 220
// How far the eruption throws the top sideways, at its first jolt, in grid units.
private const val JOLT_SWAY = 2.4f

// The lava flows down its front: where each leaves the rim (radians round from the front), how far
// down the slope it gets (1 is the foot), when it starts in the flow's time, and how wide it runs.
private class LavaFlow(val angle: Float, val reach: Float, val delay: Float, val width: Float, val wander: Float)

private val Flows = listOf(
    LavaFlow(-0.95f, 0.78f, 0.12f, 2.3f, 0.35f),
    LavaFlow(-0.35f, 0.92f, 0f, 3.6f, -0.25f),
    LavaFlow(0.15f, 0.62f, 0.2f, 2.4f, 0.3f),
    LavaFlow(0.55f, 0.86f, 0.05f, 3.3f, 0.2f),
    LavaFlow(1.1f, 0.7f, 0.15f, 2.2f, -0.3f),
)

/**
 * A volcano, instead of a cup: shaken, it rumbles gently, puffing a little smoke; poured, it doesn't
 * tip but erupts - a jolt, a flash in the crater, lava thrown high in glowing blobs and a plume of
 * ash - while lava wells over the rim and runs down its slopes. It rests glowing, the lava where it
 * stopped, until the next shake cools it away. A volcano that first appears with the roll already
 * poured (a game being continued) shows its lava at rest, without the eruption; under reduced motion
 * there's no rumble or burst, and the lava is simply there.
 */
class VolcanoDiceCupStyle(override val id: String, private val palette: VolcanoPalette) : DiceCupStyle, Swatched {
    override val shape: CupShape = CupShape.SQUAT
    override val swatch: Color = palette.lava.glow

    @Composable
    override fun Cup(rolling: Boolean, tilted: Boolean, modifier: Modifier) {
        val reduceMotion = LocalReduceMotion.current
        val erupted = tilted && !rolling
        // How far down the slopes the lava has run, and how hot it still is; a continued game's at rest.
        val flow = remember { Animatable(if (erupted) 1f else 0f) }
        val heat = remember { Animatable(if (erupted) 1f else 0f) }
        // The burst, 0 to 1 - 1 once it's over, and where it starts.
        val burst = remember { Animatable(1f) }
        var eruptionSeed by remember { mutableIntStateOf(0) }
        LaunchedEffect(erupted) {
            if (!erupted) {
                // Under reduced motion it's simply cold again, with no cooling between.
                if (reduceMotion) heat.snapTo(0f) else heat.animateTo(0f, tween(COOL_MILLIS))
                flow.snapTo(0f)
            } else if (flow.value < 1f) {
                eruptionSeed = Random.nextInt()
                heat.snapTo(1f)
                if (reduceMotion) {
                    flow.snapTo(1f)
                } else {
                    launch { burst.snapTo(0f); burst.animateTo(1f, tween(ERUPTION_MILLIS, easing = LinearEasing)) }
                    flow.animateTo(1f, tween(FLOW_MILLIS, easing = FastOutSlowInEasing))
                }
            }
        }
        val rumbleWeight by animateFloatAsState(if (rolling) 1f else 0f, tween(RUMBLE_FADE_MILLIS), label = "volcanoRumbleFade") // i18n: not translated - an animation label, not shown
        // The rumble's clock only exists while it counts, so a still volcano asks for no frames.
        val rumble: State<Float>? = if (rolling || rumbleWeight > 0f) rememberRumbleLoop() else null

        Canvas(modifier = modifier) {
            val scope = CupDrawScope(this, shape, CupPose(0f, 0f))
            // Read here, inside the draw, so the rumble only repaints the cup. Only the top half
            // moves - its foot stays planted on the table (see [drawCone]).
            val t = (rumble?.value ?: 0f) * 2f * PI.toFloat()
            val b = burst.value
            val jolt = if (b < 1f) JOLT_SWAY * (1f - b).pow(3) * sin(b * 70f) else 0f
            val sway = rumbleWeight * RUMBLE_SWAY * (sin(6f * t) * 0.7f + sin(11f * t + 1f) * 0.3f) + jolt
            scope.drawVolcano(flow.value, heat.value, b, eruptionSeed, sway, if (rumble != null && rumbleWeight > 0f) rumble.value to rumbleWeight else null)
        }
    }

    /**
     * The radius of the cone at [h] (0 at the crater's rim to 1 at its foot), [angle] radians round
     * from its front: its concave profile, made uneven - a few broad swells and hollows that wander
     * as they go down, and a ragged rim - so it's a mountain, not a lathe-turned cone.
     */
    private fun radiusAt(h: Float, angle: Float): Float {
        val profile = CRATER_RADIUS + (BASE_RADIUS - CRATER_RADIUS) * h.pow(SLOPE_CURVE)
        val swells = 0.09f * sin(3f * angle + 1.3f + 3f * h) + 0.05f * sin(5f * angle + 0.4f - 6f * h) + 0.03f * sin(11f * angle + 2f + 9f * h)
        val ragged = 0.07f * sin(17f * angle + 0.7f) * (1f - h).pow(6)
        return profile * (1f + swells + ragged)
    }

    /**
     * Where the point [angle] radians round from the front of the cone, [h] of the way down it, is
     * drawn - with its top half [sway]ed that far sideways (grid units, at the rim), fading to
     * nothing halfway down: the rumble moves the peak, never the foot.
     */
    private fun CupDrawScope.onCone(angle: Float, h: Float, sway: Float = 0f): Offset {
        val r = radiusAt(h, angle)
        val y = CRATER_Y + (BASE_Y - CRATER_Y) * h
        return Offset(gx(centreX + r * sin(angle) + sway * swayWeight(h)), gy(y + r * CUP_VIEW_SQUASH * cos(angle)))
    }

    /** How much of the rumble's sway reaches [h] down the cone: all of it at the top (and above), none from halfway down. */
    private fun swayWeight(h: Float): Float {
        val w = ((SWAY_DEPTH - h) / SWAY_DEPTH).coerceIn(0f, 1f)
        return w * w * (3f - 2f * w)
    }

    private fun CupDrawScope.drawVolcano(flow: Float, heat: Float, burst: Float, seed: Int, sway: Float, rumble: Pair<Float, Float>?) {
        drawCone(sway)
        if (burst < 1f) drawFlash(burst, sway)
        drawCrater(heat, sway)
        if (heat > 0f && flow > 0f) drawFlows(flow, heat, sway)
        if (burst < 1f) drawEruption(burst, seed, sway)
        if (rumble != null) drawRumbleSmoke(rumble.first, rumble.second, sway)
    }

    /**
     * The cone, its shadow and its ring of rocks, painted once per size - and stamped whole when it's
     * still, or while it [sway]s in thin bands, each shifted by how far up it is, so the top trembles
     * while the foot stays put, with no seam between them.
     */
    private fun CupDrawScope.drawCone(sway: Float) {
        val image = cachedSurface(VolcanoBody(id)) { CupDrawScope(this, CupShape.SQUAT, CupPose(0f, 0f)).paintCone() } ?: return
        if (sway == 0f) {
            drawImage(image)
            return
        }
        val height = image.height
        val split = gy(CRATER_Y + (BASE_Y - CRATER_Y) * SWAY_DEPTH).toInt().coerceIn(0, height)
        drawImage(image, srcOffset = IntOffset(0, split), srcSize = IntSize(image.width, height - split), dstOffset = IntOffset(0, split), dstSize = IntSize(image.width, height - split))
        val bands = SWAY_BANDS
        for (band in 0 until bands) {
            val top = split * band / bands
            val bottom = split * (band + 1) / bands
            if (bottom <= top) continue
            val gridY = (top + bottom) / 2f / size.height * shape.gridHeight
            val h = (gridY - CRATER_Y) / (BASE_Y - CRATER_Y)
            translate(left = gx(sway * swayWeight(h))) {
                drawImage(image, srcOffset = IntOffset(0, top), srcSize = IntSize(image.width, bottom - top), dstOffset = IntOffset(0, top), dstSize = IntSize(image.width, bottom - top))
            }
        }
    }

    private data class VolcanoBody(val id: String)

    /** The cone's outline: down its left slope, round the front of its foot, back up its right. */
    private fun CupDrawScope.cone(): Path = Path().apply {
        val steps = 48
        for (i in 0..steps) {
            val p = onCone(-PI.toFloat() / 2f, i / steps.toFloat())
            if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
        }
        for (i in 0..48) {
            val p = onCone(-PI.toFloat() / 2f + PI.toFloat() * i / 48, 1f)
            lineTo(p.x, p.y)
        }
        for (i in steps downTo 0) {
            val p = onCone(PI.toFloat() / 2f, i / steps.toFloat())
            lineTo(p.x, p.y)
        }
        close()
    }

    /**
     * The cone: its shadow on the table (its own footprint, cast right and back), rock shaded round
     * its curve and broken up - patches of lighter and darker stone, ledges of rock running round it,
     * cracks, a scatter of scree and ash dusting its upper slopes - and a ring of rocks of every size
     * heaped round its foot. No channels down it: the lava finds its own way when it erupts.
     */
    private fun CupDrawScope.paintCone() {
        val random = Random(id.hashCode())
        for (layer in 0 until 3) {
            val r = BASE_RADIUS + 3f + layer * 1.5f
            drawOval(
                Color.Black.copy(alpha = 0.16f),
                topLeft = Offset(gx(centreX + 2.5f - r), gy(BASE_Y + 1f - r * CUP_VIEW_SQUASH)),
                size = Size(gx(2f * r), gy(2f * r * CUP_VIEW_SQUASH)),
            )
        }
        val cone = cone()
        drawPath(
            cone,
            Brush.horizontalGradient(
                0f to palette.rockDark,
                0.32f to palette.rockLight,
                0.6f to palette.rock,
                1f to palette.rockDark,
                startX = gx(centreX - BASE_RADIUS),
                endX = gx(centreX + BASE_RADIUS),
            ),
        )
        // Everything in pixels up front: the grid isn't reachable inside clipPath.
        fun anywhere() = onCone((random.nextFloat() - 0.5f) * 3.2f, random.nextFloat())
        val patches = List(24) { Triple(anywhere(), gy(2.5f + random.nextFloat() * 5f), random.nextBoolean()) }
        val ledges = List(7) {
            val h = 0.12f + random.nextFloat() * 0.8f
            val from = (random.nextFloat() - 0.5f) * 3f
            val span = 0.4f + random.nextFloat() * 0.9f
            val upper = Path()
            val lower = Path()
            for (i in 0..10) {
                val a = from + span * i / 10f
                val wobble = 0.008f * sin(a * 4f + h * 20f)
                val p = onCone(a, h + wobble)
                val q = onCone(a, h + wobble + 0.018f)
                if (i == 0) {
                    upper.moveTo(p.x, p.y)
                    lower.moveTo(q.x, q.y)
                } else {
                    upper.lineTo(p.x, p.y)
                    lower.lineTo(q.x, q.y)
                }
            }
            upper to lower
        }
        val flecks = List(170) {
            val at = anywhere()
            val r = gy(0.3f + random.nextFloat() * 0.7f)
            val corners = 4 + random.nextInt(2)
            val turn = random.nextFloat() * 6.28f
            polygonPath(List(corners) { i -> at + Offset(cos(turn + i * 6.28f / corners), sin(turn + i * 6.28f / corners)) * (r * (0.6f + random.nextFloat() * 0.5f)) }) to
                lerp(palette.rockDark, palette.rockLight, random.nextFloat())
        }
        val cracks = List(4) {
            var a = (random.nextFloat() - 0.5f) * 2.8f
            var h = 0.2f + random.nextFloat() * 0.7f
            Path().apply {
                val start = onCone(a, h)
                moveTo(start.x, start.y)
                repeat(4) {
                    a += (random.nextFloat() - 0.5f) * 0.15f
                    h += 0.02f + random.nextFloat() * 0.04f
                    val p = onCone(a, h)
                    lineTo(p.x, p.y)
                }
            }
        }
        val thin = gy(0.4f)
        val ashTop = gy(CRATER_Y)
        val ashBottom = gy(CRATER_Y + 10f)
        clipPath(cone) {
            for ((at, r, light) in patches) {
                val tint = if (light) palette.rockLight else palette.rockDark
                drawCircle(Brush.radialGradient(listOf(tint.copy(alpha = 0.35f), Color.Transparent), at, r), r, at)
            }
            for ((upper, lower) in ledges) {
                drawPath(lower, palette.rockDark.copy(alpha = 0.3f), style = Stroke(thin * 2.4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(upper, palette.rockLight.copy(alpha = 0.3f), style = Stroke(thin * 1.4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            for ((fleck, colour) in flecks) drawPath(fleck, colour.copy(alpha = 0.7f))
            for (crack in cracks) drawPath(crack, palette.rockDark.copy(alpha = 0.4f), style = Stroke(thin * 0.8f, join = StrokeJoin.Miter))
            // Ash settled on its upper slopes.
            drawPath(cone, Brush.verticalGradient(listOf(palette.ash.copy(alpha = 0f), lerp(palette.ash, Color.White, 0.3f).copy(alpha = 0.35f), palette.ash.copy(alpha = 0f)), startY = ashTop, endY = ashBottom))
        }
        drawRockRing(random)
    }

    /**
     * Rocks of every size heaped in a ring round the foot, from pebbles to boulders - the far ones
     * first, so the nearer overlap them - each a rough stone lit from the top left, resting on the
     * table on its own shadow.
     */
    private fun CupDrawScope.drawRockRing(random: Random) {
        val rocks = List(ROCKS) { k ->
            val angle = -PI.toFloat() / 2f - 0.3f + (PI.toFloat() + 0.6f) * k / (ROCKS - 1) + (random.nextFloat() - 0.5f) * 0.1f
            val out = random.nextFloat() * 1.8f
            // Mostly small, now and then a big one.
            val size = 0.8f + random.nextFloat().pow(2) * 2.6f
            Triple(angle, out, size)
        }.sortedBy { (angle, _, _) -> cos(angle) }
        for ((angle, out, size) in rocks) {
            val r = radiusAt(1f, angle) + out
            val at = Offset(gx(centreX + r * sin(angle)), gy(BASE_Y + r * CUP_VIEW_SQUASH * cos(angle)))
            val radius = gy(size)
            val corners = 7
            val turn = random.nextFloat() * 6.28f
            val outline = List(corners) { i ->
                val a = turn + i * 6.28f / corners
                val reach = radius * (0.75f + random.nextFloat() * 0.4f)
                // Flattened, and its base sitting on the table rather than on its centre.
                at + Offset(cos(a) * reach, (sin(a) * 0.68f - 0.55f) * reach)
            }
            drawOval(Color.Black.copy(alpha = 0.3f), topLeft = at + Offset(-radius * 0.9f, -radius * 0.25f), size = Size(radius * 2.2f, radius * 0.6f))
            val stone = polygonPath(outline)
            val shade = 0.25f + random.nextFloat() * 0.5f
            drawPath(stone, lerp(palette.rockDark, palette.rock, shade + 0.3f))
            drawPath(polygonPath(outline.map { at + (it - at) * 0.62f - Offset(radius * 0.18f, radius * 0.22f) }), palette.rockLight.copy(alpha = 0.55f))
            drawPath(stone, palette.rockDark.copy(alpha = 0.8f), style = Stroke(gy(0.25f), join = StrokeJoin.Round))
        }
    }

    /**
     * The crater, its top [sway]ed: its ragged rim, the dark throat, and the lava in it - a dull
     * glow, white-hot once it's erupting.
     */
    private fun CupDrawScope.drawCrater(heat: Float, sway: Float) {
        val lava = palette.lava
        val rim = List(36) { i -> onCone(i * 2f * PI.toFloat() / 36f, 0f, sway) }
        val centre = rim.fold(Offset.Zero) { sum, p -> sum + p } / rim.size.toFloat()
        val throat = rim.map { centre + (it - centre) * 0.78f + Offset(0f, gy(0.5f)) }
        drawPath(polygonPath(rim), palette.rockDark)
        val glowRadius = (rim.maxOf { it.x } - rim.minOf { it.x }) * 0.42f
        drawPath(
            smoothPath(throat, closed = true),
            Brush.radialGradient(listOf(lerp(lava.deep, lava.core, heat), lerp(lava.crust, lava.glow, heat), lava.crust), centre + Offset(0f, gy(0.5f)), glowRadius),
        )
        if (heat > 0f) drawPath(polygonPath(rim), lava.glow.copy(alpha = 0.3f * heat), style = Stroke(gy(2.5f), join = StrokeJoin.Round))
        drawPath(polygonPath(rim), palette.rockLight.copy(alpha = 0.7f), style = Stroke(gy(0.7f), join = StrokeJoin.Round))
    }

    /**
     * The lava running down the slopes, [flow] of the way along its run, at [heat]: first a glowing
     * lip where it wells over the front of the rim, then each flow as a ribbon - narrow where it
     * leaves the rim, spreading as it runs, its edges a little uneven and its end a rounded tongue -
     * crusted dark red at its edges, glowing through, with a white-hot thread down its middle.
     */
    private fun CupDrawScope.drawFlows(flow: Float, heat: Float, sway: Float) {
        val lava = palette.lava
        val lip = Path()
        for (i in 0..24) {
            val p = onCone(-1.25f + 2.5f * i / 24f, 0.02f, sway)
            if (i == 0) lip.moveTo(p.x, p.y) else lip.lineTo(p.x, p.y)
        }
        val lipWidth = gy(1.6f) * (0.3f + 0.7f * flow.coerceAtMost(0.3f) / 0.3f)
        drawPath(lip, lava.deep.copy(alpha = heat), style = Stroke(lipWidth * 1.5f, cap = StrokeCap.Round))
        drawPath(lip, lava.glow.copy(alpha = heat), style = Stroke(lipWidth, cap = StrokeCap.Round))
        for ((index, f) in Flows.withIndex()) {
            val shown = ((flow - f.delay) / (1f - f.delay)).coerceIn(0f, 1f)
            if (shown <= 0f) continue
            val random = Random(index * 977 + 13)
            val steps = 24
            val last = shown * steps
            val centres = mutableListOf<Offset>()
            val widths = mutableListOf<Float>()
            for (i in 0..steps) {
                val step = minOf(i.toFloat(), last)
                val h = f.reach * step / steps
                centres += onCone(f.angle + f.wander * sin(h * 4.5f) * h, h, sway)
                widths += gy(f.width) * (0.45f + 0.75f * h / f.reach) * (0.85f + random.nextFloat() * 0.3f)
                if (step >= last) break
            }
            if (centres.size < 2) continue
            val ribbon = ribbon(centres, widths)
            val tip = centres.last()
            val tipWidth = widths.last()
            val core = Path().apply {
                moveTo(centres[0].x, centres[0].y)
                for (c in centres.drop(1)) lineTo(c.x, c.y)
            }
            drawPath(ribbon, lava.glow.copy(alpha = 0.22f * heat), style = Stroke(tipWidth * 1.4f, join = StrokeJoin.Round))
            drawPath(ribbon, lava.deep.copy(alpha = heat))
            drawCircle(lava.deep.copy(alpha = heat), tipWidth * 0.62f, tip)
            drawPath(ribbon(centres, widths.map { it * 0.62f }), lava.glow.copy(alpha = heat))
            drawCircle(lava.glow.copy(alpha = heat), tipWidth * 0.42f, tip)
            drawPath(core, lava.core.copy(alpha = heat), style = Stroke(gy(0.55f), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }

    /** A ribbon down [centres], [widths] wide at each - its two edges and its ends. */
    private fun ribbon(centres: List<Offset>, widths: List<Float>): Path = Path().apply {
        val left = mutableListOf<Offset>()
        val right = mutableListOf<Offset>()
        for (i in centres.indices) {
            val ahead = centres[(i + 1).coerceAtMost(centres.lastIndex)]
            val behind = centres[(i - 1).coerceAtLeast(0)]
            val along = ahead - behind
            val length = along.getDistance().coerceAtLeast(0.001f)
            val normal = Offset(-along.y / length, along.x / length) * (widths[i] / 2f)
            left += centres[i] + normal
            right += centres[i] - normal
        }
        moveTo(left[0].x, left[0].y)
        for (p in left.drop(1)) lineTo(p.x, p.y)
        for (p in right.reversed()) lineTo(p.x, p.y)
        close()
    }

    /** The flash in the crater as it blows, brightest at once and fading. */
    private fun CupDrawScope.drawFlash(burst: Float, sway: Float) {
        val strength = ((0.35f - burst) / 0.35f).coerceIn(0f, 1f)
        if (strength <= 0f) return
        val at = Offset(gx(centreX + sway), gy(CRATER_Y - 2f))
        val r = gy(26f) * (0.6f + 0.4f * (1f - strength))
        drawCircle(Brush.radialGradient(listOf(palette.lava.core.copy(alpha = 0.8f * strength), palette.lava.glow.copy(alpha = 0.35f * strength), Color.Transparent), at, r), r, at)
    }

    /**
     * The eruption at [burst] (0 to 1): ash billowing up out of the crater, then lava thrown up in
     * glowing blobs that arc over and fall back onto the slopes - a fresh spray every eruption,
     * from [seed].
     */
    private fun CupDrawScope.drawEruption(burst: Float, seed: Int, sway: Float) {
        val random = Random(seed)
        val seconds = burst * ERUPTION_MILLIS / 1000f
        // The ash: dark, heavy puffs billowing up and spreading, lit from below at first.
        for (k in 0 until 9) {
            val start = k * 0.04f
            val age = burst - start
            if (age <= 0f) continue
            val rise = 34f * (1f - (1f - age).pow(2))
            val drift = (random.nextFloat() - 0.5f) * 14f * age
            val r = gy(3.5f + 8f * age) * (0.8f + random.nextFloat() * 0.4f)
            val at = Offset(gx(centreX + sway + drift), gy(CRATER_Y - 3f - rise))
            drawCircle(palette.ash.copy(alpha = 0.7f * (1f - age)), r, at)
            drawCircle(lerp(palette.ash, palette.lava.glow, 0.5f).copy(alpha = 0.4f * (1f - age).pow(3)), r * 0.7f, at + Offset(0f, r * 0.3f))
        }
        // The jet: a column of lava blasting up out of the crater, then falling away.
        if (burst < 0.45f) {
            val strength = sin(PI.toFloat() * burst / 0.45f)
            val top = CRATER_Y - 2f - 26f * strength
            val jet = Path().apply {
                val x = centreX + sway
                moveTo(gx(x - 3.2f), gy(CRATER_Y))
                quadraticTo(gx(x - 1.6f), gy((CRATER_Y + top) / 2f), gx(x - 0.5f), gy(top))
                quadraticTo(gx(x), gy(top - 2f), gx(x + 0.5f), gy(top))
                quadraticTo(gx(x + 1.6f), gy((CRATER_Y + top) / 2f), gx(x + 3.2f), gy(CRATER_Y))
                close()
            }
            drawPath(jet, Brush.verticalGradient(listOf(palette.lava.glow.copy(alpha = 0f), palette.lava.glow, palette.lava.core), startY = gy(top), endY = gy(CRATER_Y)))
        }
        // The lava bombs: small, bright and fast, each trailing a streak of its own light.
        repeat(20) {
            val launch = random.nextFloat() * 0.4f
            val vx = (random.nextFloat() - 0.5f) * 40f
            val vy = -(30f + random.nextFloat() * 30f)
            val size = 0.45f + random.nextFloat() * 0.6f
            val startX = centreX + sway + (random.nextFloat() - 0.5f) * CRATER_RADIUS
            val age = seconds - launch * ERUPTION_MILLIS / 1000f
            if (age <= 0f) return@repeat
            fun at(time: Float) = Offset(gx(startX + vx * time), gy(CRATER_Y - 1f + vy * time + 0.5f * BOMB_GRAVITY * time * time))
            val now = at(age)
            // Gone once it's fallen back below the rim, onto the slope.
            if (now.y > gy(CRATER_Y + 10f)) return@repeat
            val cooling = (age / 1.1f).coerceIn(0f, 1f)
            drawLine(palette.lava.glow.copy(alpha = 0.6f * (1f - cooling)), at((age - 0.06f).coerceAtLeast(0f)), now, strokeWidth = gy(size * 1.1f), cap = StrokeCap.Round)
            drawCircle(lerp(palette.lava.core, palette.lava.deep, cooling), gy(size), now)
        }
    }

    /** While it's shaken: a few thin puffs of smoke rising from the crater on the rumble's clock. */
    private fun CupDrawScope.drawRumbleSmoke(t: Float, weight: Float, sway: Float) {
        for (k in 0 until 3) {
            val age = (t * 2f + k / 3f) % 1f
            val at = Offset(gx(centreX + sway + sin(age * 6f + k) * 2f), gy(CRATER_Y - 1f - age * 16f))
            drawCircle(palette.ash.copy(alpha = 0.4f * weight * sin(age * PI.toFloat())), gy(2f + age * 4f), at)
        }
    }
}

private const val BOMB_GRAVITY = 95f

@Composable
private fun rememberRumbleLoop(): State<Float> = rememberInfiniteTransition(label = "volcanoRumble").animateFloat( // i18n: not translated - an animation label, not shown
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(tween(RUMBLE_LOOP_MILLIS, easing = LinearEasing), RepeatMode.Restart),
    label = "volcanoRumbleLoop", // i18n: not translated - an animation label, not shown
)
