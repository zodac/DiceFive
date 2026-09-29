package net.zodac.dicefive.ui.game.style

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

// A googly die's pupil - its pip - and the socket round it that it rolls about in, well over twice its
// radius so it has room to roll, as fractions of the face its pips are laid out across; and how thick
// the socket's rim is.
private const val GOOGLY_PUPIL_RADIUS = 0.055f
private const val GOOGLY_SOCKET_RADIUS = 0.15f
private const val GOOGLY_RIM_WIDTH = 0.026f

// How far a pupil can roll from its socket's centre before its edge meets the rim, on the same scale.
private const val GOOGLY_PUPIL_REACH = GOOGLY_SOCKET_RADIUS - GOOGLY_RIM_WIDTH / 2 - GOOGLY_PUPIL_RADIUS

// The pips spread further apart than a plain die's, so a six's sockets don't touch.
private const val GOOGLY_SPREAD = 1.12f

// The face the pips are laid out across is the die less this much all round - less than a plain
// die's, to leave the sockets room - which is roughly this much of a die on the mat.
private val GOOGLY_PIP_PADDING = 4.dp
private const val GOOGLY_FACE_SHARE = 0.82f

/**
 * A plain die with googly eyes: an ordinary face and pips, except every pip is a loose pupil in a
 * clear socket well over twice its size, rolling about in it as the die is swept off the mat, thrown,
 * tumbles, bounces and spins - see [DieMotion], which the tray keeps for each die and hands down as
 * [LocalDieMotion]. Anywhere a die isn't moved about (the Styles screen, say) its pupils sit where
 * they settled.
 */
class GooglyDiceStyle(
    override val id: String,
    private val top: Color,
    private val bottom: Color,
    private val pip: Color,
    private val socket: Color = Color.White,
) : DiceStyle, Swatched {
    override val swatch: Color = top
    override val bodyColor: Color = lerp(top, bottom, 0.5f)
    override val pupilTravel: Float = GOOGLY_PUPIL_REACH * GOOGLY_FACE_SHARE

    @Composable
    override fun Die(value: Int, held: Boolean, modifier: Modifier) {
        val motion = LocalDieMotion.current
        val dieIndex = LocalDieIndex.current
        val settled = remember(dieIndex) { restingPupils(dieIndex) }
        StyledDie(
            value = value,
            held = held,
            modifier = modifier,
            face = Brush.linearGradient(listOf(top, bottom)),
            edge = bottom.copy(alpha = 0.6f),
            pipColor = pip,
            pipShape = PipShape.CUSTOM,
            pipPadding = GOOGLY_PIP_PADDING,
            // Read while drawing, so the pupils move without the die recomposing.
            customPips = { drawGooglyEyes(it, motion?.pupils ?: settled) },
        )
    }

    /** [value]'s pips as googly eyes, each pupil at its place in [pupils] (see [DieMotion.pupils]). */
    private fun DrawScope.drawGooglyEyes(value: Int, pupils: List<Offset>) {
        val s = size.minDimension
        val socketRadius = s * GOOGLY_SOCKET_RADIUS
        val rim = s * GOOGLY_RIM_WIDTH
        val reach = s * GOOGLY_PUPIL_REACH
        pipLayout(value).forEachIndexed { i, p ->
            val centre = Offset(
                (0.5f + (p.x - 0.5f) * GOOGLY_SPREAD) * size.width,
                (0.5f + (p.y - 0.5f) * GOOGLY_SPREAD) * size.height,
            )
            // The dome (the iris) and its rim.
            drawCircle(socket, socketRadius, centre)
            drawCircle(pip, socketRadius - rim / 2, centre, style = Stroke(width = rim))
            drawCircle(Color.Black, s * GOOGLY_PUPIL_RADIUS, centre + pupils[i] * reach)
        }
    }
}
