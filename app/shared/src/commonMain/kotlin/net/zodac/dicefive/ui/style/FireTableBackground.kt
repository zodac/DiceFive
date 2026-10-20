package net.zodac.dicefive.ui.style

import androidx.compose.ui.graphics.Brush
import net.zodac.dicefive.ui.theme.FireBackgroundBottom
import net.zodac.dicefive.ui.theme.FireBackgroundTop

/** The Classic background in Red [TableBackground]: a red felt background behind the scorecard. */
object FireTableBackground : TableBackground {
    override val id: String = "classic_red"

    override val scoreAreaBrush: Brush = Brush.verticalGradient(listOf(FireBackgroundTop, FireBackgroundBottom))
}
