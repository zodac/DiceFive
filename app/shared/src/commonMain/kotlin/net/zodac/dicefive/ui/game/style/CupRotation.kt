package net.zodac.dicefive.ui.game.style

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import net.zodac.dicefive.ui.common.LocalReduceMotion

private const val SHAKE_AMPLITUDE_DEGREES = 7f
private const val SNAP_TO_STANDING_MILLIS = 150
private const val POUR_TILT_MILLIS = 320
private const val WOBBLE_FADE_MILLIS = 120

// Negative: rotationZ turns clockwise for positive angles, so this tips the poured-out cup over to
// the left.
private const val RESTING_TILT_DEGREES = -32f
// A single fixed pivot for every rotation - resting tilt AND shake alike - rather than switching
// between the base (1f) and the center (0.5f) depending on `rolling`. That switch was instantaneous,
// not animated, so at the moment rolling flipped, the SAME rotation angle suddenly rendered around a
// different point and the whole cup visibly jumped to a different screen position for a frame -
// independent of how fast or slow the tilt angle itself was animating. This point is close to the
// base (so the settled tilt still reads as the cup resting on its base) but not exactly on it (so
// during a shake the base visibly moves too, not just the rim - fixing the earlier "only the top
// half shakes" complaint without needing a second, switched pivot).
internal const val PIVOT_Y_FRACTION = 0.75f

// Tall cups are authored on a 58 x 84 grid - the in-game cup's own size in dp - and scaled to the canvas.
const val CUP_GRID_WIDTH = 58f
const val CUP_GRID_HEIGHT = 84f

/**
 * The proportions a cup is drawn in: its canvas size in dp in the game (and so the grid its art is
 * authored on): [TALL] for the usual cup, [MEDIUM] for one a little wider and shorter (a barrel, a
 * takeaway cup), or [SQUAT] for one as wide as it's tall, like a cauldron. All fit the game's 104dp
 * cup slot, even tipped over.
 */
enum class CupShape(val gridWidth: Float, val gridHeight: Float) {
    TALL(CUP_GRID_WIDTH, CUP_GRID_HEIGHT),
    MEDIUM(66f, 76f),
    SQUAT(76f, 66f),
}

/**
 * The [DrawScope] a cup is drawn in, with its [shape]'s grid ([gx]/[gy] turn grid units into
 * pixels, [centreX] is the cup's centre line) and its current [pose], for any liquid inside it.
 */
class CupDrawScope internal constructor(drawScope: DrawScope, val shape: CupShape, val pose: CupPose) : DrawScope by drawScope {
    val centreX: Float get() = shape.gridWidth / 2f

    fun gx(v: Float): Float = size.width * v / shape.gridWidth

    fun gy(v: Float): Float = size.height * v / shape.gridHeight
}

// Every cup is seen side-on from a little above (roughly 22 degrees), so a circle round the cup - its
// mouth, its base, a hoop - is drawn as an ellipse this many times as tall as it is wide. Using one
// value for all of them is what keeps the open top, the base and anything wrapped round the body
// looking like they're seen from the same angle.
const val CUP_VIEW_SQUASH = 0.38f

/**
 * How a cup is currently turned, for art inside it that shouldn't just turn with it - a liquid.
 *
 * [rotation] is the cup's own angle. [liquidRotation] trails it on an underdamped spring, the way
 * liquid lags behind the glass holding it: it overshoots and wobbles when the cup is shaken or
 * poured, then settles back to matching it.
 */
class CupPose(val rotation: Float, val liquidRotation: Float) {
    /**
     * The angle (degrees, clockwise) to turn a liquid's surface by, in the cup's own upright
     * drawing, so it stays level on screen - plus whatever slosh is still settling.
     */
    val surfaceTilt: Float get() = -liquidRotation

    /** How far the liquid is still lagging the cup, in degrees: 0 once it's settled. */
    val slosh: Float get() = rotation - liquidRotation
}

/**
 * Whether a cup's own ambient animation (the Cauldron's bubbling) should run. The game turns it off
 * once a turn's rolls are used up and the cup is dimmed, so a spent cup sits still instead of
 * redrawing every frame; everywhere else - the Styles screen's previews - it stays on.
 */
val LocalCupAnimated = compositionLocalOf { true }

/**
 * Something that changes whenever the player does anything at the table - the game provides the
 * current dice, so holding or releasing one changes it - for a cup that reacts to being left alone
 * (the Top Hat's rabbit) to restart its wait on. Null where nobody's playing.
 */
val LocalCupActivity = compositionLocalOf<Any?> { null }

/**
 * Called the moment the Top Hat's rabbit peeks out, for The Magician's Secret. The game provides
 * it; anywhere else (the Styles screen, where the hat never tips) it does nothing.
 */
val LocalOnRabbitSeen = compositionLocalOf<() -> Unit> { {} }

/**
 * The Flowerpot cup's plant: how far it has grown ([stage], from 0 up to
 * [net.zodac.dicefive.model.FLOWERPOT_FULL_BLOOM] - see
 * [net.zodac.dicefive.model.flowerpotGrowthStage]) and whose it is ([grower], their seat at the
 * table, or null outside a game). A new stage for the same grower grows into place; a different
 * grower's plant replaces it outright rather than shrinking or growing into it.
 */
data class FlowerpotGrowth(val stage: Int, val grower: Int?)

/**
 * The Flowerpot's plant as it should be drawn. The game provides the current player's; anywhere else
 * (the Styles screen, the menu's logo) it's a plain pot of soil, with nothing grown yet.
 */
val LocalFlowerpotGrowth = compositionLocalOf { FlowerpotGrowth(stage = 0, grower = null) }

