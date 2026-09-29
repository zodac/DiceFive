package net.zodac.dicefive.ui.game.style

import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** The most pips a face has, and so the most pupils a googly-eyed die needs. */
internal const val MAX_PUPILS = 6

// Past this many of the die's own sizes in one frame, it didn't move there - it was put there (thrown
// back on from out of sight at a new spot, say), so that frame's jump doesn't count as speed.
private const val TELEPORT_DIE_SIZES = 0.5f

// The hardest the die's movement can shove a pupil, in die sizes a second per second - so one uneven
// frame can't fling it.
private const val MAX_ACCELERATION = 200f

// How quickly a pupil's slide dies away, per second; how much of its speed it keeps bouncing off its
// socket's rim; and how much of the die's spin it gets left behind by, rather than dragged round with.
private const val PUPIL_FRICTION = 1.6f
private const val PUPIL_BOUNCE = 0.65f
private const val PUPIL_SPIN_LAG = 0.85f

// How hard a pupil slides down a face tipped on its edge, in die sizes a second per second - gravity,
// scaled to the die. Lying flat, there's no slope to slide down.
private const val PUPIL_GRAVITY = 45f

// Below these - die sizes a second for the die, socket-widths a second for a pupil - each counts as
// still; once everything has been still for this many frames running, the pupils stop being moved.
private const val STILL_DIE_SPEED = 0.05f
private const val STILL_PUPIL_SPEED = 0.15f
private const val STILL_FRAMES = 6

// The longest frame the pupils are moved on by in one go, so a stalled frame doesn't throw them.
private const val MAX_STEP_SECONDS = 0.05f

// How much the device's own pull (see DieMotion.feel) has to change, in g, since the pupils last came
// to rest before it wakes them - so a phone lying still, or a sensor's noise, doesn't keep them going.
private const val FELT_WAKE_GEES = 0.03f

// Against the rim, a pupil only coming outwards as fast as a couple of frames of its push would make it
// is being held there by a steady pull, not thrown into it - so it rests rather than bouncing.
private const val RESTING_FRAMES = 2f

/**
 * The die being drawn's [DieMotion], for a style whose faces its movement shakes about (see
 * [DiceStyle.pupilTravel]) - or null, where a die isn't on the mat or its style has nothing loose.
 */
val LocalDieMotion = compositionLocalOf<DieMotion?> { null }

/**
 * Where a googly-eyed die's pupils sit before anything has moved them - as [DieMotion.pupils]
 * are measured - each somewhere near its rim, as they'd have settled, and differently on each die
 * ([seed]).
 */
internal fun restingPupils(seed: Int): List<Offset> {
    val random = Random(seed * 71 + 5)
    return List(MAX_PUPILS) {
        val angle = random.nextFloat() * 2f * PI.toFloat()
        val reach = 0.55f + random.nextFloat() * 0.45f
        Offset(cos(angle) * reach, sin(angle) * reach)
    }
}

/**
 * One die's movement across the mat, and the loose pupils of its googly eyes that the movement throws
 * about. The tray tells it where the die is each time it's drawn ([moveTo]) - its centre, in the
 * die's own sizes, and how far it's turned - and, while anything is moving, [follow] moves the pupils
 * on every frame: each slides freely in its socket, flung the opposite way to however the die speeds
 * up, slows or changes direction, sliding downhill as it tumbles, left behind a little as it spins, slowing by friction and bouncing
 * softly off the socket's rim. Once the die and every pupil have come to rest it stops asking for
 * frames, until the die next moves.
 *
 * The device itself can pull on them too ([feel]) - only the main menu's logo does that, turning
 * the phone's tilt and shake into the same slide. Without it, pupils behave exactly as they always
 * have on the mat.
 *
 * [travel] is how far a pupil can roll from its socket's centre, as a fraction of the die's size -
 * how hard the die's movement throws it depends on how far it has to go. [seed] picks where the
 * pupils start ([restingPupils]).
 */
@Stable
class DieMotion(seed: Int, private val travel: Float) {
    /**
     * Each pupil's place in its socket, one per pip in pip order, in the die's own frame (turning with
     * it): as a fraction of how far it can roll, from 0 at the socket's centre to 1 against its rim.
     * Read while drawing, so the pupils redraw as they move without anything recomposing.
     */
    var pupils: List<Offset> by mutableStateOf(restingPupils(seed))
        private set

    /** Whether the die or its pupils are on the move, so [follow] should be running. */
    var awake: Boolean by mutableStateOf(false)
        private set

    private val speeds = Array(MAX_PUPILS) { Offset.Zero }
    private var centre: Offset? = null
    private var yawDegrees = 0f
    private var tipDegrees = 0f
    private var lastCentre: Offset? = null
    private var lastYawDegrees = 0f
    private var lastVelocity: Offset? = null
    private var lastNanos: Long? = null
    private var stillFrames = 0
    private var felt = Offset.Zero
    private var feltAtRest = Offset.Zero

