package net.zodac.dicefive.ui.game.style

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.zodac.dicefive.ui.theme.DicePipColor
import net.zodac.dicefive.ui.theme.IvoryDiceBottom
import net.zodac.dicefive.ui.theme.IvoryDiceTop

/** Default [DiceStyle]: a rounded ivory die with a soft bevel, matching the reference art. */
object IvoryDiceStyle : DiceStyle {
    override val id: String = "ivory"

    @Composable
    override fun Die(value: Int, held: Boolean, modifier: Modifier) =
        BeveledDie(value, held, IvoryDiceTop, IvoryDiceBottom, DicePipColor, modifier)
}
