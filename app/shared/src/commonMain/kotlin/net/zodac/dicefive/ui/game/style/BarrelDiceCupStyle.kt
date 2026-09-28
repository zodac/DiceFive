package net.zodac.dicefive.ui.game.style

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.sqrt
import net.zodac.dicefive.ui.theme.BarrelInterior
import net.zodac.dicefive.ui.theme.BarrelIron
import net.zodac.dicefive.ui.theme.BarrelIronSheen
import net.zodac.dicefive.ui.theme.BarrelRim
import net.zodac.dicefive.ui.theme.BarrelWood
import net.zodac.dicefive.ui.theme.BarrelWoodDark
import net.zodac.dicefive.ui.theme.BarrelWoodLight
import net.zodac.dicefive.ui.theme.CupShadow

private const val CENTRE_X = 29f
private const val END_RADIUS = 20f
private const val RIM_Y = 10f
private const val BASE_Y = 74f
// The widest point of the bulge, halfway down, and where the two hoops sit on the sides.
private const val BULGE_RADIUS = 24.5f
private const val BULGE_Y = 42f
private const val UPPER_HOOP_Y = 22.6f
private const val LOWER_HOOP_Y = 62.4f
private const val HOOP_RADIUS = 22.9f
private const val INNER_WALL_DEPTH = 6f
// Where the joins between staves meet the rim, as offsets from the centre line.
private val STAVE_JOIN_OFFSETS = listOf(-15f, -5f, 5f, 15f)

/**
 * A [DiceCupStyle] shaped like a small wooden barrel: bulging brown staves bound by two iron hoops,
 * seen from a little above (see [CUP_VIEW_SQUASH]), so the open top, the base, the hoops and the
 * stave joins all curve by the same amount.
 */
object BarrelDiceCupStyle : DiceCupStyle {
    override val id: String = "barrel"

    @Composable
    override fun Cup(rolling: Boolean, tilted: Boolean, modifier: Modifier) {
        CupCanvas(rolling, tilted, modifier) {
            val w = size.width
            val h = size.height
            fun x(v: Float) = w * v / CUP_GRID_WIDTH
            fun y(v: Float) = h * v / CUP_GRID_HEIGHT

            val shadowRadius = END_RADIUS + 3f
            drawOval(
                color = CupShadow.copy(alpha = 0.4f),
                topLeft = Offset(x(CENTRE_X - shadowRadius), y(BASE_Y + 2f - shadowRadius * CUP_VIEW_SQUASH)),
                size = Size(x(2f * shadowRadius), y(2f * shadowRadius * CUP_VIEW_SQUASH)),
            )

            // Bulging sides, and the front half of the base's ellipse along the bottom. The top edge
            // is a straight line across the rim's ends; the mouth, drawn last, covers it.
            val body = Path().apply {
                moveTo(x(CENTRE_X - END_RADIUS), y(RIM_Y))
                cubicTo(x(3f), y(30f), x(3f), y(56f), x(CENTRE_X - END_RADIUS), y(BASE_Y))
                frontArcTo(this, END_RADIUS, BASE_Y, ::x, ::y)
                cubicTo(x(55f), y(56f), x(55f), y(30f), x(CENTRE_X + END_RADIUS), y(RIM_Y))
                close()
            }
            // Horizontal shading (dark edges, lit left of centre) so the staves read as curved.
            drawPath(
                path = body,
                brush = Brush.horizontalGradient(
                    0f to BarrelWoodDark,
                    0.3f to BarrelWoodLight,
                    0.58f to BarrelWood,
                    1f to BarrelWoodDark,
                    startX = x(3f),
                    endX = x(55f),
                ),
            )

            // Joins between staves: each runs down the front of the barrel, from the rim's ellipse to
            // the base's, bowing out with the bulge.
            clipPath(body) {
                for (offset in STAVE_JOIN_OFFSETS) {
                    val across = sqrt(1f - (offset / END_RADIUS) * (offset / END_RADIUS))
                    val topY = RIM_Y + END_RADIUS * CUP_VIEW_SQUASH * across
                    val bottomY = BASE_Y + END_RADIUS * CUP_VIEW_SQUASH * across
                    val midX = CENTRE_X + offset * BULGE_RADIUS / END_RADIUS
                    val midY = BULGE_Y + BULGE_RADIUS * CUP_VIEW_SQUASH * across
                    val join = Path().apply {
                        moveTo(x(CENTRE_X + offset), y(topY))
                        // Control point chosen so the curve passes through (midX, midY) at its middle.
                        quadraticTo(
                            x(2f * midX - (CENTRE_X + offset)), y(2f * midY - (topY + bottomY) / 2f),
                            x(CENTRE_X + offset), y(bottomY),
                        )
                    }
                    drawPath(join, color = Color.Black.copy(alpha = 0.25f), style = Stroke(width = y(0.8f)))
                }
            }

            // Iron hoops wrapped round the front, each with a thin lighter line along its top edge
            // for a metallic glint.
            for (hoopY in listOf(UPPER_HOOP_Y, LOWER_HOOP_Y)) {
                val hoop = Path().apply {
                    moveTo(x(CENTRE_X - HOOP_RADIUS), y(hoopY))
                    frontArcTo(this, HOOP_RADIUS, hoopY, ::x, ::y)
                }
                drawPath(hoop, color = BarrelIron, style = Stroke(width = y(2.4f)))
                val glint = Path().apply {
                    moveTo(x(CENTRE_X - HOOP_RADIUS + 0.4f), y(hoopY - 0.8f))
                    frontArcTo(this, HOOP_RADIUS - 0.4f, hoopY - 0.8f, ::x, ::y)
                }
                drawPath(glint, color = BarrelIronSheen, style = Stroke(width = y(0.6f)))
            }

            // Open top: the inside of the far staves lit just below the rim, the rest in shadow -
            // the same oval dropped by INNER_WALL_DEPTH and clipped to the mouth.
            val mouthTopLeft = Offset(x(CENTRE_X - END_RADIUS), y(RIM_Y - END_RADIUS * CUP_VIEW_SQUASH))
            val mouthSize = Size(x(2f * END_RADIUS), y(2f * END_RADIUS * CUP_VIEW_SQUASH))
            val mouth = Path().apply { addOval(Rect(mouthTopLeft, mouthSize)) }
            clipPath(mouth) {
                drawOval(color = BarrelWood, topLeft = mouthTopLeft, size = mouthSize)
                translate(top = y(INNER_WALL_DEPTH)) {
                    drawOval(color = BarrelInterior, topLeft = mouthTopLeft, size = mouthSize)
                }
            }
            drawOval(color = BarrelRim, topLeft = mouthTopLeft, size = mouthSize, style = Stroke(width = y(1.1f)))
        }
    }
}

/**
 * Continues [path] from the left end of a horizontal circle round the barrel's axis (centred at
 * [centreY], [radius] wide) along its front half to the right end - a cubic that closely matches
 * the half-ellipse the viewing angle turns it into.
 */
private fun frontArcTo(
    path: Path,
    radius: Float,
    centreY: Float,
    x: (Float) -> Float,
    y: (Float) -> Float,
) {
    // 4/3 of the ellipse's half-height puts a cubic's midpoint exactly on the ellipse.
    val controlY = centreY + radius * CUP_VIEW_SQUASH * 4f / 3f
    path.cubicTo(
        x(CENTRE_X - radius), y(controlY),
        x(CENTRE_X + radius), y(controlY),
        x(CENTRE_X + radius), y(centreY),
    )
}
