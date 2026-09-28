package net.zodac.dicefive.ui.game.style

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import net.zodac.dicefive.ui.theme.BarrelInterior
import net.zodac.dicefive.ui.theme.BarrelIron
import net.zodac.dicefive.ui.theme.BarrelIronSheen
import net.zodac.dicefive.ui.theme.BarrelRim
import net.zodac.dicefive.ui.theme.BarrelWood
import net.zodac.dicefive.ui.theme.BarrelWoodDark
import net.zodac.dicefive.ui.theme.BarrelWoodLight
import net.zodac.dicefive.ui.theme.CupShadow

// Authored on a 58 x 84 grid - the in-game cup's own size in dp - and scaled to the canvas.
private const val GRID_WIDTH = 58f
private const val GRID_HEIGHT = 84f

/** A [DiceCupStyle] shaped like a small wooden barrel: bulging brown staves bound by two iron hoops. */
object BarrelDiceCupStyle : DiceCupStyle {
    override val id: String = "barrel"

    @Composable
    override fun Cup(rolling: Boolean, tilted: Boolean, modifier: Modifier) {
        CupCanvas(rolling, tilted, modifier) {
            val w = size.width
            val h = size.height
            fun x(v: Float) = w * v / GRID_WIDTH
            fun y(v: Float) = h * v / GRID_HEIGHT

            drawOval(
                color = CupShadow.copy(alpha = 0.4f),
                topLeft = Offset(w * 0.18f, h * 0.93f),
                size = Size(w * 0.64f, h * 0.08f),
            )

            // Bulging sides, and a slightly rounded bottom edge so the base reads as round too.
            val body = Path().apply {
                moveTo(x(9f), y(6f))
                cubicTo(x(3f), y(28f), x(3f), y(58f), x(9f), y(80f))
                quadraticTo(x(29f), y(83.5f), x(49f), y(80f))
                cubicTo(x(55f), y(58f), x(55f), y(28f), x(49f), y(6f))
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

            // Joins between staves, bowed to follow the bulge.
            val staveJoin = Color.Black.copy(alpha = 0.25f)
            val staveWidth = y(0.8f)
            for (path in listOf(
                Path().apply { moveTo(x(19f), y(6.8f)); quadraticTo(x(14f), y(43f), x(19f), y(82f)) },
                Path().apply { moveTo(x(29f), y(7f)); lineTo(x(29f), y(82.6f)) },
                Path().apply { moveTo(x(39f), y(6.8f)); quadraticTo(x(44f), y(43f), x(39f), y(82f)) },
            )) {
                drawPath(path, color = staveJoin, style = Stroke(width = staveWidth))
            }

            // Iron hoops, curved to wrap round the barrel, each with a thin lighter line along its
            // top edge for a metallic glint.
            for (hoopY in listOf(18f, 68f)) {
                val hoop = Path().apply {
                    moveTo(x(5.9f), y(hoopY))
                    quadraticTo(x(29f), y(hoopY + 4f), x(52.1f), y(hoopY))
                }
                drawPath(hoop, color = BarrelIron, style = Stroke(width = y(2.4f)))
                val glint = Path().apply {
                    moveTo(x(6.5f), y(hoopY - 0.7f))
                    quadraticTo(x(29f), y(hoopY + 3.3f), x(51.5f), y(hoopY - 0.7f))
                }
                drawPath(glint, color = BarrelIronSheen, style = Stroke(width = y(0.6f)))
            }

            // Open top: dark interior ringed by the stave ends.
            val mouthTopLeft = Offset(x(9f), y(2.5f))
            val mouthSize = Size(x(40f), y(7f))
            drawOval(color = BarrelInterior, topLeft = mouthTopLeft, size = mouthSize)
            drawOval(color = BarrelRim, topLeft = mouthTopLeft, size = mouthSize, style = Stroke(width = y(1.1f)))
        }
    }
}
