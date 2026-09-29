package net.zodac.dicefive.ui.game

import net.zodac.dicefive.model.DieColour
import net.zodac.dicefive.model.ScoreCategory

// What a screen reader says for the board's drawn art - the dice, the score tiles - which has no
// text of its own to read. Names match what the Rules pages call each category.

/** A score category's name, as the Rules pages give it - [irish] for Luck of the Irish's colours. */
internal fun ScoreCategory.spokenName(irish: Boolean): String = when (this) {
    ScoreCategory.ONES -> "Ones"
    ScoreCategory.TWOS -> "Twos"
    ScoreCategory.THREES -> "Threes"
    ScoreCategory.FOURS -> "Fours"
    ScoreCategory.FIVES -> "Fives"
    ScoreCategory.SIXES -> "Sixes"
    ScoreCategory.THREE_OF_A_KIND -> "3x"
    ScoreCategory.FOUR_OF_A_KIND -> "4x"
    ScoreCategory.FULL_HOUSE -> "Full House"
    ScoreCategory.SMALL_STRAIGHT -> "Small Straight"
    ScoreCategory.LARGE_STRAIGHT -> "Large Straight"
    ScoreCategory.FIVE_OF_A_KIND -> "5x"
    ScoreCategory.CHANCE -> "Chance"
    ScoreCategory.REDS -> "${DieColour.RED.spokenName(irish)}s"
    ScoreCategory.YELLOWS -> "${DieColour.YELLOW.spokenName(irish)}s"
    ScoreCategory.BLUES -> "${DieColour.BLUE.spokenName(irish)}s"
    ScoreCategory.COLOURED_HOUSE -> "Coloured House"
}

/** A die colour's name - the Irish flag's colours in its place while [irish] (see DieColour.palette). */
internal fun DieColour.spokenName(irish: Boolean): String = when (this) {
    DieColour.RED -> if (irish) "Green" else "Red"
    DieColour.YELLOW -> if (irish) "White" else "Yellow"
    DieColour.BLUE -> if (irish) "Orange" else "Blue"
}
