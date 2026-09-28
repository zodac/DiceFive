package net.zodac.dicefive.ui.game.style

import androidx.compose.ui.graphics.Brush
import net.zodac.dicefive.ui.theme.FireBackgroundBottom
import net.zodac.dicefive.ui.theme.FireBackgroundTop

/** A "fire" [TableBackground]: a red felt background behind the scorecard. */
object FireTableBackground : TableBackground {
    override val id: String = "fire"

    override val scoreAreaBrush: Brush = Brush.verticalGradient(listOf(FireBackgroundTop, FireBackgroundBottom))
}
