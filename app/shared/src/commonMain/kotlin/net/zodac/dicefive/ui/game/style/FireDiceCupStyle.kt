package net.zodac.dicefive.ui.game.style

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import net.zodac.dicefive.ui.theme.FireCupBodyBottom
import net.zodac.dicefive.ui.theme.FireCupBodyTop
import net.zodac.dicefive.ui.theme.FireCupRim
import net.zodac.dicefive.ui.theme.FireCupShadow
import net.zodac.dicefive.ui.theme.FlameOrange
import net.zodac.dicefive.ui.theme.FlameOrangeLight

/**
 * A "fire" [DiceCupStyle]: a plain straight-tapered tumbler - no flared foot, no brass bands -
 * simpler than [LeatherDiceCupStyle]'s shape by design, in red, with a single orange flame painted
 * up its centre. Shares [LeatherDiceCupStyle]'s shake/tilt physics via [rememberCupRotation]; only
 * the art differs between the two.
 */
object FireDiceCupStyle : DiceCupStyle {
    override val id: String = "fire"

    private const val RESTING_TILT_DEGREES = 32f
    private const val PIVOT_Y_FRACTION = 0.75f

    @Composable
    override fun Cup(rolling: Boolean, tilted: Boolean, modifier: Modifier) {
        val rotation = rememberCupRotation(rolling, tilted, RESTING_TILT_DEGREES)

        Canvas(
            modifier = modifier.graphicsLayer {
                rotationZ = rotation
                transformOrigin = TransformOrigin(0.5f, PIVOT_Y_FRACTION)
            },
        ) {
            val w = size.width
            val h = size.height

            drawOval(
                color = FireCupShadow.copy(alpha = 0.4f),
                topLeft = Offset(w * 0.18f, h * 0.93f),
                size = Size(w * 0.64f, h * 0.08f),
            )

            // A plain straight-sided trapezoid, flat across the bottom - no separate flared foot,
            // no brass bands: the "simple" shape this cup trades Leather's ornamentation for.
            val topLeft = Offset(w * 0.12f, h * 0.05f)
            val topRight = Offset(w * 0.88f, h * 0.05f)
            val bottomRight = Offset(w * 0.68f, h * 1.0f)
            val bottomLeft = Offset(w * 0.32f, h * 1.0f)

            val body = Path().apply {
                moveTo(topLeft.x, topLeft.y)
                lineTo(topRight.x, topRight.y)
                lineTo(bottomRight.x, bottomRight.y)
                lineTo(bottomLeft.x, bottomLeft.y)
                close()
            }
            drawPath(body, brush = Brush.verticalGradient(listOf(FireCupBodyTop, FireCupBodyBottom)))

            // A single flame, centred and narrow enough to sit inside the body's own taper with
            // clear whitespace either side, rather than two wider ones that had to be clipped to
            // keep from crossing the sloped sides. clipPath is still here as a hard guarantee -
            // belt and braces, not the primary fix - so the flame can never visually "hover" past
            // the cup's edge regardless of exact proportions.
            clipPath(body) {
                drawFlame(
                    topLeft = Offset(w * 0.38f, h * 0.28f),
                    size = Size(w * 0.24f, h * 0.62f),
                    outerColor = FlameOrange,
                    innerColor = FlameOrangeLight,
                )
            }

            val rimSize = Size(w * 0.76f, h * 0.09f)
            val rimTopLeft = Offset(w * 0.12f, 0f)
            drawOval(color = FireCupShadow, topLeft = rimTopLeft, size = rimSize)
            drawOval(
                color = FireCupRim,
                topLeft = rimTopLeft,
                size = rimSize,
                style = Stroke(width = h * 0.012f),
            )
        }
    }
}
