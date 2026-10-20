package net.zodac.dicefive.ui.style

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import net.zodac.dicefive.data.settings.DefaultStyleIds
import net.zodac.dicefive.ui.theme.TrayBlueBottom
import net.zodac.dicefive.ui.theme.TrayBlueSlotBorder
import net.zodac.dicefive.ui.theme.TrayBlueSlotBottom
import net.zodac.dicefive.ui.theme.TrayBlueSlotTop
import net.zodac.dicefive.ui.theme.TrayBlueTop

/** Default [DiceMat]: a bright blue tray, matching [MidnightFeltBackground]. */
object TrayBlueMat : DiceMat {
    override val id: String = DefaultStyleIds.DICE_MAT

    override val diceTrayBrush: Brush = Brush.verticalGradient(listOf(TrayBlueTop, TrayBlueBottom))
    override val slotSocketBrush: Brush = Brush.verticalGradient(listOf(TrayBlueSlotTop, TrayBlueSlotBottom))
    override val slotSocketBorder: Color = TrayBlueSlotBorder
}