/**
 * The canvas every [DiceCupStyle] draws its cup on: [onDraw] paints the cup standing upright on its
 * [shape]'s grid, and this applies the shared shake/pour rotation from [rememberCupRotation] around
 * it, so the cups only differ in their art. The [CupDrawScope] carries the [CupPose] for any liquid
 * a cup draws; most cups ignore it. A cup that doesn't [tips] (the Shipping container, which opens its
 * doors instead) shakes the same, but stays standing once poured.
 */
@Composable
fun CupCanvas(
    rolling: Boolean,
    tilted: Boolean,
    modifier: Modifier,
    shape: CupShape = CupShape.TALL,
    tips: Boolean = true,
    onDraw: CupDrawScope.() -> Unit,
) {
    val rotation = rememberCupRotation(rolling, tilted, if (tips) RESTING_TILT_DEGREES else 0f)
    val sloshing by animateFloatAsState(
        targetValue = rotation,
        animationSpec = spring(dampingRatio = 0.3f, stiffness = Spring.StiffnessLow),
        label = "cupLiquid", // i18n: not translated - an animation label, not shown
    )
    // No slosh under reduced motion: whatever's in the cup moves with it, on the same frame - even a snap would
    // trail the cup by one.
    val liquidRotation = if (LocalReduceMotion.current) rotation else sloshing
    val pose = CupPose(rotation, liquidRotation)
    Canvas(
        modifier = modifier.graphicsLayer {
            rotationZ = rotation
            transformOrigin = TransformOrigin(0.5f, PIVOT_Y_FRACTION)
        },
    ) {
        CupDrawScope(this, shape, pose).onDraw()
    }
}

/**
 * The shake-then-settle rotation (in degrees) shared by every [DiceCupStyle]: standing upright most
 * of the time, tipped to [restingTiltDegrees] once this turn's dice have been poured out, and
 * wobbling around whichever of those it's currently at while [rolling]. Pulled out of
 * the original leather cup once a second style needed the exact same physics - the cups only differ
 * in what they draw, not how they move. [CupCanvas] applies it as `graphicsLayer { rotationZ = ... }`,
 * with a `transformOrigin` near the cup's own base so the shake reads as the whole cup rocking on its
 * base rather than spinning around its centre.
 */
@Composable
fun rememberCupRotation(rolling: Boolean, tilted: Boolean, restingTiltDegrees: Float): Float {
    // `&& !rolling`: on the 2nd/3rd roll of a turn, `tilted` is still true from the PREVIOUS
    // roll's result at the moment a new shake starts (the phase it reflects doesn't change until
    // the roll resolves) - without this the cup would stay in its poured-out pose and shake from
    // there instead of standing back up first. Forcing the target tilt to 0 the instant rolling
    // starts brings it back to standing, then back down to this same resting tilt once rolling
    // ends and `tilted` is genuinely true - so every roll, not just the first of a turn, starts
    // from standing.
    val restTiltTarget = if (tilted && !rolling) restingTiltDegrees else 0f
    val restTilt by animateFloatAsState(
        targetValue = restTiltTarget,
        // Asymmetric on purpose. Standing up (target 0) is a near-snap: that's what makes
        // "standing" read as the shake's actual STARTING pose rather than a slow straighten
        // that's still visibly under way, blended with the wobble, for its first moments - which
        // was indistinguishable from just shaking a still-tilted cup. Tipping back over (target
        // restingTiltDegrees) keeps the slower, deliberate tween: that's the "pouring the dice
        // out" motion once a roll resolves, which should still look unhurried.
        // Under reduced motion it doesn't move at all: the cup is simply standing, or simply tipped.
        animationSpec = if (LocalReduceMotion.current) {
            snap()
        } else {
            tween(durationMillis = if (restTiltTarget == 0f) SNAP_TO_STANDING_MILLIS else POUR_TILT_MILLIS)
        },
        label = "cupTilt", // i18n: not translated - an animation label, not shown
    )
    // Faded in/out over WOBBLE_FADE_MILLIS rather than switched the instant `rolling` flips:
    // cutting the wobble's contribution off abruptly could drop the rendered rotation anywhere in a
    // +-SHAKE_AMPLITUDE_DEGREES range with nothing to smooth it out - a visible pop to a half-tilted
    // or "wrong way" pose. Worst on a turn with only one shake to begin with (Hard AI's common
    // one-roll-then-hold-everything turn), where there's no following shake to bury the jump in.
    val wobbleWeight by animateFloatAsState(
        targetValue = if (rolling) 1f else 0f,
        animationSpec = tween(durationMillis = WOBBLE_FADE_MILLIS),
        label = "cupWobbleFade", // i18n: not translated - an animation label, not shown
    )
    // The wobble's clock only exists while it counts - shaking, or fading out after one. A still
    // cup then asks for no frames at all: an infinite transition left running at weight 0 kept
    // every cup on screen (the board's, the menu logo's, each Styles tile's) recomposing every
    // frame for nothing. It starts fresh each shake, at weight 0, so there's no pop from that either.
    val shakeWobble = if (rolling || wobbleWeight > 0f) rememberShakeWobble() else 0f
    return restTilt + shakeWobble * wobbleWeight
}

/** The shake's back-and-forth, in degrees - see [rememberCupRotation] for when it runs. */
@Composable
private fun rememberShakeWobble(): Float {
    val shakeWobble by rememberInfiniteTransition(label = "cupShake").animateFloat( // i18n: not translated - an animation label, not shown
        initialValue = -SHAKE_AMPLITUDE_DEGREES,
        targetValue = SHAKE_AMPLITUDE_DEGREES,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 90, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "cupWobble", // i18n: not translated - an animation label, not shown
    )
    return shakeWobble
}
