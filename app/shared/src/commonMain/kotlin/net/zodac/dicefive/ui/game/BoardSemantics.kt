package net.zodac.dicefive.ui.game

import androidx.compose.runtime.Composable
import net.zodac.dicefive.model.DieColour
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.model.ScoreRule
import net.zodac.dicefive.resources.Res
import net.zodac.dicefive.resources.die_colour_blue
import net.zodac.dicefive.resources.die_colour_blue_irish
import net.zodac.dicefive.resources.die_colour_red
import net.zodac.dicefive.resources.die_colour_red_irish
import net.zodac.dicefive.resources.die_colour_yellow
import net.zodac.dicefive.resources.die_colour_yellow_irish
import net.zodac.dicefive.resources.score_number
import net.zodac.dicefive.resources.score_target
import net.zodac.dicefive.resources.score_wildcard
import net.zodac.dicefive.ui.common.stringResource

// What a screen reader says for the board's drawn art - the dice, the score tiles - which has no
// text of its own to read. Names match what the Rules pages call each category.

/** A score category's name, as the Rules pages give it - [irish] for Luck of the Irish's colours. */
@Composable
internal fun ScoreCategory.spokenName(irish: Boolean): String = when (val rule = rule) {
    is ScoreRule.Target -> stringResource(Res.string.score_target, rule.index)
    is ScoreRule.Wildcard -> stringResource(Res.string.score_wildcard, rule.numbers.first, rule.numbers.last)
    is ScoreRule.Numbered if displayName == null -> stringResource(Res.string.score_number, rule.number)
    else -> mark ?: stringResource(requireNotNull((if (irish) irishName else null) ?: displayName) { "$this has no name" })
}

/** A die colour's name - the Irish flag's colours in its place while [irish] (see DieColour.palette). */
@Composable
internal fun DieColour.spokenName(irish: Boolean): String = when (this) {
    DieColour.RED -> stringResource(if (irish) Res.string.die_colour_red_irish else Res.string.die_colour_red)
    DieColour.YELLOW -> stringResource(if (irish) Res.string.die_colour_yellow_irish else Res.string.die_colour_yellow)
    DieColour.BLUE -> stringResource(if (irish) Res.string.die_colour_blue_irish else Res.string.die_colour_blue)
}
