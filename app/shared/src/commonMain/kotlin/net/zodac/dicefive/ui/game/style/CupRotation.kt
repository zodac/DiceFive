package net.zodac.dicefive.ui.game.style

import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer

private const val SHAKE_AMPLITUDE_DEGREES = 7f
private const val SNAP_TO_STANDING_MILLIS = 150
private const val POUR_TILT_MILLIS = 320
private const val WOBBLE_FADE_MILLIS = 120

private const val RESTING_TILT_DEGREES = 32f
// A single fixed pivot for every rotation - resting tilt AND shake alike - rather than switching
// between the base (1f) and the center (0.5f) depending on `rolling`. That switch was instantaneous,
// not animated, so at the moment rolling flipped, the SAME rotation angle suddenly rendered around a
// different point and the whole cup visibly jumped to a different screen position for a frame -
// independent of how fast or slow the tilt angle itself was animating. This point is close to the
// base (so the settled tilt still reads as the cup resting on its base) but not exactly on it (so
// during a shake the base visibly moves too, not just the rim - fixing the earlier "only the top
// half shakes" complaint without needing a second, switched pivot).
private const val PIVOT_Y_FRACTION = 0.75f

/**
 * The canvas every [DiceCupStyle] draws its cup on: [onDraw] paints the cup standing upright, and
 * this applies the shared shake/pour rotation from [rememberCupRotation] around it, so the cups only
 * differ in their art.
 */
@Composable
fun CupCanvas(rolling: Boolean, tilted: Boolean, modifier: Modifier, onDraw: DrawScope.() -> Unit) {
    val rotation = rememberCupRotation(rolling, tilted, RESTING_TILT_DEGREES)
    Canvas(
        modifier = modifier.graphicsLayer {
            rotationZ = rotation
            transformOrigin = TransformOrigin(0.5f, PIVOT_Y_FRACTION)
        },
        onDraw = onDraw,
    )
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
        animationSpec = tween(durationMillis = if (restTiltTarget == 0f) SNAP_TO_STANDING_MILLIS else POUR_TILT_MILLIS),
        label = "cupTilt",
    )
    val infiniteTransition = rememberInfiniteTransition(label = "cupShake")
    val shakeWobble by infiniteTransition.animateFloat(
        initialValue = -SHAKE_AMPLITUDE_DEGREES,
        targetValue = SHAKE_AMPLITUDE_DEGREES,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 90, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "cupWobble",
    )
    // Faded in/out over WOBBLE_FADE_MILLIS rather than switched the instant `rolling` flips: the
    // infinite transition above never resets its own phase, so cutting its contribution off
    // abruptly could drop the rendered rotation anywhere in a +-SHAKE_AMPLITUDE_DEGREES range with
    // nothing to smooth it out - a visible pop to a half-tilted or "wrong way" pose. Worst on a turn
    // with only one shake to begin with (Hard AI's common one-roll-then-hold-everything turn),
    // where there's no following shake to bury the jump in.
    val wobbleWeight by animateFloatAsState(
        targetValue = if (rolling) 1f else 0f,
        animationSpec = tween(durationMillis = WOBBLE_FADE_MILLIS),
        label = "cupWobbleFade",
    )
    return restTilt + shakeWobble * wobbleWeight
}
