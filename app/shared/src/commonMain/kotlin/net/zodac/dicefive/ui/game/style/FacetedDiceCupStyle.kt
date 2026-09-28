package net.zodac.dicefive.ui.game.style

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import net.zodac.dicefive.ui.theme.CupShadow
import net.zodac.dicefive.ui.theme.FacetedCupEdge
import net.zodac.dicefive.ui.theme.FacetedCupLitFace
import net.zodac.dicefive.ui.theme.FacetedCupMidFace
import net.zodac.dicefive.ui.theme.FacetedCupShadeFace

/**
 * Default [DiceCupStyle]: a six-sided prism cup in the green score tiles' colours, three faces
 * visible and shaded left to right, with a hexagonal mouth and gold edges. Only a gentle taper and
 * no foot - see [drawFacetedCup], which [FireDiceCupStyle] shares.
 */
object FacetedDiceCupStyle : DiceCupStyle {
    override val id: String = "faceted"

    @Composable
    override fun Cup(rolling: Boolean, tilted: Boolean, modifier: Modifier) {
        CupCanvas(rolling, tilted, modifier) {
            drawFacetedCup(
                shadeFace = FacetedCupShadeFace,
                litFace = FacetedCupLitFace,
                midFace = FacetedCupMidFace,
                edge = FacetedCupEdge,
                mouth = CupShadow,
                shadow = CupShadow,
            )
        }
    }
}

// The cup is authored on a 58 x 84 grid - the in-game cup's own size in dp - and scaled to the canvas.
private const val GRID_WIDTH = 58f
private const val GRID_HEIGHT = 84f

/**
 * Draws the faceted cup filling this canvas: a soft contact shadow, the left (shadowed), centre (lit)
 * and right faces, [edge]-coloured seams between them, and the open hexagonal mouth. [centreFaceArt]
 * paints extra decoration on the centre face, clipped to it, before the seams go on top.
 */
fun DrawScope.drawFacetedCup(
    shadeFace: Color,
    litFace: Color,
    midFace: Color,
    edge: Color,
    mouth: Color,
    shadow: Color,
    centreFaceArt: DrawScope.() -> Unit = {},
) {
    val w = size.width
    val h = size.height
    fun at(x: Float, y: Float) = Offset(w * x / GRID_WIDTH, h * y / GRID_HEIGHT)
    fun polygon(points: List<Offset>) = Path().apply {
        moveTo(points[0].x, points[0].y)
        for (point in points.drop(1)) lineTo(point.x, point.y)
        close()
    }

    drawOval(
        color = shadow.copy(alpha = 0.4f),
        topLeft = Offset(w * 0.18f, h * 0.93f),
        size = Size(w * 0.64f, h * 0.08f),
    )

    // Rim corners (the mouth's front edge) and base corners of the three visible faces.
    val rimLeft = at(8f, 6f)
    val rimFrontLeft = at(18f, 9f)
    val rimFrontRight = at(40f, 9f)
    val rimRight = at(50f, 6f)
    val baseLeft = at(12f, 80f)
    val baseFrontLeft = at(20.5f, 80f)
    val baseFrontRight = at(37.5f, 80f)
    val baseRight = at(46f, 80f)

    drawPath(polygon(listOf(rimLeft, rimFrontLeft, baseFrontLeft, baseLeft)), color = shadeFace)
    val centreFace = polygon(listOf(rimFrontLeft, rimFrontRight, baseFrontRight, baseFrontLeft))
    drawPath(centreFace, color = litFace)
    drawPath(polygon(listOf(rimFrontRight, rimRight, baseRight, baseFrontRight)), color = midFace)
    clipPath(centreFace) { centreFaceArt() }

    val seamWidth = h * 0.9f / GRID_HEIGHT
    val seamColor = edge.copy(alpha = 0.55f)
    drawLine(seamColor, rimFrontLeft, baseFrontLeft, strokeWidth = seamWidth)
    drawLine(seamColor, rimFrontRight, baseFrontRight, strokeWidth = seamWidth)
    drawLine(seamColor, baseLeft, baseRight, strokeWidth = seamWidth)

    val mouthPath = polygon(listOf(rimLeft, at(18f, 3f), at(40f, 3f), rimRight, rimFrontRight, rimFrontLeft))
    drawPath(mouthPath, color = mouth)
    drawPath(mouthPath, color = edge, style = Stroke(width = h / GRID_HEIGHT, join = StrokeJoin.Round))
}
