package net.zodac.dicefive.ui.game.style

import androidx.compose.ui.graphics.Brush
import net.zodac.dicefive.ui.theme.BarrelBackgroundBottom
import net.zodac.dicefive.ui.theme.BarrelBackgroundTop
import net.zodac.dicefive.ui.theme.FeltNavyBottom
import net.zodac.dicefive.ui.theme.FeltNavyTop

/** Default [TableBackground]: deep-navy felt behind the scorecard. */
object MidnightFeltBackground : TableBackground {
    override val id: String = "classic_navy"

    override val scoreAreaBrush: Brush = Brush.verticalGradient(listOf(FeltNavyTop, FeltNavyBottom))
}

/** The Classic background in Brown [TableBackground]: dark stained wood behind the scorecard. */
object BarrelTableBackground : TableBackground {
    override val id: String = "classic_brown"

    override val scoreAreaBrush: Brush = Brush.verticalGradient(listOf(BarrelBackgroundTop, BarrelBackgroundBottom))
}
