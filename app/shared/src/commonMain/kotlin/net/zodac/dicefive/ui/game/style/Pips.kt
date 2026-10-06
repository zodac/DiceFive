package net.zodac.dicefive.ui.game.style

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

/** Fractional (0f..1f) positions of a standard die face's pips, for values 1..6. */
private val PIP_LAYOUTS: Map<Int, List<Offset>> = mapOf(
    1 to listOf(Offset(0.5f, 0.5f)),
    2 to listOf(Offset(0.26f, 0.26f), Offset(0.74f, 0.74f)),
    3 to listOf(Offset(0.26f, 0.26f), Offset(0.5f, 0.5f), Offset(0.74f, 0.74f)),
    4 to listOf(Offset(0.26f, 0.26f), Offset(0.74f, 0.26f), Offset(0.26f, 0.74f), Offset(0.74f, 0.74f)),
    5 to listOf(
        Offset(0.26f, 0.26f), Offset(0.74f, 0.26f), Offset(0.5f, 0.5f),
        Offset(0.26f, 0.74f), Offset(0.74f, 0.74f),
    ),
    6 to listOf(
        Offset(0.26f, 0.22f), Offset(0.74f, 0.22f),
        Offset(0.26f, 0.5f), Offset(0.74f, 0.5f),
        Offset(0.26f, 0.78f), Offset(0.74f, 0.78f),
    ),
)

/** Draws the pips for [value] (1..6) across the full size of this draw scope. */
fun DrawScope.drawPips(value: Int, color: Color, pipRadiusFraction: Float = 0.09f) {
    val radius = size.minDimension * pipRadiusFraction
    drawPipPositions(value) { centre -> drawCircle(color = color, radius = radius, center = centre) }
}

/**
 * Calls [drawPip] at the centre of each pip for [value] (1..6), laid out across this draw scope. A [spread]
 * over 1 moves the pips that much further from the face's centre, and so from each other.
 */
fun DrawScope.drawPipPositions(value: Int, spread: Float = 1f, drawPip: DrawScope.(Offset) -> Unit) {
    for (position in pipLayout(value)) {
        drawPip(Offset((0.5f + (position.x - 0.5f) * spread) * size.width, (0.5f + (position.y - 0.5f) * spread) * size.height))
    }
}

/** Where the pips for [value] (1..6) sit on a face, as fractions (0f..1f) of its width and height. */
fun pipLayout(value: Int): List<Offset> = PIP_LAYOUTS[value] ?: PIP_LAYOUTS.getValue(1)

/** A square Canvas pre-wired to [drawPips] - used by both the 3D dice and the flat category icons. */
@Composable
fun PipFace(value: Int, color: Color, modifier: Modifier = Modifier, pipRadiusFraction: Float = 0.09f) {
    Canvas(modifier = modifier) { drawPips(value, color, pipRadiusFraction) }
}
