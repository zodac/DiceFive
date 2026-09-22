package net.zodac.dicefive.ui.game.style

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import net.zodac.dicefive.ui.theme.CupBodyBottom
import net.zodac.dicefive.ui.theme.CupBodyTop
import net.zodac.dicefive.ui.theme.CupRimGold
import net.zodac.dicefive.ui.theme.CupShadow

/** Default [DiceCupStyle]: a hand-drawn leather-and-brass shaker cup with a shake/pour animation. */
object LeatherDiceCupStyle : DiceCupStyle {
    override val id: String = "leather"

    private const val RESTING_TILT_DEGREES = 32f
    private const val SHAKE_AMPLITUDE_DEGREES = 7f
    private const val SNAP_TO_STANDING_MILLIS = 150
    private const val POUR_TILT_MILLIS = 320
    // A single fixed pivot for every rotation - resting tilt AND shake alike - rather than
    // switching between the base (1f) and the center (0.5f) depending on `rolling`. That switch
    // was instantaneous, not animated, so at the moment rolling flipped, the SAME rotation angle
    // suddenly rendered around a different point and the whole cup visibly jumped to a different
    // screen position for a frame - independent of how fast or slow the tilt angle itself was
    // animating. This point is close to the base (so the settled tilt still reads as the cup
    // resting on its foot) but not exactly on it (so during a shake the foot visibly moves too,
    // not just the rim - fixing the earlier "only the top half shakes" complaint without needing
    // a second, switched pivot).
    private const val PIVOT_Y_FRACTION = 0.75f

    @Composable
    override fun Cup(rolling: Boolean, tilted: Boolean, modifier: Modifier) {
        // `&& !rolling`: on the 2nd/3rd roll of a turn, `tilted` is still true from the PREVIOUS
        // roll's result at the moment a new shake starts (the phase it reflects doesn't change
        // until the roll resolves) - without this the cup would stay in its poured-out pose and
        // shake from there instead of standing back up first. Forcing the target tilt to 0 the
        // instant rolling starts brings it back to standing, then back down to this same resting
        // tilt once rolling ends and `tilted` is genuinely true - so every roll, not just the
        // first of a turn, starts from standing.
        val restTiltTarget = if (tilted && !rolling) RESTING_TILT_DEGREES else 0f
        val restTilt by animateFloatAsState(
            targetValue = restTiltTarget,
            // Asymmetric on purpose. Standing up (target 0) is a near-snap: that's what makes
            // "standing" read as the shake's actual STARTING pose rather than a slow straighten
            // that's still visibly under way, blended with the wobble, for its first moments -
            // which was indistinguishable from just shaking a still-tilted cup. Tipping back over
            // (target RESTING_TILT_DEGREES) keeps the slower, deliberate tween: that's the
            // "pouring the dice out" motion once a roll resolves, which should still look unhurried.
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
        val rotation = restTilt + if (rolling) shakeWobble else 0f

        Canvas(
            modifier = modifier.graphicsLayer {
                rotationZ = rotation
                transformOrigin = TransformOrigin(0.5f, PIVOT_Y_FRACTION)
            },
        ) {
            val w = size.width
            val h = size.height

            // Soft contact shadow under the cup's foot.
            drawOval(
                color = CupShadow.copy(alpha = 0.35f),
                topLeft = Offset(w * 0.18f, h * 0.93f),
                size = Size(w * 0.64f, h * 0.08f),
            )

            val topLeft = Offset(w * 0.12f, h * 0.05f)
            val topRight = Offset(w * 0.88f, h * 0.05f)
            val bottomRight = Offset(w * 0.72f, h * 0.90f)
            val bottomLeft = Offset(w * 0.28f, h * 0.90f)
            val footRight = Offset(w * 0.80f, h * 1.0f)
            val footLeft = Offset(w * 0.20f, h * 1.0f)

            val body = Path().apply {
                moveTo(topLeft.x, topLeft.y)
                lineTo(topRight.x, topRight.y)
                lineTo(bottomRight.x, bottomRight.y)
                lineTo(footRight.x, footRight.y)
                lineTo(footLeft.x, footLeft.y)
                lineTo(bottomLeft.x, bottomLeft.y)
                close()
            }
            drawPath(body, brush = Brush.verticalGradient(listOf(CupBodyTop, CupBodyBottom)))

            // Glossy highlight sliver.
            drawPath(
                path = Path().apply {
                    moveTo(w * 0.30f, h * 0.10f)
                    lineTo(w * 0.38f, h * 0.10f)
                    lineTo(w * 0.34f, h * 0.85f)
                    lineTo(w * 0.27f, h * 0.85f)
                    close()
                },
                color = CupRimGold.copy(alpha = 0.18f),
            )

            // Brass bands.
            for (bandFraction in listOf(0.34f, 0.64f)) {
                drawLine(
                    color = CupRimGold,
                    start = Offset(w * (0.12f + 0.13f * bandFraction), h * (0.05f + 0.85f * bandFraction)),
                    end = Offset(w * (0.88f - 0.13f * bandFraction), h * (0.05f + 0.85f * bandFraction)),
                    strokeWidth = h * 0.018f,
                )
            }

            // Dark hollow interior at the open rim, ringed with a thin gold lip.
            val rimSize = Size(w * 0.76f, h * 0.09f)
            val rimTopLeft = Offset(w * 0.12f, 0f)
            drawOval(color = CupShadow, topLeft = rimTopLeft, size = rimSize)
            drawOval(
                color = CupRimGold,
                topLeft = rimTopLeft,
                size = rimSize,
                style = Stroke(width = h * 0.012f),
            )
        }
    }
}
