package net.zodac.dicefive.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import net.zodac.dicefive.model.DieColour

/** The tag of a [DieColourMark] or a [BoxColourMark], for a test to see it: a screen reader finds nothing there. */
internal const val COLOUR_MARK_TAG = "colour_mark"

/** How much of a die's side its [DieColourMark] takes, and how far it reaches out over the corner. */
private const val COLOUR_MARK_SIZE = 0.34f
private val COLOUR_MARK_OUTSET = 5.dp

private val MARK_DARK = Color(0xFF14161C)
private const val DISC_RING = 0.12f
private const val SHAPE_ON_DISC = 0.58f
private const val SHAPE_ON_BOX = 0.9f
private const val TRIANGLE_TOP = 1.1f
private const val TRIANGLE_FOOT = 0.8f
private const val SQUARE_HALF = 0.85f

/**
 * [colour]'s shape, the Tricolour shapes setting's way to tell the dice apart without their colours: white on a dark disc,
 * over the corner of a die of any style, in the [Box] that holds the die. It reaches out over the corner, clear of the
 * die's pips, unless [inside]. Not spoken, as a die says its colour.
 */
@Composable
internal fun BoxScope.DieColourMark(colour: DieColour, inside: Boolean = false) {
    val outset = if (inside) 0.dp else COLOUR_MARK_OUTSET
    Canvas(Modifier.align(Alignment.TopEnd).fillMaxSize(COLOUR_MARK_SIZE).offset(x = outset, y = -outset).testTag(COLOUR_MARK_TAG)) {
        val radius = size.minDimension / 2
        drawCircle(MARK_DARK, radius)
        drawCircle(Color.White, radius, style = Stroke(radius * DISC_RING))
        drawColourShape(colour, radius * SHAPE_ON_DISC, Color.White)
    }
}

/** [colour]'s shape in dark, for that colour's box: the same one as on its dice. Not spoken, as the box says its name. */
@Composable
internal fun BoxColourMark(colour: DieColour, modifier: Modifier = Modifier) {
    Canvas(modifier.testTag(COLOUR_MARK_TAG)) { drawColourShape(colour, size.minDimension / 2 * SHAPE_ON_BOX, MARK_DARK) }
}

/** A triangle for red, a circle for yellow, and a square for blue, whichever colours a style stands in for them. */
private fun DrawScope.drawColourShape(colour: DieColour, reach: Float, ink: Color) {
    val centre = center
    when (colour) {
        DieColour.RED -> drawPath(
            Path().apply {
                moveTo(centre.x, centre.y - reach * TRIANGLE_TOP)
                lineTo(centre.x + reach, centre.y + reach * TRIANGLE_FOOT)
                lineTo(centre.x - reach, centre.y + reach * TRIANGLE_FOOT)
                close()
            },
            ink,
        )
        DieColour.YELLOW -> drawCircle(ink, reach, centre)
        DieColour.BLUE -> drawRect(
            ink,
            Offset(centre.x - reach * SQUARE_HALF, centre.y - reach * SQUARE_HALF),
            Size(reach * 2 * SQUARE_HALF, reach * 2 * SQUARE_HALF),
        )
    }
}
