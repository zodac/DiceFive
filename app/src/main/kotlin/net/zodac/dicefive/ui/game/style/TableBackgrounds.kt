package net.zodac.dicefive.ui.game.style

import androidx.compose.ui.graphics.Brush
import net.zodac.dicefive.ui.theme.FeltNavyBottom
import net.zodac.dicefive.ui.theme.FeltNavyTop

/** Default [TableBackground]: deep-navy felt behind the scorecard. */
object MidnightFeltBackground : TableBackground {
    override val id: String = "midnight_felt"

    override val scoreAreaBrush: Brush = Brush.verticalGradient(listOf(FeltNavyTop, FeltNavyBottom))
}
