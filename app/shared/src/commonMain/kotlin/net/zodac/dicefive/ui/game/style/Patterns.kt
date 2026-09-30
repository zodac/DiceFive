package net.zodac.dicefive.ui.game.style

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import net.zodac.dicefive.ui.common.LocalReduceMotion

// Pattern painters shared by more than one piece of table art. Each takes a fixed seed rather than
// real randomness, so a pattern is the same every time it's drawn instead of shimmering on every
// recomposition.

/**
 * Scattered pointed stars of varying size and brightness, about one per 44dp square. At [seconds]
 * into a twinkle each star's brightness swells and dims a little, each on its own beat - subtle
 * enough to read as the sky breathing rather than flashing. The default freezes them.
 */
internal fun DrawScope.drawStars(seed: Int, color: Color = Color.White, seconds: Float = 0f) {
    val random = Random(seed)
    // Its own stream, so the stars stay where they always were.
    val beat = Random(seed + 7919)
    val cell = 44.dp.toPx()
    val count = (size.width * size.height / (cell * cell)).toInt().coerceAtLeast(4)
    val star = Path()
    repeat(count) {
        val centre = Offset(random.nextFloat() * size.width, random.nextFloat() * size.height)
        val radius = (1.3f + random.nextFloat() * 1.9f).dp.toPx()
        val alpha = 0.3f + random.nextFloat() * 0.6f
        val phase = beat.nextFloat() * 2f * PI.toFloat()
        val speed = 0.5f + beat.nextFloat() * 1.1f
        val twinkle = 1f - TWINKLE_DEPTH * (0.5f + 0.5f * sin(seconds * speed + phase))
        // Four long points with hollow sides, so even a small one reads as a star and not a dot.
        star.rewind()
        for (i in 0 until 8) {
            val angle = i * PI.toFloat() / 4f - PI.toFloat() / 2f
            val reach = if (i % 2 == 0) radius else radius * STAR_WAIST
            val x = centre.x + cos(angle) * reach
            val y = centre.y + sin(angle) * reach
            if (i == 0) star.moveTo(x, y) else star.lineTo(x, y)
        }
        star.close()
        drawPath(star, color.copy(alpha = alpha * twinkle))
    }
}

// How far in a star's hollow sides pinch, as a fraction of its points' reach.
private const val STAR_WAIST = 0.3f

// How far a star dims at the bottom of its twinkle, as a fraction of its brightness.
private const val TWINKLE_DEPTH = 0.55f

/** Calls [onTick] with the time in seconds on every frame, for as long as it's in the composition. */
@Composable
internal fun TwinkleClock(onTick: (Float) -> Unit) {
    // No clock at all under reduced motion: the stars stay at the brightness they were drawn at.
    if (LocalReduceMotion.current) return
    LaunchedEffect(Unit) {
        while (true) {
            // Wrapped so the float stays precise however long the app has been up.
            withFrameNanos { onTick((it / 1_000_000L % 3_600_000L) / 1000f) }
        }
    }
}

/**
 * Polished marble, like a kitchen countertop: soft cloudy mottling in [vein]'s colour, then a few
 * long veins wandering across the surface - each one gently irregular rather than smooth, mostly
 * soft haze with a faint crisp line through it, throwing off thinner branches as it goes. Widths scale with the surface's
 * shorter side, so a die and a whole mat get veins in proportion. */
