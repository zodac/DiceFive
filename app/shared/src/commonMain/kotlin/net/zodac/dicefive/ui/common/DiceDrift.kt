package net.zodac.dicefive.ui.common

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.random.Random

// How many dice drift across the backdrop - all of them always under way, each coming straight back
// in from below once it has gone, so there are never fewer than about three on screen, or more than five.
private const val DRIFT_DICE = 5

// How fast a die drifts, in screen heights a second - a slow crossing of half a minute or so.
private const val MIN_SPEED_HEIGHTS = 1f / 40f
private const val MAX_SPEED_HEIGHTS = 1f / 26f

// How far off straight up a die's path leans, in degrees, to one side or the other - so it always
// travels up the screen and across it. The most it leans is also held to what lets it cross the
// whole height before running out of width (about 25 degrees on a phone held upright; see leanRange),
// and the least to a share of that, so a narrow screen's dice don't all bunch in its lower half.
private const val MIN_LEAN_DEGREES = 8f
private const val MAX_LEAN_DEGREES = 60f
private const val MIN_LEAN_SHARE = 0.35f

// How fast a die turns, in degrees a second - and never so fast that its corners move faster than
// the die itself travels (see aim).
private const val MIN_SPIN_DEGREES = 2f
private const val MAX_SPIN_DEGREES = 6f

// A new die's size, as a fraction of the screen's shorter side - the range the still watermark uses.
private const val MIN_SIZE = 0.18f
private const val MAX_SIZE = 0.38f

// How many paths a returning die weighs up, taking the one that stays furthest from the other dice
// over the next [PATH_LOOKAHEAD_SECONDS], checked every [PATH_CHECK_SECONDS] - so they don't bunch up
// or cross over each other.
private const val ENTRY_CANDIDATES = 12
private const val PATH_LOOKAHEAD_SECONDS = 12f
private const val PATH_CHECK_SECONDS = 1.5f

// Half a square's diagonal over its side: the furthest a turned die can ever reach from its centre.
private val HALF_DIAGONAL = sqrt(2f) / 2f

/**
 * One die drifting across the backdrop. Its centre is in screen widths ([x]) and heights ([y]), its
 * [size] a fraction of the screen's shorter side, and its velocity ([vx], [vy]) in those same
 * units a second - fixed from the moment it comes on until it leaves, so it crosses in a straight
 * line. [crossing] counts its trips back on.
 */
internal class DriftingDie(
    var x: Float,
    var y: Float,
    var size: Float,
    var rotation: Float,
    var value: Int,
) {
    var vx = 0f
    var vy = 0f
    var spinDegrees = 0f
    var crossing = 0
}

/**
 * The main menu's drifting watermark dice: each crosses the screen slowly, up and to one side, in a
 * straight line and turning slowly as it goes, then comes back in from below - out of sight at
 * first, so it glides on rather than popping in - on a new path, at a new size and showing a new
 * face, on whichever path keeps furthest from the others. The dice start where [start] places
 * them (the still backdrop's own dice), so the first frame is the familiar one, and any more up to
 * [DRIFT_DICE] start on their way in.
 *
 * Plain logic with no Compose in it, moved on by [advance] each frame, so it can be tested with a
 * seeded [random] and made-up screen sizes.
 */
internal class DiceDrift(start: List<DriftingDie>, private val random: Random = Random) {

    val dice: List<DriftingDie> = start.take(DRIFT_DICE) + List((DRIFT_DICE - start.size).coerceAtLeast(0)) {
        DriftingDie(x = 0f, y = 0f, size = MIN_SIZE, rotation = 0f, value = 1).apply { crossing = -1 }
    }

    private var started = false

    /** Moves every die on by [seconds] on a [width] x [height] screen. */
    fun advance(seconds: Float, width: Float, height: Float) {
        if (width <= 0f || height <= 0f) return
        if (!started) {
            started = true
            for (die in dice) {
                if (die.crossing < 0) {
                    enter(die, width, height)
                } else {
                    val velocity = randomVelocity(width, height, random.nextBoolean(), randomLean(width, height))
                    aim(die, velocity, width, height)
                }
            }
        }
        for (die in dice) {
            die.x += die.vx * seconds
            die.y += die.vy * seconds
            die.rotation += die.spinDegrees * seconds
            if (isGone(die, width, height)) enter(die, width, height)
        }
    }

    /** Brings [die] back in from just below the bottom edge, on a new path, size and face. */
    private fun enter(die: DriftingDie, width: Float, height: Float) {
        die.size = random.between(MIN_SIZE, MAX_SIZE)
        die.value = random.nextInt(1, 7)
        die.rotation = random.between(0f, 90f)
        // Just out of sight below the bottom edge - it shows as soon as it moves up.
        die.y = (height + extentPx(die, width, height)) / height
        val others = dice.filter { it !== die }
        val best = List(ENTRY_CANDIDATES) {
            val towardsRight = random.nextBoolean()
            val lean = randomLean(width, height)
            // How far across it drifts on its way up the whole height, in widths - it starts far
            // enough towards the side it's leaving from to fit that in, where the screen allows.
            val across = tan(lean) * height / width
            val fromSide = random.between(-0.05f, (1f - across).coerceAtLeast(0f) + 0.05f)
            val x = if (towardsRight) fromSide else 1f - fromSide
            Path(x, randomVelocity(width, height, towardsRight, lean))
        }.maxBy { path -> others.minOfOrNull { clearancePx(path, die, it, width, height) } ?: 0f }
        die.x = best.x
        die.crossing++
        aim(die, best.velocity, width, height)
    }

