package net.zodac.dicefive.ui.game.style

import androidx.compose.ui.graphics.Brush
import net.zodac.dicefive.ui.theme.FireTrayBottom
import net.zodac.dicefive.ui.theme.FireTrayTop

/** A "fire" [DiceMat]: a plain red tray. */
object FireDiceMat : DiceMat {
    override val id: String = "fire"

    override val diceTrayBrush: Brush = Brush.verticalGradient(listOf(FireTrayTop, FireTrayBottom))
}
