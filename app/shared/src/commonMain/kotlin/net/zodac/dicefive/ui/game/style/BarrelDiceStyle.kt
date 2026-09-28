package net.zodac.dicefive.ui.game.style

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.zodac.dicefive.ui.theme.BarrelDiceBottom
import net.zodac.dicefive.ui.theme.BarrelDicePipColor
import net.zodac.dicefive.ui.theme.BarrelDiceTop

/** A "barrel" [DiceStyle]: a honey-oak die with off-white pips. */
object BarrelDiceStyle : DiceStyle {
    override val id: String = "barrel"

    @Composable
    override fun Die(value: Int, held: Boolean, modifier: Modifier) =
        BeveledDie(value, held, BarrelDiceTop, BarrelDiceBottom, BarrelDicePipColor, modifier)
}
