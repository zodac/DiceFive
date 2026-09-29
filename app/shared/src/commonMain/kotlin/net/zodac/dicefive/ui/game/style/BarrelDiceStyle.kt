package net.zodac.dicefive.ui.game.style

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import net.zodac.dicefive.ui.theme.BarrelDiceBottom
import net.zodac.dicefive.ui.theme.BarrelDicePipColor
import net.zodac.dicefive.ui.theme.BarrelDiceTop

/** A "barrel" [DiceStyle]: a honey-oak die with off-white pips. */
object BarrelDiceStyle : DiceStyle {
    override val id: String = "barrel"
    override val bodyColor: Color = lerp(BarrelDiceTop, BarrelDiceBottom, 0.5f)

    override fun recoloured(palette: DieColourPalette): DiceStyle = ColouredClassicDiceStyle(id, palette)

    @Composable
    override fun Die(value: Int, held: Boolean, modifier: Modifier) =
        BeveledDie(value, held, BarrelDiceTop, BarrelDiceBottom, BarrelDicePipColor, modifier)
}