    /**
     * Notes that the die is now centred at [centre] (in its own sizes), turned [yawDegrees], with the
     * face in view tipped [tipDegrees] about its left-right axis as it tumbles - positive with its top
     * edge tipping away - so its pupils slide down the slope.
     */
    fun moveTo(centre: Offset, yawDegrees: Float, tipDegrees: Float = 0f) {
        val previous = this.centre
        this.centre = centre
        this.yawDegrees = yawDegrees
        this.tipDegrees = tipDegrees
        if (previous == null) {
            // First seen here: nothing has moved yet.
            lastCentre = centre
            lastYawDegrees = yawDegrees
        } else if (previous != centre || yawDegrees != lastYawDegrees) {
            awake = true
        }
    }

    /**
     * Notes the pull the device itself now puts on the pupils, in g, on screen (x right, y down):
     * gravity down whichever way the phone is tipped, and against however it's being moved. Wakes
     * them only if it has changed enough since they last came to rest to move them.
     */
    fun feel(gees: Offset) {
        felt = gees * PUPIL_GRAVITY
        if ((felt - feltAtRest).getDistance() > FELT_WAKE_GEES * PUPIL_GRAVITY) awake = true
    }

    /** Freezes the pupils where they are: the die has landed, so they stop sliding at once. */
    fun settle() {
        speeds.fill(Offset.Zero)
        lastCentre = centre
        lastYawDegrees = yawDegrees
        lastVelocity = null
        awake = false
    }

    /** Moves the pupils on every frame until the die and they have all come to rest. */
    suspend fun follow() {
        try {
            while (step(withFrameNanos { it })) {
                // Each step is a frame.
            }
        } finally {
            lastNanos = null
            lastVelocity = null
            stillFrames = 0
            awake = false
        }
    }

    /**
     * Moves the pupils on to [frameNanos], by however the die has moved since the last step. Returns
     * whether anything is still moving.
     */
    fun step(frameNanos: Long): Boolean {
        val centre = centre ?: return false
        val seconds = lastNanos?.let { ((frameNanos - it) / 1e9f).coerceIn(0f, MAX_STEP_SECONDS) } ?: 0f
        lastNanos = frameNanos
        val previous = lastCentre
        val spun = yawDegrees - lastYawDegrees
        lastCentre = centre
        lastYawDegrees = yawDegrees

        var acceleration = Offset.Zero
        var dieSpeed = 0f
        var turn = 0f
        if (previous != null && seconds > 0f) {
            val moved = centre - previous
            if (moved.getDistance() > TELEPORT_DIE_SIZES) {
                lastVelocity = null
            } else {
                val velocity = moved / seconds
                lastVelocity?.let { acceleration = (velocity - it) / seconds }
                lastVelocity = velocity
                dieSpeed = velocity.getDistance()
                turn = spun
            }
        }
        val strength = acceleration.getDistance()
        if (strength > MAX_ACCELERATION) acceleration *= MAX_ACCELERATION / strength

        // Into the die's own frame - it's drawn turned yawDegrees - and in socket-widths, not die sizes.
        // A face tipped away has its near edge raised, so its pupils slide towards its far (top) edge.
        val slope = Offset(0f, -PUPIL_GRAVITY * sin(tipDegrees * PI.toFloat() / 180f))
        // The device's own pull (feel) is on screen, so it's turned into the die's frame the same way.
        val push = (slope + (felt - acceleration).turned(-yawDegrees)) / travel
        val restingSpeed = if (felt == Offset.Zero) 0f else push.getDistance() * seconds * RESTING_FRAMES
        pupils = pupils.mapIndexed { i, pupil ->
            // Left behind by part of the die's spin: turned back against it, in the die's frame.
            var place = pupil.turned(-turn * PUPIL_SPIN_LAG)
            var speed = speeds[i].turned(-turn * PUPIL_SPIN_LAG)
            speed += (push - speed * PUPIL_FRICTION) * seconds
            place += speed * seconds
            val reach = place.getDistance()
            if (reach > 1f) {
                // Against the rim: stopped there, and bounced back off it a little.
                val outwards = place / reach
                place = outwards
                val out = speed.x * outwards.x + speed.y * outwards.y
                // Held there by the device's steady pull, it rests - bouncing, it would jitter forever.
                val bounce = if (out < restingSpeed) 0f else PUPIL_BOUNCE
                if (out > 0f) speed -= outwards * (out * (1f + bounce))
            }
            speeds[i] = speed
            place
        }

        val still = tipDegrees == 0f && dieSpeed < STILL_DIE_SPEED && speeds.all { it.getDistance() < STILL_PUPIL_SPEED }
        stillFrames = if (still && seconds > 0f) stillFrames + 1 else 0
        if (stillFrames >= STILL_FRAMES) {
            speeds.fill(Offset.Zero)
            feltAtRest = felt
            return false
        }
        return true
    }
}

/** This turned [degrees] clockwise on screen (y runs down), as graphicsLayer's rotationZ turns things. */
private fun Offset.turned(degrees: Float): Offset {
    val radians = degrees * PI.toFloat() / 180f
    val c = cos(radians)
    val s = sin(radians)
    return Offset(x * c - y * s, x * s + y * c)
}