    /** One way a returning die could come in: where along the bottom, and its velocity. */
    private class Path(val x: Float, val velocity: Offset)

    /**
     * The closest [path] - [die]'s, starting now - comes to [other] over the next
     * [PATH_LOOKAHEAD_SECONDS], edge to edge, in pixels; negative if they'd overlap.
     */
    private fun clearancePx(path: Path, die: DriftingDie, other: DriftingDie, width: Float, height: Float): Float {
        val gap = (die.size + other.size) / 2f * minOf(width, height)
        var closest = Float.MAX_VALUE
        var t = 0f
        while (t <= PATH_LOOKAHEAD_SECONDS) {
            val dx = (path.x + path.velocity.x * t - other.x - other.vx * t) * width
            val dy = (die.y + path.velocity.y * t - other.y - other.vy * t) * height
            closest = minOf(closest, sqrt(dx * dx + dy * dy) - gap)
            t += PATH_CHECK_SECONDS
        }
        return closest
    }

    /** A lean off straight up, in radians, that suits a [width] x [height] screen (see [MIN_LEAN_DEGREES]). */
    private fun randomLean(width: Float, height: Float): Float {
        val fits = atan(width / height) * 180f / PI.toFloat()
        val most = fits.coerceIn(MIN_LEAN_DEGREES, MAX_LEAN_DEGREES)
        val least = (most * MIN_LEAN_SHARE).coerceAtLeast(MIN_LEAN_DEGREES).coerceAtMost(most)
        return random.between(least, most) * PI.toFloat() / 180f
    }

    /** A steady velocity up the screen at [lean] off straight up, to the right or left, in screen widths and heights a second. */
    private fun randomVelocity(width: Float, height: Float, towardsRight: Boolean, lean: Float): Offset {
        val speedPx = random.between(MIN_SPEED_HEIGHTS, MAX_SPEED_HEIGHTS) * height
        val side = if (towardsRight) 1f else -1f
        return Offset(side * speedPx * sin(lean) / width, -speedPx * cos(lean) / height)
    }

    /**
     * Sets [die] off at [velocity], turning slower than it travels: its corners, [reachPx] from its
     * centre, move slower than its centre does.
     */
    private fun aim(die: DriftingDie, velocity: Offset, width: Float, height: Float) {
        die.vx = velocity.x
        die.vy = velocity.y
        val fastestSpin = travelSpeedPx(die, width, height) / reachPx(die, width, height) * 180f / PI.toFloat()
        val spin = random.between(MIN_SPIN_DEGREES, MAX_SPIN_DEGREES).coerceAtMost(fastestSpin * 0.9f)
        die.spinDegrees = if (random.nextBoolean()) spin else -spin
    }

    /**
     * Whether [die], as it's turned now, is wholly off the [width] x [height] screen - past its top
     * or sides. (Below the bottom is where it comes in from, so that doesn't count.) Measured at its
     * current turn, not its furthest reach, so it's gone the moment it can't be seen - and so is never
     * out of sight longer than it has to be.
     */
    private fun isGone(die: DriftingDie, width: Float, height: Float): Boolean {
        val extent = extentPx(die, width, height)
        val cx = die.x * width
        val cy = die.y * height
        return cx < -extent || cx > width + extent || cy < -extent
    }

    /** The furthest [die] could ever reach from its centre, whichever way it's turned - for its spin limit. */
    private fun reachPx(die: DriftingDie, width: Float, height: Float): Float =
        die.size * minOf(width, height) * HALF_DIAGONAL
}

/** How far [die], as it's turned now, reaches from its centre across or down the screen - half its bounding box. */
internal fun extentPx(die: DriftingDie, width: Float, height: Float): Float {
    val radians = die.rotation * PI.toFloat() / 180f
    return die.size * minOf(width, height) / 2f * (abs(cos(radians)) + abs(sin(radians)))
}

/** Whether [die], as it's turned now, reaches onto a [width] x [height] screen. */
internal fun isShowing(die: DriftingDie, width: Float, height: Float): Boolean {
    val extent = extentPx(die, width, height)
    val cx = die.x * width
    val cy = die.y * height
    return cx + extent > 0f && cx - extent < width && cy + extent > 0f && cy - extent < height
}

/** How fast [die]'s corners move from its turning, in pixels a second, on a [width] x [height] screen. */
internal fun cornerSpeedPx(die: DriftingDie, width: Float, height: Float): Float =
    abs(die.spinDegrees) * PI.toFloat() / 180f * die.size * minOf(width, height) * sqrt(2f) / 2f

/** How fast [die]'s centre travels, in pixels a second, on a [width] x [height] screen. */
internal fun travelSpeedPx(die: DriftingDie, width: Float, height: Float): Float {
    val dx = die.vx * width
    val dy = die.vy * height
    return sqrt(dx * dx + dy * dy)
}

private fun Random.between(from: Float, until: Float): Float = from + nextFloat() * (until - from)
