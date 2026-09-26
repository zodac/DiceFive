package net.zodac.dicefive.ui.game.style

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import net.zodac.dicefive.model.DieColour
import net.zodac.dicefive.ui.theme.TricolourBlueDiceBottom
import net.zodac.dicefive.ui.theme.TricolourBlueDiceTop
import net.zodac.dicefive.ui.theme.TricolourBluePipColor
import net.zodac.dicefive.ui.theme.TricolourBlueStripe
import net.zodac.dicefive.ui.theme.TricolourBlueSwatch
import net.zodac.dicefive.ui.theme.TricolourRedDiceBottom
import net.zodac.dicefive.ui.theme.TricolourRedDiceTop
import net.zodac.dicefive.ui.theme.TricolourRedPipColor
import net.zodac.dicefive.ui.theme.TricolourRedStripe
import net.zodac.dicefive.ui.theme.TricolourRedSwatch
import net.zodac.dicefive.ui.theme.TricolourYellowDiceBottom
import net.zodac.dicefive.ui.theme.TricolourYellowDiceTop
import net.zodac.dicefive.ui.theme.TricolourYellowPipColor
import net.zodac.dicefive.ui.theme.TricolourYellowStripe
import net.zodac.dicefive.ui.theme.TricolourYellowSwatch

/**
 * A die whose colour is part of the roll itself, drawn in that colour whatever [DiceStyle] the player
 * has picked - in a mode like Tricolour the colour is information, so a skin can't be allowed to hide
 * it. Same bevelled shape as every [DiceStyle], only the colours differ.
 */
@Composable
fun ColouredDie(value: Int, colour: DieColour, held: Boolean, modifier: Modifier) {
    val palette = colour.palette
    BeveledDie(value, held, palette.diceTop, palette.diceBottom, palette.pip, modifier)
}

/** Every shade one [DieColour] is drawn in, on the dice and on the scorecard. */
data class DieColourPalette(
    val diceTop: Color,
    val diceBottom: Color,
    val pip: Color,
    /** The flat colour of that colour's scorecard box. */
    val swatch: Color,
    /** A deeper shade for a tile background with a glyph drawn over it. */
    val stripe: Color,
)

val DieColour.palette: DieColourPalette
    get() = when (this) {
        DieColour.RED -> DieColourPalette(
            TricolourRedDiceTop, TricolourRedDiceBottom, TricolourRedPipColor, TricolourRedSwatch, TricolourRedStripe,
        )
        DieColour.YELLOW -> DieColourPalette(
            TricolourYellowDiceTop, TricolourYellowDiceBottom, TricolourYellowPipColor, TricolourYellowSwatch, TricolourYellowStripe,
        )
        DieColour.BLUE -> DieColourPalette(
            TricolourBlueDiceTop, TricolourBlueDiceBottom, TricolourBluePipColor, TricolourBlueSwatch, TricolourBlueStripe,
        )
    }
