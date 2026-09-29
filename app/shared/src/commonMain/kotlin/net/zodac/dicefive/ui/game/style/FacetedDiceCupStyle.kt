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
import androidx.compose.ui.graphics.drawscope.translate
import net.zodac.dicefive.ui.theme.CupShadow
import net.zodac.dicefive.ui.theme.FacetedCupEdge
import net.zodac.dicefive.ui.theme.FacetedCupLitFace
import net.zodac.dicefive.ui.theme.FacetedCupMidFace
import net.zodac.dicefive.ui.theme.FacetedCupShadeFace

/**
 * A [DiceCupStyle]: a six-sided prism cup in the green score tiles' colours, three faces
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
                interior = CupShadow,
                shadow = CupShadow,
            )
        }
    }
}

private const val CENTRE_X = 29f
private const val RIM_RADIUS = 21f
private const val RIM_Y = 10f
private const val BASE_RADIUS = 17f
private const val BASE_Y = 74f
// How far down the inside of the back wall stays lit before the interior falls into shadow.
private const val INNER_WALL_DEPTH = 7f

/**
 * Draws the faceted cup filling this canvas, seen from a little above (see [CUP_VIEW_SQUASH]): a
 * soft contact shadow, the left (shadowed), centre (lit) and right faces, [edge]-coloured seams
 * between them, and the open hexagonal mouth - the inside of the far wall lit in [midFace] just
 * below the rim, falling away into [interior] further down, so it reads as a hollow.
 */
fun DrawScope.drawFacetedCup(
    shadeFace: Color,
    litFace: Color,
    midFace: Color,
    edge: Color,
    interior: Color,
    shadow: Color,
) {
    val w = size.width
    val h = size.height
    fun at(x: Float, y: Float) = Offset(w * x / CUP_GRID_WIDTH, h * y / CUP_GRID_HEIGHT)
    fun polygon(points: List<Offset>) = Path().apply {
        moveTo(points[0].x, points[0].y)
        for (point in points.drop(1)) lineTo(point.x, point.y)
        close()
    }

    // A regular hexagon round the cup's axis, a corner at each side, squashed by the viewing angle:
    // [right, frontRight, frontLeft, left, backLeft, backRight].
    fun hexagon(radius: Float, centreY: Float): List<Offset> {
        val depth = radius * 0.866f * CUP_VIEW_SQUASH
        return listOf(
            at(CENTRE_X + radius, centreY),
            at(CENTRE_X + radius / 2f, centreY + depth),
            at(CENTRE_X - radius / 2f, centreY + depth),
            at(CENTRE_X - radius, centreY),
            at(CENTRE_X - radius / 2f, centreY - depth),
            at(CENTRE_X + radius / 2f, centreY - depth),
        )
    }

    val shadowRadius = BASE_RADIUS + 3f
    drawOval(
        color = shadow.copy(alpha = 0.4f),
        topLeft = at(CENTRE_X - shadowRadius, BASE_Y + 2f - shadowRadius * CUP_VIEW_SQUASH),
        size = Size(w * 2f * shadowRadius / CUP_GRID_WIDTH, h * 2f * shadowRadius * CUP_VIEW_SQUASH / CUP_GRID_HEIGHT),
    )

    val rim = hexagon(RIM_RADIUS, RIM_Y)
    val base = hexagon(BASE_RADIUS, BASE_Y)
    drawPath(polygon(listOf(rim[3], rim[2], base[2], base[3])), color = shadeFace)
    drawPath(polygon(listOf(rim[2], rim[1], base[1], base[2])), color = litFace)
    drawPath(polygon(listOf(rim[1], rim[0], base[0], base[1])), color = midFace)

    val seamWidth = h * 0.9f / CUP_GRID_HEIGHT
    val seamColor = edge.copy(alpha = 0.55f)
    drawLine(seamColor, rim[2], base[2], strokeWidth = seamWidth)
    drawLine(seamColor, rim[1], base[1], strokeWidth = seamWidth)
    drawPath(
        path = Path().apply {
            moveTo(base[3].x, base[3].y)
            lineTo(base[2].x, base[2].y)
            lineTo(base[1].x, base[1].y)
            lineTo(base[0].x, base[0].y)
        },
        color = seamColor,
        style = Stroke(width = seamWidth, join = StrokeJoin.Round),
    )

    // The mouth: filled with the lit inner wall, then the same outline dropped by INNER_WALL_DEPTH and
    // clipped to the mouth covers everything but a band along the back in shadow - the far wall's
    // inside visible below the rim, the near wall hiding the rest.
    val mouth = polygon(rim)
    clipPath(mouth) {
        drawPath(mouth, color = midFace)
        translate(top = h * INNER_WALL_DEPTH / CUP_GRID_HEIGHT) {
            drawPath(mouth, color = interior)
        }
    }
    drawPath(mouth, color = edge, style = Stroke(width = h / CUP_GRID_HEIGHT, join = StrokeJoin.Round))
}
