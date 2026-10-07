package net.zodac.dicefive.ui.game

import androidx.compose.runtime.Composable
import net.zodac.dicefive.model.DieColour
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.resources.Res
import net.zodac.dicefive.resources.die_colour_blue
import net.zodac.dicefive.resources.die_colour_blue_irish
import net.zodac.dicefive.resources.die_colour_red
import net.zodac.dicefive.resources.die_colour_red_irish
import net.zodac.dicefive.resources.die_colour_yellow
import net.zodac.dicefive.resources.die_colour_yellow_irish
import net.zodac.dicefive.resources.score_blues
import net.zodac.dicefive.resources.score_blues_irish
import net.zodac.dicefive.resources.score_chance
import net.zodac.dicefive.resources.score_coloured_house
import net.zodac.dicefive.resources.score_evens
import net.zodac.dicefive.resources.score_fives
import net.zodac.dicefive.resources.score_fours
import net.zodac.dicefive.resources.score_full_house
import net.zodac.dicefive.resources.score_large_straight
import net.zodac.dicefive.resources.score_odds
import net.zodac.dicefive.resources.score_ones
import net.zodac.dicefive.resources.score_reds
import net.zodac.dicefive.resources.score_reds_irish
import net.zodac.dicefive.resources.score_sixes
import net.zodac.dicefive.resources.score_small_straight
import net.zodac.dicefive.resources.score_target
import net.zodac.dicefive.resources.score_threes
import net.zodac.dicefive.resources.score_two_pair
import net.zodac.dicefive.resources.score_twos
import net.zodac.dicefive.resources.score_yellows
import net.zodac.dicefive.resources.score_yellows_irish
import net.zodac.dicefive.ui.common.stringResource

// What a screen reader says for the board's drawn art - the dice, the score tiles - which has no
// text of its own to read. Names match what the Rules pages call each category.

/** A score category's name, as the Rules pages give it - [irish] for Luck of the Irish's colours. */
@Composable
internal fun ScoreCategory.spokenName(irish: Boolean): String = when (this) {
    ScoreCategory.ONES -> stringResource(Res.string.score_ones)
    ScoreCategory.TWOS -> stringResource(Res.string.score_twos)
    ScoreCategory.THREES -> stringResource(Res.string.score_threes)
    ScoreCategory.FOURS -> stringResource(Res.string.score_fours)
    ScoreCategory.FIVES -> stringResource(Res.string.score_fives)
    ScoreCategory.SIXES -> stringResource(Res.string.score_sixes)
    ScoreCategory.THREE_OF_A_KIND -> "3x" // i18n: not translated - the game's mark for the box
    ScoreCategory.FOUR_OF_A_KIND -> "4x" // i18n: not translated - the game's mark for the box
    ScoreCategory.FULL_HOUSE -> stringResource(Res.string.score_full_house)
    ScoreCategory.SMALL_STRAIGHT -> stringResource(Res.string.score_small_straight)
    ScoreCategory.LARGE_STRAIGHT -> stringResource(Res.string.score_large_straight)
    ScoreCategory.FIVE_OF_A_KIND -> "5x" // i18n: not translated - the game's mark for the box
    ScoreCategory.CHANCE -> stringResource(Res.string.score_chance)
    ScoreCategory.REDS -> stringResource(if (irish) Res.string.score_reds_irish else Res.string.score_reds)
    ScoreCategory.YELLOWS -> stringResource(if (irish) Res.string.score_yellows_irish else Res.string.score_yellows)
    ScoreCategory.BLUES -> stringResource(if (irish) Res.string.score_blues_irish else Res.string.score_blues)
    ScoreCategory.COLOURED_HOUSE -> stringResource(Res.string.score_coloured_house)
    ScoreCategory.TWO_PAIR -> stringResource(Res.string.score_two_pair)
    ScoreCategory.EVENS -> stringResource(Res.string.score_evens)
    ScoreCategory.ODDS -> stringResource(Res.string.score_odds)
    ScoreCategory.TARGET_1, ScoreCategory.TARGET_2, ScoreCategory.TARGET_3, ScoreCategory.TARGET_4,
    ScoreCategory.TARGET_5, ScoreCategory.TARGET_6, ScoreCategory.TARGET_7, ScoreCategory.TARGET_8,
    ScoreCategory.TARGET_9, ScoreCategory.TARGET_10, ScoreCategory.TARGET_11, ScoreCategory.TARGET_12,
    -> stringResource(Res.string.score_target, ScoreCategory.TARGETS.indexOf(this) + 1)
    ScoreCategory.ALIBI -> "Alibi" // i18n: not translated - the game's mark for the box
}

/** A die colour's name - the Irish flag's colours in its place while [irish] (see DieColour.palette). */
@Composable
internal fun DieColour.spokenName(irish: Boolean): String = when (this) {
    DieColour.RED -> stringResource(if (irish) Res.string.die_colour_red_irish else Res.string.die_colour_red)
    DieColour.YELLOW -> stringResource(if (irish) Res.string.die_colour_yellow_irish else Res.string.die_colour_yellow)
    DieColour.BLUE -> stringResource(if (irish) Res.string.die_colour_blue_irish else Res.string.die_colour_blue)
}
