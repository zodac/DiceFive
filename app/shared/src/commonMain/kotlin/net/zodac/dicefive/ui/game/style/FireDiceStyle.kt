package net.zodac.dicefive.ui.game.style

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.zodac.dicefive.ui.theme.FireDiceBottom
import net.zodac.dicefive.ui.theme.FireDicePipColor
import net.zodac.dicefive.ui.theme.FireDiceTop

/** A "fire" [DiceStyle]: a red die with glowing orange pips. */
object FireDiceStyle : DiceStyle {
    override val id: String = "fire"

    @Composable
    override fun Die(value: Int, held: Boolean, modifier: Modifier) =
        BeveledDie(value, held, FireDiceTop, FireDiceBottom, FireDicePipColor, modifier)
}
