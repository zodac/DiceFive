package net.zodac.dicefive.ui.game.style

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue

private const val SHAKE_AMPLITUDE_DEGREES = 7f
private const val SNAP_TO_STANDING_MILLIS = 150
private const val POUR_TILT_MILLIS = 320

/**
 * The shake-then-settle rotation (in degrees) shared by every [DiceCupStyle]: standing upright most
 * of the time, tipped to [restingTiltDegrees] once this turn's dice have been poured out, and
 * wobbling around whichever of those it's currently at while [rolling]. Pulled out of
 * [LeatherDiceCupStyle] once [FireDiceCupStyle] needed the exact same physics - the cups only differ
 * in what they draw, not how they move. Apply the result as `graphicsLayer { rotationZ = ... }`, with
 * a `transformOrigin` near the cup's own base so the shake reads as the whole cup rocking on its
 * foot rather than spinning around its centre.
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
    return restTilt + if (rolling) shakeWobble else 0f
}
