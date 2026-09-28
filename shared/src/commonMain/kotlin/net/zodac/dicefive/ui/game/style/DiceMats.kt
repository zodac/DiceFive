package net.zodac.dicefive.ui.game.style

import androidx.compose.ui.graphics.Brush
import net.zodac.dicefive.ui.theme.TrayBlueBottom
import net.zodac.dicefive.ui.theme.TrayBlueTop

/** Default [DiceMat]: a bright blue tray, matching [MidnightFeltBackground]'s original pairing. */
object TrayBlueMat : DiceMat {
    override val id: String = "tray_blue"

    override val diceTrayBrush: Brush = Brush.verticalGradient(listOf(TrayBlueTop, TrayBlueBottom))
}
