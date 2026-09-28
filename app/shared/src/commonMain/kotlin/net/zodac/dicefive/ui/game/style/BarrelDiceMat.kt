package net.zodac.dicefive.ui.game.style

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import net.zodac.dicefive.ui.theme.BarrelSlotBorder
import net.zodac.dicefive.ui.theme.BarrelSlotBottom
import net.zodac.dicefive.ui.theme.BarrelSlotTop
import net.zodac.dicefive.ui.theme.BarrelTrayBottom
import net.zodac.dicefive.ui.theme.BarrelTrayTop

private const val PLANK_COUNT = 4

/** A "barrel" [DiceMat]: a tray of brown wooden planks in the barrel cup's own wood. */
object BarrelDiceMat : DiceMat {
    override val id: String = "barrel"

    override val diceTrayBrush: Brush = Brush.verticalGradient(listOf(BarrelTrayTop, BarrelTrayBottom))
    override val slotSocketBrush: Brush = Brush.verticalGradient(listOf(BarrelSlotTop, BarrelSlotBottom))
    override val slotSocketBorder: Color = BarrelSlotBorder

    @Composable
    override fun DiceTrayDecoration(modifier: Modifier) {
        Canvas(modifier = modifier) {
            // A dark seam between each pair of planks, with a faint light line just under it where
            // the next plank's edge catches the light.
            val seamWidth = 1.dp.toPx()
            for (plank in 1 until PLANK_COUNT) {
                val y = size.height * plank / PLANK_COUNT
                drawLine(Color.Black.copy(alpha = 0.35f), Offset(0f, y), Offset(size.width, y), strokeWidth = seamWidth)
                drawLine(
                    Color.White.copy(alpha = 0.07f),
                    Offset(0f, y + seamWidth),
                    Offset(size.width, y + seamWidth),
                    strokeWidth = seamWidth,
                )
            }
        }
    }
}
