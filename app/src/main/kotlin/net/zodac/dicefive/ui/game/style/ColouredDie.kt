package net.zodac.dicefive.ui.game.style

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import net.zodac.dicefive.model.DieColour
import net.zodac.dicefive.ui.theme.GoldAccent
import net.zodac.dicefive.ui.theme.IrishGreenDiceBottom
import net.zodac.dicefive.ui.theme.IrishGreenDiceTop
import net.zodac.dicefive.ui.theme.IrishGreenPipColor
import net.zodac.dicefive.ui.theme.IrishGreenStripe
import net.zodac.dicefive.ui.theme.IrishGreenSwatch
import net.zodac.dicefive.ui.theme.IrishOrangeDiceBottom
import net.zodac.dicefive.ui.theme.IrishOrangeDiceTop
import net.zodac.dicefive.ui.theme.IrishOrangePipColor
import net.zodac.dicefive.ui.theme.IrishOrangeStripe
import net.zodac.dicefive.ui.theme.IrishOrangeSwatch
import net.zodac.dicefive.ui.theme.IrishWhiteDiceBottom
import net.zodac.dicefive.ui.theme.IrishWhiteDiceTop
import net.zodac.dicefive.ui.theme.IrishWhitePipColor
import net.zodac.dicefive.ui.theme.IrishWhiteStripe
import net.zodac.dicefive.ui.theme.IrishWhiteSwatch
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
import net.zodac.dicefive.ui.theme.TricolourYellowHeldRing
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
    val palette = colour.palette(LocalIrishTricolour.current)
    BeveledDie(value, held, palette.diceTop, palette.diceBottom, palette.pip, modifier, heldRingColor = palette.heldRing)
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
    /** The held ring's colour on this die - [GoldAccent] for every colour except yellow, whose own
     * face is close enough to gold that the two need swapping (see [TricolourYellowHeldRing]). */
    val heldRing: Color = GoldAccent,
)

val DieColour.palette: DieColourPalette
    get() = when (this) {
        DieColour.RED -> DieColourPalette(
            TricolourRedDiceTop, TricolourRedDiceBottom, TricolourRedPipColor, TricolourRedSwatch, TricolourRedStripe,
        )
        DieColour.YELLOW -> DieColourPalette(
            TricolourYellowDiceTop, TricolourYellowDiceBottom, TricolourYellowPipColor, TricolourYellowSwatch, TricolourYellowStripe,
            heldRing = TricolourYellowHeldRing,
        )
        DieColour.BLUE -> DieColourPalette(
            TricolourBlueDiceTop, TricolourBlueDiceBottom, TricolourBluePipColor, TricolourBlueSwatch, TricolourBlueStripe,
        )
    }

/**
 * [palette] as normal, or - while [LocalIrishTricolour] is true - the Irish flag's green/white/orange
 * standing in for this colour's usual red/yellow/blue instead. Every caller that draws a Tricolour
 * colour (dice, the Reds/Yellows/Blues scorecard swatches, Coloured House's stripes) goes through
 * this one function, so re-skinning only ever needs touching this `when`.
 */
fun DieColour.palette(irish: Boolean): DieColourPalette = if (!irish) {
    palette
} else {
    when (this) {
        DieColour.RED -> DieColourPalette(IrishGreenDiceTop, IrishGreenDiceBottom, IrishGreenPipColor, IrishGreenSwatch, IrishGreenStripe)
        DieColour.YELLOW -> DieColourPalette(IrishWhiteDiceTop, IrishWhiteDiceBottom, IrishWhitePipColor, IrishWhiteSwatch, IrishWhiteStripe)
        DieColour.BLUE -> DieColourPalette(IrishOrangeDiceTop, IrishOrangeDiceBottom, IrishOrangePipColor, IrishOrangeSwatch, IrishOrangeStripe)
    }
}
