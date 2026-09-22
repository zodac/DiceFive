package net.zodac.dicefive.ui.game.style

import androidx.compose.ui.graphics.Brush
import net.zodac.dicefive.ui.theme.FeltNavyBottom
import net.zodac.dicefive.ui.theme.FeltNavyTop
import net.zodac.dicefive.ui.theme.TrayBlueBottom
import net.zodac.dicefive.ui.theme.TrayBlueTop

/** Default [TableBackground]: deep-navy felt behind the scorecard, brighter blue in the dice tray. */
object MidnightFeltBackground : TableBackground {
    override val id: String = "midnight_felt"

    override val scoreAreaBrush: Brush = Brush.verticalGradient(listOf(FeltNavyTop, FeltNavyBottom))

    override val diceTrayBrush: Brush = Brush.verticalGradient(listOf(TrayBlueTop, TrayBlueBottom))
}
