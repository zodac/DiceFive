package net.zodac.dicefive.ui.style

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import net.zodac.dicefive.data.settings.DefaultStyleIds
import net.zodac.dicefive.ui.theme.TrayBlueBottom
import net.zodac.dicefive.ui.theme.TrayBlueRing
import net.zodac.dicefive.ui.theme.TrayBlueSlotBorder
import net.zodac.dicefive.ui.theme.TrayBlueSlotBottom
import net.zodac.dicefive.ui.theme.TrayBlueSlotTop
import net.zodac.dicefive.ui.theme.TrayBlueTop

private val RING_WIDTH = 1.5.dp
private val TRAY_CORNER = 16.dp

/** Default [DiceMat]: the launcher icon's blue mat and gold ring, as a tray on [MidnightFeltBackground]. */
object TrayBlueMat : DiceMat {
    override val id: String = DefaultStyleIds.DICE_MAT

    override val diceTrayBrush: Brush = Brush.verticalGradient(listOf(TrayBlueTop, TrayBlueBottom))
    override val slotSocketBrush: Brush = Brush.verticalGradient(listOf(TrayBlueSlotTop, TrayBlueSlotBottom))
    override val slotSocketBorder: Color = TrayBlueSlotBorder

    @Composable
    override fun DiceTrayDecoration(modifier: Modifier) {
        Canvas(modifier = modifier) {
            // The ring's outer edge follows the tray's own rounded edge.
            insetOutline(RING_WIDTH / 2, TRAY_CORNER - RING_WIDTH / 2, TrayBlueRing, Stroke(width = RING_WIDTH.toPx()))
        }
    }
}
