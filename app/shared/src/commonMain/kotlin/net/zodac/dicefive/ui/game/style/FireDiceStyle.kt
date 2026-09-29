package net.zodac.dicefive.ui.game.style

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import net.zodac.dicefive.ui.theme.FireDiceBottom
import net.zodac.dicefive.ui.theme.FireDicePipColor
import net.zodac.dicefive.ui.theme.FireDiceTop

/** A "fire" [DiceStyle]: a red die with glowing orange pips. */
object FireDiceStyle : DiceStyle {
    override val id: String = "fire"
    override val bodyColor: Color = lerp(FireDiceTop, FireDiceBottom, 0.5f)

    override fun recoloured(palette: DieColourPalette): DiceStyle = ColouredClassicDiceStyle(id, palette)

    @Composable
    override fun Die(value: Int, held: Boolean, modifier: Modifier) =
        BeveledDie(value, held, FireDiceTop, FireDiceBottom, FireDicePipColor, modifier)
}