internal fun DrawScope.drawMarble(seed: Int, vein: Color) {
    val random = Random(seed)
    val w = size.width
    val h = size.height
    val scale = size.minDimension

    // Cloudy mottling: a few large, very faint blooms.
    repeat(5) {
        val centre = Offset(random.nextFloat() * w, random.nextFloat() * h)
        val radius = scale * (0.3f + random.nextFloat() * 0.5f)
        drawCircle(
            Brush.radialGradient(listOf(vein.copy(alpha = 0.07f), Color.Transparent), center = centre, radius = radius),
            radius = radius,
            center = centre,
        )
    }

    val thin = maxOf(scale * 0.006f, 0.6.dp.toPx())
    repeat(4) {
        // Each vein starts on a random edge and sets off in its own direction, so they meander and
        // cross rather than running in parallel stripes.
        val start = when (random.nextInt(4)) {
            0 -> Offset(0f, random.nextFloat() * h)
            1 -> Offset(w, random.nextFloat() * h)
            2 -> Offset(random.nextFloat() * w, 0f)
            else -> Offset(random.nextFloat() * w, h)
        }
        val towardsCentre = atan2(h / 2 - start.y, w / 2 - start.x)
        val heading = towardsCentre + (random.nextFloat() - 0.5f) * 1.2f
        val main = veinPath(random, start, heading, step = scale * 0.025f, bendiness = 0.25f)
        drawVein(main.first, vein, thin * (1f + random.nextFloat()), strong = true)
        // Branches off the main vein, thinner and shorter.
        for (fork in main.second.filterIndexed { i, _ -> i > 2 && random.nextFloat() < 0.12f }) {
            val branch = veinPath(random, fork, heading + (random.nextFloat() - 0.5f) * 1.4f, step = scale * 0.02f, bendiness = 0.3f, maxSteps = 12)
            drawVein(branch.first, vein, thin * 0.6f, strong = false)
        }
    }
}

/**
 * A vein: a random walk from [start] along [heading] (radians), turning up to [bendiness] either
 * way each [step], until it leaves the surface or runs out of [maxSteps]. Returns a smooth curve
 * through the walk and the points along it (for branches to fork from).
 */
private fun DrawScope.veinPath(
    random: Random,
    start: Offset,
    heading: Float,
    step: Float,
    bendiness: Float,
    maxSteps: Int = 200,
): Pair<Path, List<Offset>> {
    val points = mutableListOf(start)
    var angle = heading
    var at = start
    val margin = step * 2
    for (i in 0 until maxSteps) {
        angle += (random.nextFloat() - 0.5f) * 2f * bendiness
        // Pulled gently back towards the original heading, so it wanders but still crosses the surface.
        angle += (heading - angle) * 0.08f
        at += Offset(cos(angle) * step, sin(angle) * step)
        points += at
        if (at.x < -margin || at.y < -margin || at.x > size.width + margin || at.y > size.height + margin) break
    }
    // Curved through the midpoints between walk steps, with each step as the control point, so the
    // vein bends smoothly instead of showing its straight segments.
    val path = Path().apply {
        moveTo(points.first().x, points.first().y)
        for (i in 1 until points.size - 1) {
            val mid = (points[i] + points[i + 1]) / 2f
            quadraticTo(points[i].x, points[i].y, mid.x, mid.y)
        }
        lineTo(points.last().x, points.last().y)
    }
    return path to points
}

/**
 * A vein drawn as a soft haze - many faint strokes of growing width stacked up, which builds into a
 * smooth falloff rather than visible bands - with a faint crisp line through the middle. Real
 * veins read as a soft seam of colour, not a bright crack.
 */
private fun DrawScope.drawVein(path: Path, color: Color, width: Float, strong: Boolean) {
    val haze = if (strong) 0.022f else 0.016f
    for (layer in 1..6) {
        drawPath(path, color.copy(alpha = haze), style = Stroke(width = width * (1f + layer * 1.8f), join = StrokeJoin.Round, cap = StrokeCap.Round))
    }
    drawPath(path, color.copy(alpha = if (strong) 0.22f else 0.16f), style = Stroke(width = width, join = StrokeJoin.Round, cap = StrokeCap.Round))
}

/** A gingham check: see-through bands of [color] both ways, [band] wide, doubling up where they cross. */
internal fun DrawScope.drawGingham(color: Color, band: Float) {
    val period = band * 2
    val check = color.copy(alpha = 0.13f)
    var x = 0f
    while (x < size.width) {
        drawRect(check, topLeft = Offset(x, 0f), size = Size(band, size.height))
        x += period
    }
    var y = 0f
    while (y < size.height) {
        drawRect(check, topLeft = Offset(0f, y), size = Size(size.width, band))
        y += period
    }
}
