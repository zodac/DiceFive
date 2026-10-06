package net.zodac.dicefive.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot
import net.zodac.dicefive.ui.common.LocalReduceMotion

/** The chains' red, and the darker red edging them so they read against a die of any colour. */
private val CHAIN_RED = Color(0xFFE53935)
private val CHAIN_EDGE = Color(0xFF3B0A0E)
private val SCRIM_RED = Color(0xFF8E0F1A)

private const val SLAM_MILLIS = 260
private const val SLAM_START_SCALE = 1.45f
private const val LINKS_PER_CHAIN = 5

/**
 * The look of a die locked by Unlucky Dice ([net.zodac.dicefive.model.Die.isUnlucky]): a red cross of two
 * chains pulled tight over it, on a reddish veil in the die's own outline ([shape], from its style's
 * `shadowShape`, and chains reaching [reach] of the way to its corners, so an egg or a D20 isn't veiled as a square). Fills the die it's laid over ([modifier] sizes it).
 *
 * The chains slam down when the die lands - they start larger and transparent and settle in
 * [SLAM_MILLIS] - and are simply there under reduced motion. Purely visual: what it means is spoken by
 * the die's own semantics (see `spokenDie`).
 */
@Composable
internal fun LockedChains(shape: Shape, reach: Float, modifier: Modifier = Modifier) {
    val reduceMotion = LocalReduceMotion.current
    val slam = remember { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(reduceMotion) {
        if (!reduceMotion && slam.value < 1f) slam.animateTo(1f, tween(SLAM_MILLIS, easing = FastOutSlowInEasing))
    }
    Canvas(
        modifier = modifier.graphicsLayer {
            val progress = slam.value
            val scale = SLAM_START_SCALE + (1f - SLAM_START_SCALE) * progress
            scaleX = scale
            scaleY = scale
            alpha = progress
        },
    ) {
        drawLockedChains(shape, reach)
    }
}

/** The veil, in the die's own outline [shape], and the two chains, from corner to corner, drawn within this scope's own size. */
internal fun DrawScope.drawLockedChains(shape: Shape, reach: Float) {
    drawOutline(shape.createOutline(size, layoutDirection, this), SCRIM_RED.copy(alpha = 0.34f))
    // Each chain's ends sit [reach] of the way from the middle to a point just inside the square's corners.
    val inset = minOf(size.width, size.height) * 0.1f
    val middle = Offset(size.width / 2f, size.height / 2f)
    fun end(x: Float, y: Float) = Offset(middle.x + (x - middle.x) * reach, middle.y + (y - middle.y) * reach)
    val topLeft = end(inset, inset)
    val bottomRight = end(size.width - inset, size.height - inset)
    val topRight = end(size.width - inset, inset)
    val bottomLeft = end(inset, size.height - inset)
    drawChain(topLeft, bottomRight)
    drawChain(topRight, bottomLeft)
}

/**
 * One chain from [from] to [to]: [LINKS_PER_CHAIN] links, each overlapping its neighbours, alternately seen
 * face-on (a ring) and edge-on (a short bar), as links of a real chain lie.
 */
private fun DrawScope.drawChain(from: Offset, to: Offset) {
    val dx = to.x - from.x
    val dy = to.y - from.y
    val length = hypot(dx, dy)
    val angle = atan2(dy, dx) * 180f / PI.toFloat()
    val pitch = length / LINKS_PER_CHAIN
    val linkLength = pitch * 1.45f
    val linkWidth = linkLength * 0.56f
    val stroke = linkWidth * 0.3f
    for (index in 0 until LINKS_PER_CHAIN) {
        val t = (index + 0.5f) / LINKS_PER_CHAIN
        val centre = Offset(from.x + dx * t, from.y + dy * t)
        translate(left = centre.x, top = centre.y) {
            rotate(angle, pivot = Offset.Zero) {
                if (index % 2 == 0) {
                    drawLink(linkLength, linkWidth, stroke)
                } else {
                    drawLink(linkLength * 0.62f, linkWidth * 0.5f, stroke)
                }
            }
        }
    }
}

/** A link centred on the origin: its dark edge first, then the red along it, both rounded. */
private fun DrawScope.drawLink(length: Float, width: Float, stroke: Float) {
    val topLeft = Offset(-length / 2f, -width / 2f)
    val corner = CornerRadius(width / 2f)
    drawRoundRect(CHAIN_EDGE, topLeft, Size(length, width), corner, Stroke(width = stroke * 1.7f, cap = StrokeCap.Round))
    drawRoundRect(CHAIN_RED, topLeft, Size(length, width), corner, Stroke(width = stroke, cap = StrokeCap.Round))
}
