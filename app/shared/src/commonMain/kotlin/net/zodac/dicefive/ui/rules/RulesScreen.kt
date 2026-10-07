package net.zodac.dicefive.ui.rules

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.rotate
import net.zodac.dicefive.ui.common.VerticalScrollbar
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.compose.LocalLifecycleOwner
import net.zodac.dicefive.ui.common.LOGO_DICE
import net.zodac.dicefive.ui.common.LocalReduceMotion
import net.zodac.dicefive.ui.common.delayWhileResumed
import net.zodac.dicefive.ui.common.logoRollPose
import net.zodac.dicefive.ui.common.playLogoRoll
import net.zodac.dicefive.ui.game.CUP_SHAKE_MILLIS
import net.zodac.dicefive.ui.game.style.ClassicGoldDiceCupStyle
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabPosition
import androidx.compose.material3.TabRowDefaults
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.lerp
import net.zodac.dicefive.ui.game.LockedChains
import net.zodac.dicefive.ui.game.TurnTimerBadge
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.zodac.dicefive.ui.common.ScreenScaffold
import net.zodac.dicefive.ui.common.FooterPill
import net.zodac.dicefive.ui.common.SoraFontFamily
import net.zodac.dicefive.ui.common.parseInlineMarkup
import net.zodac.dicefive.model.Die
import net.zodac.dicefive.model.DieColour
import net.zodac.dicefive.model.HitTarget
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.ui.game.CategoryTile
import net.zodac.dicefive.ui.game.targetProgress
import net.zodac.dicefive.ui.game.style.IvoryDiceStyle
import net.zodac.dicefive.ui.game.style.palette

/** One page of [RulesScreen]. [title] heads the page itself; [tabLabel] is the shorter name its tab
 * carries, so the tab row shows more than one or two tabs at a time. [blocks] render in order, each
 * as its own kind of block rather than as hand-typed markup (a leading "- " for a bullet, "[25pts]:"
 * after a name), so every list and every category on every page is laid out the same way.
 *
 * Text may use [parseInlineMarkup]'s markers, to one convention throughout: `backticks` (gold
 * monospace, like a [RulesCategory]'s heading) for a scoring category's name, *italic* for the name
 * of a section, mode or setting, and **bold** for a number of points or a count. Nothing else is
 * styled, and no dice are written out as text - a [RulesDice] row shows them instead. */
private data class RulesPage(val title: String, val tabLabel: String, val blocks: List<RulesBlock>)

private sealed interface RulesBlock

/** The default cup with a handful of dice tipped out beside it - a picture for a page with no
 * example rolls of its own, so it doesn't look empty. */
private data object RulesIllustration : RulesBlock

/** The game's own turn timer badge, stopped at [TURN_TIMER_EXAMPLE_SECONDS] so it flashes as it
 * does when a turn is running out. */
private data object RulesTurnTimer : RulesBlock

/** A paragraph of body text. */
private data class RulesText(val text: String) : RulesBlock

/** One step of an ordered list - "1.", "2." - with its text hanging beside the number, and an
 * optional [example] under it. */
private data class RulesStep(val number: Int, val text: String, val example: RulesDice? = null) : RulesBlock

/** A scoring category: its [name] as a small heading, what it takes in [description], and an
 * [example] roll scoring it. */
private data class RulesCategory(val name: String, val description: String, val example: RulesDice) : RulesBlock

/** One die in a [RulesDice] example: its [value], whether it [counts] towards the category being
 * shown (the rest are drawn faded), the [colour] it rolled in Tricolour, if any, and whether it is [locked]
 * in chains by Unlucky Dice (drawn at full strength with the chains over it, and never counting). */
private data class ExampleDie(val value: Int, val counts: Boolean = true, val colour: DieColour? = null, val locked: Boolean = false) {
    /** A Hit List target's place that any die fills - drawn as an empty, dashed die. */
    val anyPlace: Boolean
        get() = value == ANY_PLACE
}

/**
 * A row of example dice illustrating the rule above it, with what they score alongside - or, with no
 * [score], a roll on its way to a hand. The faded dice don't count towards it, or with [fadedNotHeld]
 * are the ones left unheld. With [isTarget], it's a Hit List target rather than a roll - its places, any die
 * filling an [ExampleDie.anyPlace] - and [score] what it's worth.
 */
private data class RulesDice(
    val dice: List<ExampleDie>,
    val score: String?,
    val fadedNotHeld: Boolean = false,
    val isTarget: Boolean = false,
    val tile: RulesTile? = null,
) : RulesBlock

/**
 * A Hit List box drawn at the end of a [RulesDice] row, as the board shows it for those dice - so the page shows what
 * a target's tile looks like on its own, part filled and fully filled. A [Target] is matched against the row's dice
 * (not for the row that is the target itself); the [Alibi] lights up as a box the dice can score in.
 */
private sealed interface RulesTile {
    data class Target(val target: HitTarget) : RulesTile

    data object Alibi : RulesTile
}

/** This row with [tile] drawn at its end. */
private fun RulesDice.showing(tile: RulesTile): RulesDice = copy(tile = tile)

private fun text(text: String) = RulesText(text)

/** Five example dice where the die at [lockedIndex] is locked by Unlucky Dice and the first [counting] of the others make the category. */
private fun lockedDice(vararg values: Int, lockedIndex: Int, counting: Int, score: String): RulesDice {
    var seen = 0
    return RulesDice(
        values.mapIndexed { index, value ->
            if (index == lockedIndex) ExampleDie(value, counts = false, locked = true) else ExampleDie(value, counts = seen++ < counting)
        },
        score,
    )
}

/** Five example dice, the first [counting] of which make the category. */
private fun dice(vararg values: Int, counting: Int = values.size, score: String): RulesDice =
    RulesDice(values.mapIndexed { index, value -> ExampleDie(value, counts = index < counting) }, score)

/** A roll of example dice, the first [held] of which are held - drawn and read out as such, with no score. */
private fun roll(vararg values: Int, held: Int): RulesDice =
    RulesDice(values.mapIndexed { index, value -> ExampleDie(value, counts = index < held) }, score = null, fadedNotHeld = true)

/** Five example dice, the ones at [counting] making the category - for a category whose dice aren't simply the first few. */
private fun diceCounting(vararg values: Int, counting: Set<Int>, score: String): RulesDice =
    RulesDice(values.mapIndexed { index, value -> ExampleDie(value, counts = index in counting) }, score)

/** A roll of example dice, the ones at [held] held - for a roll whose held dice aren't simply the first few. */
private fun rollHolding(vararg values: Int, held: Set<Int>): RulesDice =
    RulesDice(values.mapIndexed { index, value -> ExampleDie(value, counts = index in held) }, score = null, fadedNotHeld = true)

/** What a Hit List [target] example writes for a place any die fills. */
private const val ANY_PLACE = 0

/** A Hit List target's places, left to right - [ANY_PLACE] where any die will do - worth [points], with its tile. */
private fun target(vararg places: Int, points: Int): RulesDice =
    RulesDice(
        places.map { ExampleDie(it) },
        score = "${points}pts",
        isTarget = true,
        tile = RulesTile.Target(HitTarget(places.map { it.takeIf { place -> place != ANY_PLACE } }, points)),
    )

/** The target the Hit List page's examples chase: 4 1 3 2 and an open place, worth 20. */
private val EXAMPLE_TARGET = HitTarget(listOf(4, 1, 3, 2, null), points = 20)

/** Five coloured example dice for Tricolour, the first [counting] of which make the category. */
private fun colouredDice(vararg dice: Pair<Int, DieColour>, counting: Int = dice.size, score: String): RulesDice =
    RulesDice(dice.mapIndexed { index, (value, colour) -> ExampleDie(value, counts = index < counting, colour = colour) }, score)

/**
 * The rules explained in the player's own words, not the rulebook's - one page per idea, swiped
 * (or picked from the tab row) rather than scrolled past as one long page, so each (upper section,
 * lower section, the joker rule, the house rule tie-break, and one page per non-Standard game mode)
 * gets its own moment rather than blurring into the next.
 *
 * Kept in step with the actual rules engine: `ScoreCategory`'s fixed values (25/30/40/50/100),
 * `ScoreCalculator`'s joker rule priority, `game/TieBreak.kt`'s criterion order (see its own
 * doc comment), and each `GameMode`'s rolls, dice and timer - a rule change there should be
 * echoed here.
 */
private val RULES_PAGES = listOf(
    RulesPage(
        title = "How to Play DiceFive",
        tabLabel = "How to Play",
        blocks = listOf(
            text("Score as many points as possible by rolling five dice, with three rolls per round."),
            text("You may keep any dice you want after a roll, then roll the remaining dice."),
            text("Once you're happy with the roll (or you've rolled three times), score it in any open category on your scorecard."),
            text("The game ends once every category is filled."),
            RulesIllustration,
        ),
    ),
    RulesPage(
        title = "Scoring: Upper Section",
        tabLabel = "Upper Section",
        blocks = listOf(
            text("Each *Upper Section* category, from `Ones` to `Sixes`, scores the total of the dice showing that number. For example:"),
            RulesCategory("Fives", "Total of the dice showing 5", dice(5, 5, 5, 2, 1, counting = 3, score = "15pts")),
            text("Score **63pts** or more across the whole section and you earn a bonus **35pts**! That's an average of three of each number."),
        ),
    ),
    RulesPage(
        title = "Scoring: Lower Section",
        tabLabel = "Lower Section",
        blocks = listOf(
            text("The *Lower Section* awards points for specific dice combinations:"),
            RulesCategory("3x", "Total of all five dice, if at least three dice are the same", dice(5, 5, 5, 2, 6, counting = 3, score = "23pts")),
            RulesCategory("4x", "Total of all five dice, if at least four dice are the same", dice(4, 4, 4, 4, 1, counting = 4, score = "17pts")),
            RulesCategory("Full House", "Three of one number and two of another", dice(3, 3, 3, 6, 6, score = "25pts")),
            RulesCategory("Small Straight", "Four numbers in a row", dice(2, 3, 4, 5, 2, counting = 4, score = "30pts")),
            RulesCategory("Large Straight", "Five numbers in a row", dice(1, 2, 3, 4, 5, score = "40pts")),
            RulesCategory("5x", "All five dice are the same", dice(6, 6, 6, 6, 6, score = "50pts")),
            RulesCategory("Chance", "The sum of all five dice", dice(2, 3, 5, 5, 6, score = "21pts")),
        ),
    ),
    RulesPage(
        title = "5x and the Joker Rule",
        tabLabel = "5x & Joker",
        blocks = listOf(
            text("If you roll five matching dice, you can score a `5x` worth **50pts**."),
            text("Roll another five matching dice after already scoring a `5x`? It earns a **100pts** bonus, on top of whatever category you then score those dice in."),
            text("When you score a repeat `5x`, the Joker rule decides where it can go:"),
            RulesStep(
                1,
                "The matching *Upper Section* category, if it's still open. Five 4s must go in `Fours`, scored for **20pts**, in addition to the bonus.",
                dice(4, 4, 4, 4, 4, score = "20pts + 100pts"),
            ),
            RulesStep(2, "Otherwise, any unscored category outside the *Upper Section*, in addition to the bonus. `Full House`, `Small Straight` and `Large Straight` score their full fixed amount."),
            RulesStep(3, "If every category outside the *Upper Section* is already filled, you must score it in an unscored *Upper Section* category for **0pts**, but you still get the **100pts** bonus."),
        ),
    ),
    RulesPage(
        title = "Tie Breaks",
        tabLabel = "Tie Breaks",
        blocks = listOf(
            text("If multiple players end the game with the same score, the following checks are made in order. The first difference decides who wins the tie:"),
            RulesStep(1, "Fewest `5x`"),
            RulesStep(2, "Most categories scored **0pts**"),
            RulesStep(3, "Lower *Upper Section* total"),
            RulesStep(4, "Lower `Chance`"),
            RulesStep(5, "Lower `3x`"),
            RulesStep(6, "Lower `4x`"),
            text("If all of these are equal, then it is a true tie."),
        ),
    ),
    RulesPage(
        title = "Mode: Tricolour",
        tabLabel = "Tricolour",
        blocks = listOf(
            text("A custom mode extending the *Standard* game mode. Every die also rolls a colour (red, yellow or blue) alongside its number."),
            text("There are four extra scoring categories:"),
            RulesCategory(
                "Reds",
                "All five dice are red",
                colouredDice(2 to DieColour.RED, 5 to DieColour.RED, 1 to DieColour.RED, 6 to DieColour.RED, 3 to DieColour.RED, score = "40pts"),
            ),
            RulesCategory(
                "Yellows",
                "All five dice are yellow",
                colouredDice(4 to DieColour.YELLOW, 4 to DieColour.YELLOW, 1 to DieColour.YELLOW, 5 to DieColour.YELLOW, 2 to DieColour.YELLOW, score = "40pts"),
            ),
            RulesCategory(
                "Blues",
                "All five dice are blue",
                colouredDice(6 to DieColour.BLUE, 3 to DieColour.BLUE, 3 to DieColour.BLUE, 2 to DieColour.BLUE, 5 to DieColour.BLUE, score = "40pts"),
            ),
            RulesCategory(
                "Coloured House",
                "Three of one colour and two of another",
                colouredDice(1 to DieColour.RED, 4 to DieColour.RED, 6 to DieColour.RED, 2 to DieColour.BLUE, 5 to DieColour.BLUE, score = "25pts"),
            ),
            text("Under the Joker rule, a repeat `5x` also scores `Coloured House` at its full **25pts**. Everything else plays exactly the same as the *Standard* rules, just with more opportunities to score."),
            text("See if you can find the Easter Egg in this mode!"),
        ),
    ),
    RulesPage(
        title = "Mode: Quickfire",
        tabLabel = "Quickfire",
        blocks = listOf(
            text("A custom mode extending the *Standard* game mode. Every game starts with the `5x` category and **six** others, picked at random, disabled. That leaves just **six** categories to score, so a game lasts only **six** turns."),
            text("A disabled category is drawn on your scorecard as a dashed outline with a slash through it, and the word Off where its score would be. It can't be scored in, not even to take a zero. Every player in a game has the same ones disabled, and a new set is picked for each game."),
            text("With `5x` disabled, there is no Joker rule and no bonus for repeat 5x. You can still roll five matching dice, but they have to be scored in another category."),
            text("The *Upper Section* bonus shrinks with the categories. It's still **35pts**, but each disabled category lowers the **63pts** you need by three of its number. For example, with `Threes` disabled you need **54pts**, which is **9pts** less. If every *Upper Section* category is disabled, there's no bonus."),
            text("The *Extended Scores* modifier's categories are never disabled."),
            text("Everything else plays exactly the same as the *Standard* rules, with three rolls a turn. Scores from this mode don't go on the *Leaderboard*, since every game has a different scorecard, but they still count towards your *Statistics*."),
        ),
    ),
    RulesPage(
        title = "Mode: Stud",
        tabLabel = "Stud",
        blocks = listOf(
            text("A custom mode extending the *Standard* game mode. Every roll is **seven** dice instead of five, but only **five** of them can score."),
            text("There are still only **five** hold slots. Tap a die on the mat to hold it - it goes to the free slot nearest it - and tap a hold slot to put its die back. Only the dice you hold score, so you can only score once all **five** slots are full. Until then, your scorecard shows what the dice you've held so far would score."),
            text("Here's an example turn:"),
            RulesStep(1, "The first roll lands three 6s. Hold them, and roll the other **four** dice again.", roll(6, 6, 6, 2, 3, 5, 1, held = 3)),
            RulesStep(2, "Another 6! Hold it too, and roll the last **three** dice.", roll(6, 6, 6, 6, 4, 2, 4, held = 4)),
            RulesStep(3, "No more 6s, but a 5 is the best of the rest. Hold it to fill the fifth slot.", roll(6, 6, 6, 6, 5, 1, 3, held = 5)),
            RulesStep(
                4,
                "Score the five held dice. They're worth **29pts** in `4x`, or **24pts** in `Sixes`.",
                dice(6, 6, 6, 6, 5, score = "29pts"),
            ),
            text("If the *Turn Timer* runs out, any empty hold slots are filled from the dice on the mat, left to right, and that hand is scored for you."),
            text("Scoring, bonuses and the Joker rule are exactly the same as the *Standard* rules. You just get more dice to choose your hand from!"),
        ),
    ),
    RulesPage(
        title = "Mode: Third Wind",
        tabLabel = "Third Wind",
        blocks = listOf(
            text("A custom mode extending the *Standard* game mode. Every category is scored **three** times instead of once, so a game lasts **39** turns."),
            text("Each category has **three** slots, stacked beside it on your scorecard. Scoring a category fills its next empty slot, and the category is worth all of its slots added up. It stays open until all **three** are filled."),
            text("There's still just one *Upper Section* bonus, but it's tripled too: score **189pts** or more across the section to earn **105pts**."),
            text("Each of the `5x` slots takes a `5x` for **50pts**. The **100pts** bonus and the Joker rule only begin once all **three** are filled, with at least one of them scoring **50pts**."),
            text("Here's an example turn, with `Fives` already holding **15pts** and **10pts**:"),
            RulesStep(1, "The first roll lands three 5s. Hold them, and roll the other **two** dice again.", roll(5, 5, 5, 2, 1, held = 3)),
            RulesStep(2, "Another 5! Hold it too, and roll the last die.", roll(5, 5, 5, 5, 3, held = 4)),
            RulesStep(3, "The last roll is a 6. That's worth **26pts** in `4x`, or **20pts** in `Fives`."),
            RulesStep(
                4,
                "Score it in `Fives`. It fills the last of its **three** slots, so `Fives` is closed for the rest of the game, worth **45pts** in total.",
                dice(5, 5, 5, 5, 6, counting = 4, score = "20pts"),
            ),
            text("In a tie break, every slot that scored **0pts** counts as a category scored **0pts**."),
            text("Scores from this mode don't go on the *Leaderboard*, but they still count towards your *Statistics*."),
            text("Everything else plays exactly the same as the *Standard* rules, just three times over!"),
        ),
    ),
    RulesPage(
        title = "Mode: Hit List",
        tabLabel = "Hit List",
        blocks = listOf(
            text("A custom mode with the *Standard* dice and **three** rolls a turn, but none of its categories. Instead, every game deals a list of **12** targets, new each game and the same for every player, plus the `Alibi`. That makes **13** turns."),
            text("A target names a number for the die in each place, left to right. Up to **two** places are left open, and any die fills those. This target is worth **20pts**:"),
            target(4, 1, 3, 2, ANY_PLACE, points = 20),
            RulesCategory(
                "Hit",
                "Every number the target names is among your dice, in any order. Scores the target's points",
                dice(2, 4, 1, 3, 5, counting = 4, score = "20pts").showing(RulesTile.Target(EXAMPLE_TARGET)),
            ),
            RulesCategory(
                "Exact Hit",
                "Every number the target names is on the die in its own place. Scores double",
                dice(4, 1, 3, 2, 6, counting = 4, score = "40pts").showing(RulesTile.Target(EXAMPLE_TARGET)),
            ),
            RulesCategory(
                "Partial Hit",
                "At least **two** of the numbers the target names are among your dice, but not all of them. Scores half the target's points, times the share of its numbers you rolled, rounded to the nearest **5pts**",
                diceCounting(4, 1, 6, 2, 5, counting = setOf(0, 1, 3), score = "10pts").showing(RulesTile.Target(EXAMPLE_TARGET)),
            ),
            text("With only **one** of a target's numbers rolled, it scores **0pts**. A partial hit still uses up its target, so the full points are gone for the rest of the game. The `Alibi` only takes a full hit."),
            text("Harder targets are worth more, and each target's tile shows its points. Naming **three** numbers is worth **10pts** or **15pts**, **four** numbers **20pts** to **30pts**, and all **five** **40pts** to **75pts**. An open place makes a target easier to hit, so it's worth less."),
            text("As you roll, each target's tile marks the numbers your dice already show with a bar beneath them, gold once a die is in its own place, and the tile lights up once it's hit."),
            RulesCategory(
                "Alibi",
                "Score a hit here instead of in its target. It's worth the points of the best open target your dice hit, never doubled, and that target stays open for another try at an exact hit",
                dice(2, 4, 1, 3, 5, counting = 4, score = "20pts").showing(RulesTile.Alibi),
            ),
            text("Held dice stay where they are, so a die in the wrong place stays there until you roll it again. If nothing scores, cross off a target or the `Alibi` for **0pts**."),
            text("Here's an example turn, chasing the target above:"),
            RulesStep(1, "The first roll has the 4 and the 1 in their places, and a 2 in the open place. Hold all **three**: the 2 counts towards a hit wherever it is.", rollHolding(4, 1, 5, 6, 2, held = setOf(0, 1, 4)).showing(RulesTile.Target(EXAMPLE_TARGET))),
            RulesStep(2, "A 3 lands in its place. That's a hit for **20pts**, but the 2 is in the open place, not the fourth. Hold the 3 too.", rollHolding(4, 1, 3, 6, 2, held = setOf(0, 1, 2, 4)).showing(RulesTile.Target(EXAMPLE_TARGET))),
            RulesStep(3, "Roll the fourth die on its own. A 2 there is an exact hit, and if it misses, the hit is safe: the last die is still a 2."),
            RulesStep(
                4,
                "It's a 2! Score the exact hit for **40pts**. Had it missed, you could take the hit for **20pts**, or score it in the `Alibi` and keep this target for another try.",
                dice(4, 1, 3, 2, 2, counting = 4, score = "40pts").showing(RulesTile.Target(EXAMPLE_TARGET)),
            ),
            text("The *Extended Scores* modifier can't be used in this mode. With *Unlucky Dice*, a locked die can't count towards a target, and a roll with one locked can't be an exact hit."),
            text("Totals here don't compare with other modes, so the achievements for a high or low score, a scorecard with no zeroes, or for winning from behind after scoring three zeroes, can't be earned in it. Scores from this mode don't go on the *Leaderboard*, since every game has a different list, but they still count towards your *Statistics*."),
        ),
    ),
    RulesPage(
        title = "Modifiers",
        tabLabel = "Modifiers",
        blocks = listOf(
            text("*Modifiers* are optional extras you can add to any game mode. Choose them on the new game screen: each can be switched on or off, and some also have a value to set."),
            text("*Modifiers* are just for fun. Scores from a game with any modifier switched on don't go on the *Leaderboard*, but they still count towards your *Statistics*."),
            text("*Turn Timer*: a limit on how long each player has to finish their whole turn, not each roll. Choose **30**, **60** or **120** seconds. A badge shows the time left and turns red as it runs out."),
            RulesTurnTimer,
            text("If a player's time runs out, their roll is scored for them in whichever open category it's worth the least in (the first one on the scorecard, if several tie)."),
            text("*Number of Rolls*: how many times a player may roll each turn, from **1** to **9**. Without it, a turn has the game mode's own number of rolls, which is **3** in every game mode."),
            text("*Stored Rolls*: any rolls a player doesn't use before scoring are kept for their next turn, on top of that turn's usual rolls, and so on. The dice cup shows the total, so it can pass **9**. Each player keeps their own."),
            text("*Stored Rolls* can also have a most rolls you can store: whatever is left over beyond it is lost when the turn is scored. Leave it empty for no limit."),
            text("Extra rolls make big scores much easier, so with either of these on, the achievements for a high score, a section total, a clean scorecard or the upper bonus can't be earned, and neither can the sunflower."),
            text("*Extended Scores*: adds three more scoring categories to every player's scorecard, after the others. It works in every game mode, so the game lasts three turns longer."),
            RulesCategory("Two Pair", "Total of the four dice making two pairs of different numbers. A fifth die is never counted, even if it matches a pair", dice(4, 4, 2, 2, 4, counting = 4, score = "12pts")),
            RulesCategory("Evens", "Total of the dice showing 2, 4 or 6", dice(6, 4, 2, 3, 1, counting = 3, score = "12pts")),
            RulesCategory("Odds", "Total of the dice showing 1, 3 or 5", dice(5, 3, 3, 6, 2, counting = 3, score = "11pts")),
            text("The extra categories have their own section, so they don't count towards the upper bonus or the lower section's total. With *Extended Scores* on, the achievements for a high score or for winning from behind after scoring three zeroes can't be earned."),
            text("*Unlucky Dice*: each time the dice are rolled, every die that was rolled has a chance of landing locked in a red cross of chains. A locked die can't be held and doesn't score, so the others are scored without it: a category that needs all five dice, like *5x* or the *Large Straight*, can't be made. Dice you are holding are never locked, and a locked die is rolled again, with the rest, on the next roll."),
            lockedDice(5, 5, 5, 2, 5, lockedIndex = 4, counting = 3, score = "15pts"),
            text("Here the last **5** is locked, so it can't be held or scored. Only the other three **5**s score in *Fives*; with the locked one it would have been **20pts**. The **2** doesn't count, as usual."),
            text("Choose the chance each rolled die has of being locked, from **10%** to **50%** in steps of **10%**, and the most dice that can be locked on one roll, from **1** to **5**. If more dice come up locked than that, a few are picked at random to be."),
            text("Locked dice make a bad turn easier, so with *Unlucky Dice* on, the achievement for winning from behind after scoring three zeroes can't be earned."),
        ),
    ),
)

/** How far in from each end of the tab row its tabs are hidden outright while there are more to
 * scroll to: the chevron's glyph (24dp, centred in its 48dp button, so ending 36dp in) plus a small
 * gap, so no label is ever drawn underneath it. */
private val TAB_EDGE_CLEAR = 40.dp

/** Past [TAB_EDGE_CLEAR], how far the tabs take to fade back in to full strength. */
private val TAB_EDGE_FADE = 24.dp

/** How much scrolling is left before an end's chevron and fade start easing away - so they fade out
 * as the last tab comes into view rather than switching off at the very end. */
private val TAB_EDGE_EASE = 48.dp

/** How much of the tab row's visible width a chevron tap scrolls by - less than all of it, so the
 * tab that was cut off at the edge is still in view afterwards, now whole. */
private const val TAB_CHEVRON_SCROLL_FRACTION = 0.6f

/**
 * The Rules page: a tab per [RulesPage] over a [HorizontalPager] of them. The tabs scroll, since the
 * list grows by a page with every game mode - a row of dots stopped saying where you were, or
 * letting you get to the last page, once there were more than a handful.
 *
 * A scrolling tab row gives no sign on its own that there's more of it: on a phone the first three
 * tabs can end right at the edge, so the row looks like all there is. A fade alone didn't fix that -
 * it only shows when a label happens to be under it, and with the selected tab centred the next one
 * can start just past the edge, leaving the fade over empty space. So each end with tabs beyond it
 * gets a [TabScrollChevron], which is there whatever the labels' widths, over a [fadeOffscreenEdges]
 * fade that keeps a cut-off label from running into it. The row and its tabs carry collection
 * semantics so TalkBack says "Tab, 1 of 7", the spoken form of the same hint.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val pagerState = rememberPagerState(pageCount = { RULES_PAGES.size })
    val coroutineScope = rememberCoroutineScope()
    val tabScrollState = rememberScrollState()

    ScreenScaffold(title = "Rules", onBack = onBack, modifier = modifier) {
        // A real Box, so the chevrons' align lands on their actual parent (see UI.md's gotchas).
        Box(modifier = Modifier.fillMaxWidth()) {
            PrimaryScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                scrollState = tabScrollState,
                // Transparent over the backdrop, like the app bar above it, and flush with the page text.
                containerColor = Color.Transparent,
                edgePadding = 0.dp,
                // Slides with the pages as they're swiped, rather than jumping once the next page is
                // the current one - see pagerIndicatorLayout.
                indicator = {
                    TabRowDefaults.PrimaryIndicator(
                        modifier = Modifier.tabIndicatorLayout { measurable, constraints, tabPositions ->
                            pagerIndicatorLayout(measurable, constraints, tabPositions, pagerState.currentPage + pagerState.currentPageOffsetFraction)
                        },
                        width = Dp.Unspecified,
                        height = TAB_INDICATOR_HEIGHT,
                    )
                },
                // Raised off the row's bottom edge so it runs through the middle of the indicator, which
                // sits on that edge - left at the bottom, the indicator rests on top of the line instead.
                // (The indicator can't be lowered onto the line: the row clips anything below its bottom.)
                divider = {
                    HorizontalDivider(modifier = Modifier.padding(bottom = (TAB_INDICATOR_HEIGHT - DividerDefaults.Thickness) / 2))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .fadeOffscreenEdges(tabScrollState, clearWidth = TAB_EDGE_CLEAR, fadeWidth = TAB_EDGE_FADE, easeDistance = TAB_EDGE_EASE)
                    .semantics { collectionInfo = CollectionInfo(rowCount = 1, columnCount = RULES_PAGES.size) },
            ) {
                RULES_PAGES.forEachIndexed { index, rulesPage ->
                    Tab(
                        selected = index == pagerState.currentPage,
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(text = rulesPage.tabLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier.semantics {
                            collectionItemInfo = CollectionItemInfo(rowIndex = 0, rowSpan = 1, columnIndex = index, columnSpan = 1)
                        },
                    )
                }
            }
            TabScrollChevron(scrollState = tabScrollState, forward = false, modifier = Modifier.align(Alignment.CenterStart))
            TabScrollChevron(scrollState = tabScrollState, forward = true, modifier = Modifier.align(Alignment.CenterEnd))
        }

        // The footer floats over the pages rather than taking a row of its own: pinned to the bottom,
        // with the page text scrolling behind it, and each page padded by the footer's measured height
        // (so a large font still clears it) plus a gap, so the last line can always scroll above it.
        var footerHeightPx by remember { mutableIntStateOf(0) }
        val density = LocalDensity.current
        val pageBottomPadding = with(density) { footerHeightPx.toDp() } + PAGE_FOOTER_GAP
        // Held here rather than inside each page, so the scrollbar beside the pager can follow whichever
        // page is showing.
        val pageScrollStates = remember { List(RULES_PAGES.size) { ScrollState(initial = 0) } }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // A gap between pages wider than the screen's side margin, so a page never shows a sliver
            // of its neighbour's text at its edge.
            HorizontalPager(state = pagerState, pageSpacing = PAGE_SPACING, modifier = Modifier.fillMaxSize()) { page ->
                val rulesPage = RULES_PAGES[page]
                Column(modifier = Modifier.fillMaxSize().verticalScroll(pageScrollStates[page]).padding(bottom = pageBottomPadding)) {
                    Text(
                        text = rulesPage.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontFamily = SoraFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.semantics { heading() },
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    for (block in rulesPage.blocks) {
                        RulesBlockView(block)
                    }
                }
            }

            // In the screen's right-hand margin, beside the text rather than over it (as on Styles),
            // and only while the page showing is too long to fit.
            VerticalScrollbar(
                scrollState = pageScrollStates[pagerState.currentPage],
                width = SCREEN_MARGIN,
                modifier = Modifier.align(Alignment.TopEnd).offset(x = SCREEN_MARGIN),
            )

            PageCountFooter(
                page = pagerState.currentPage,
                pageCount = RULES_PAGES.size,
                modifier = Modifier.align(Alignment.BottomCenter).onSizeChanged { footerHeightPx = it.height },
            )
        }
    }
}

/** Body text of every block, styled by its [parseInlineMarkup] markers - a scoring category's name
 * in the same gold monospace as a [RulesCategory]'s heading. TalkBack hears it with "pts" spoken
 * as "points" ([spokenPoints]). */
@Composable
private fun RulesBodyText(text: String, modifier: Modifier = Modifier) {
    val categoryStyle = SpanStyle(fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
    val styled = remember(text, categoryStyle) { parseInlineMarkup(text.keepCategoryNamesWhole(), codeStyle = categoryStyle) }
    Text(
        text = styled,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.semantics { contentDescription = spokenPoints(styled.text) },
    )
}

/** Swaps the spaces inside each `backticked` category name for non-breaking ones, so a name like
 * "Small Straight" never wraps across two lines. */
private fun String.keepCategoryNamesWhole(): String =
    replace(Regex("`[^`]*`")) { match -> match.value.replace(' ', '\u00A0') }

/** "15pts" as "15 points" (and "+" as "plus"), so TalkBack doesn't read the abbreviation out as letters. */
private fun spokenPoints(text: String): String =
    text.replace(Regex("""(\d+)pts\b"""), "$1 points").replace(" + ", " plus ")

/** Space under every block, so paragraphs, steps and categories are spaced alike. */
private val BLOCK_GAP = 10.dp

/** Space under a [RulesCategory] - twice [BLOCK_GAP], so each category, with its example, reads as
 * its own entry rather than running into the next. */
private val CATEGORY_GAP = 20.dp

/** How far a step's text (and its example) hangs in from its number. */
private val STEP_INDENT = 24.dp

/** Draws one [RulesBlock] of a page. */
@Composable
private fun RulesBlockView(block: RulesBlock) {
    when (block) {
        is RulesText -> RulesBodyText(block.text, modifier = Modifier.padding(bottom = BLOCK_GAP))
        RulesIllustration -> RulesIllustrationView(modifier = Modifier.padding(top = 24.dp, bottom = BLOCK_GAP))
        // One TalkBack stop describing the example, in place of the badge's own live region - which
        // would announce "time running out" as if a turn on this page really were.
        RulesTurnTimer -> TurnTimerBadge(
            secondsRemaining = TURN_TIMER_EXAMPLE_SECONDS,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = BLOCK_GAP)
                .clearAndSetSemantics {
                    contentDescription = "Example: the turn timer, turning red with $TURN_TIMER_EXAMPLE_SECONDS seconds left"
                },
        )
        is RulesDice -> RulesDiceRow(block, modifier = Modifier.padding(bottom = BLOCK_GAP))
        is RulesStep -> Column(modifier = Modifier.padding(bottom = BLOCK_GAP).semantics(mergeDescendants = true) {}) {
            Row {
                Text(
                    text = "${block.number}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(STEP_INDENT),
                )
                RulesBodyText(block.text, modifier = Modifier.weight(1f))
            }
            block.example?.let { RulesDiceRow(it, modifier = Modifier.padding(start = STEP_INDENT, top = 6.dp)) }
        }
        // One TalkBack stop for the name, what it takes and the example, not three.
        is RulesCategory -> Column(modifier = Modifier.padding(bottom = CATEGORY_GAP).semantics(mergeDescendants = true) {}) {
            Text(
                text = block.name,
                style = MaterialTheme.typography.titleSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            RulesBodyText(block.description)
            RulesDiceRow(block.example, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

/** The size of each die in a [RulesDiceRow]. */
private val EXAMPLE_DIE_SIZE = 28.dp

/** How faint a die that doesn't count towards the example's category is drawn. */
private const val EXAMPLE_DIE_FADED_ALPHA = 0.35f

/**
 * Five example dice in the Classic style (each in its own colour in a Tricolour example), then what
 * they score. Dice that don't count towards the category are faded, so "three 5s, plus two others"
 * reads at a glance. TalkBack hears it as one sentence - the dice, which don't count and the score -
 * rather than five unlabelled images.
 */
@Composable
private fun RulesDiceRow(example: RulesDice, modifier: Modifier = Modifier) {
    val spoken = remember(example) { example.spokenDescription() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.clearAndSetSemantics { contentDescription = spoken },
    ) {
        val anyPlaceColor = MaterialTheme.colorScheme.onSurfaceVariant
        for (die in example.dice) {
            val style = die.colour?.let { IvoryDiceStyle.recoloured(it.palette) } ?: IvoryDiceStyle
            val dieModifier = Modifier
                .padding(end = 6.dp)
                .size(EXAMPLE_DIE_SIZE)
                .graphicsLayer { alpha = if (die.counts || die.locked) 1f else EXAMPLE_DIE_FADED_ALPHA }
            if (example.isTarget && die.anyPlace) {
                Box(modifier = dieModifier.drawBehind { drawAnyPlace(anyPlaceColor) })
            } else if (die.locked) {
                Box(modifier = dieModifier) {
                    style.Die(value = die.value, held = false, modifier = Modifier.fillMaxSize())
                    LockedChains(shape = style.shadowShape(die.value, 0, null), reach = style.lockedChainReach, modifier = Modifier.matchParentSize())
                }
            } else {
                style.Die(value = die.value, held = false, modifier = dieModifier)
            }
        }
        example.score?.let { score ->
            Text(
                text = "= $score",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
        example.tile?.let { tile ->
            Spacer(modifier = Modifier.weight(1f))
            RulesTileView(tile, example)
        }
    }
}

/** [tile] as the board draws it for [example]'s dice - see [RulesTile]. */
@Composable
private fun RulesTileView(tile: RulesTile, example: RulesDice) {
    when (tile) {
        is RulesTile.Target -> {
            val dice = example.dice.map { Die(value = it.value) }
            val matches = if (example.isTarget) null else tile.target.matches(dice)
            CategoryTile(
                category = ScoreCategory.TARGET_1,
                // Lit, as on the board, for a hit - not a partial one.
                highlighted = !example.isTarget && tile.target.isHit(dice),
                target = tile.target,
                matches = matches,
            )
        }
        RulesTile.Alibi -> CategoryTile(category = ScoreCategory.ALIBI, highlighted = true)
    }
}

/** A target's open place: a die's outline, dashed, with nothing on it. */
private fun DrawScope.drawAnyPlace(color: Color) {
    val stroke = ANY_PLACE_STROKE.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(stroke / 2, stroke / 2),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(size.minDimension * ANY_PLACE_CORNER_FRACTION),
        style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(ANY_PLACE_DASH.toPx(), ANY_PLACE_DASH.toPx()))),
    )
}

private val ANY_PLACE_STROKE = 1.5.dp
private val ANY_PLACE_DASH = 3.dp
private const val ANY_PLACE_CORNER_FRACTION = 0.2f

/**
 * "Example: 5, 5, 5, 2, 6. The 2 and 6 don't count. Scores 23 points." - the row read aloud. A roll
 * on its way to a hand says which dice are held instead: "Example roll: 6, 6, 6, 2, 3. Held: 6, 6
 * and 6." A Hit List target says its places: "Example target: 4, 1, 3, 2, any. Worth 20 points."
 */
private fun RulesDice.spokenDescription(): String {
    fun ExampleDie.spoken() = colour?.let { "${it.name.lowercase()} $value" } ?: value.toString()
    fun List<String>.spokenList() = if (size == 1) single() else "${dropLast(1).joinToString(", ")} and ${last()}"
    if (isTarget) {
        val places = dice.joinToString(", ") { if (it.anyPlace) "any" else it.spoken() }
        return "Example target: $places.${score?.let { " Worth ${spokenPoints(it)}." }.orEmpty()}"
    }
    val tileSentence = when (val shown = tile) {
        is RulesTile.Target -> {
            val hand = dice.map { Die(value = it.value) }
            " Its tile shows ${targetProgress(shown.target, shown.target.matches(hand), shown.target.score(hand))}."
        }
        RulesTile.Alibi -> " The Alibi lights up."
        null -> ""
    }
    val all = dice.joinToString(", ") { it.spoken() }
    if (fadedNotHeld) {
        val held = dice.filter { it.counts }.map { it.spoken() }
        val heldSentence = if (held.isEmpty()) " Nothing held." else " Held: ${held.spokenList()}."
        return "Example roll: $all.$heldSentence$tileSentence"
    }
    val lockedSentence = dice.filter { it.locked }.map { it.spoken() }.takeIf { it.isNotEmpty() }
        ?.let { locked -> " The ${locked.spokenList()} ${if (locked.size == 1) "is" else "are"} locked in chains." }.orEmpty()
    val ignored = dice.filterNot { it.counts || it.locked }.map { it.spoken() }
    val ignoredSentence = lockedSentence + when (ignored.size) {
        0 -> ""
        1 -> " The ${ignored.single()} doesn't count."
        else -> " The ${ignored.spokenList()} don't count."
    }
    val scoreSentence = score?.let { " Scores ${spokenPoints(it)}." }.orEmpty()
    return "Example: $all.$ignoredSentence$scoreSentence$tileSentence"
}

/** The seconds the Modifiers page's example timer is stopped at - inside the game's last few, so it flashes. */
private const val TURN_TIMER_EXAMPLE_SECONDS = 4

/** ScreenScaffold's side margin, which the pages' scrollbar sits in. */
private val SCREEN_MARGIN = 20.dp

/** The gap between two pages of the pager - twice [SCREEN_MARGIN], so neither page's text shows in
 * the other's margin when they're not quite lined up. */
private val PAGE_SPACING = SCREEN_MARGIN * 2

/** How tall the cup in [RulesIllustrationView] stands. */
private val ILLUSTRATION_CUP_HEIGHT = 104.dp

/** How big each die in [RulesIllustrationView] is. */
private val ILLUSTRATION_DIE_SIZE = 36.dp

/**
 * The Classic cup standing beside five Classic dice - always those, whatever the player has picked,
 * as the Rules describe the game rather than their table. They play like the main menu's logo:
 * tapping the cup shakes it, as in a game, and tapping the dice rolls them (see [playLogoRoll]),
 * landing back on the faces they started on; a tap mid-shake or mid-roll is ignored. Unlike the
 * menu's dice, they unlock nothing - "Not Those Dice!" is the menu's alone.
 *
 * Decoration all the same, so no TalkBack stop: raw taps rather than clickables, which would give
 * TalkBack an unnamed "double tap to activate" that does nothing a screen reader user could use.
 * Under reduced motion neither moves, as on the menu.
 */
@Composable
private fun RulesIllustrationView(modifier: Modifier = Modifier) {
    val cupStyle = ClassicGoldDiceCupStyle
    val diceStyle = IvoryDiceStyle
    val cupWidth = ILLUSTRATION_CUP_HEIGHT * (cupStyle.shape.gridWidth / cupStyle.shape.gridHeight)
    val reduceMotion = LocalReduceMotion.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    // How far into a roll the dice are, or null at rest; and whether the cup is mid-shake.
    var rollMillis by remember { mutableStateOf<Float?>(null) }
    var cupShaking by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier.fillMaxWidth().clearAndSetSemantics {},
    ) {
        cupStyle.Cup(
            rolling = cupShaking,
            tilted = false,
            modifier = Modifier
                .size(width = cupWidth, height = ILLUSTRATION_CUP_HEIGHT)
                .pointerInput(reduceMotion) {
                    detectTapGestures {
                        if (!cupShaking && !reduceMotion) {
                            cupShaking = true
                            scope.launch {
                                lifecycle.delayWhileResumed(CUP_SHAKE_MILLIS)
                                cupShaking = false
                            }
                        }
                    }
                },
        )
        Spacer(modifier = Modifier.width(12.dp))
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.pointerInput(reduceMotion) {
                detectTapGestures {
                    if (rollMillis == null && !reduceMotion) {
                        scope.launch {
                            playLogoRoll { rollMillis = it }
                            rollMillis = null
                        }
                    }
                }
            },
        ) {
            // The menu's fan, die for die: the same faces in the same order, tilts and drops.
            LOGO_DICE.forEachIndexed { index, die ->
                val pose = rollMillis?.let { logoRollPose(index, die.value, it) }
                diceStyle.Die(
                    value = pose?.value ?: die.value,
                    held = false,
                    modifier = Modifier
                        .padding(horizontal = 2.dp)
                        .offset(y = die.drop - ILLUSTRATION_DIE_SIZE * (pose?.hop ?: 0f))
                        .size(ILLUSTRATION_DIE_SIZE)
                        .rotate(die.tilt + (pose?.spinDegrees ?: 0f)),
                )
            }
        }
    }
}

/**
 * Places the tab row's indicator under [pagePosition] - the pager's current page plus how far it's
 * been swiped towards the next (`currentPage + currentPageOffsetFraction`) - so it slides between
 * two tabs with the finger, its width easing from one label's to the other's. Read during layout,
 * so following a swipe re-places the indicator without recomposing the tab row.
 */
private fun MeasureScope.pagerIndicatorLayout(
    measurable: Measurable,
    constraints: Constraints,
    tabPositions: List<TabPosition>,
    pagePosition: Float,
): MeasureResult {
    val position = pagePosition.coerceIn(0f, (tabPositions.size - 1).toFloat())
    val from = tabPositions[position.toInt()]
    val to = tabPositions[(position.toInt() + 1).coerceAtMost(tabPositions.size - 1)]
    val fraction = position - position.toInt()
    // Under the label, as the stock indicator sits (matchContentSize), not the whole tab. The
    // positions a scrollable tab row hands a custom indicator start at the label, not the tab - its
    // `left` is already in by the tab's padding - so the label's centre is half its width along.
    val width = lerp(from.contentWidth, to.contentWidth, fraction)
    val centre = lerp(from.left + from.contentWidth / 2, to.left + to.contentWidth / 2, fraction)
    val widthPx = width.roundToPx()
    val placeable = measurable.measure(constraints.copy(minWidth = widthPx, maxWidth = widthPx))
    return layout(placeable.width, placeable.height) {
        placeable.place(x = (centre - width / 2).roundToPx(), y = 0)
    }
}

/** The tab row's indicator, as thick as the stock one - named so the divider can centre itself on it. */
private val TAB_INDICATOR_HEIGHT = 3.dp

/** Space between the end of a page's text and the top of the footer, once scrolled to the bottom. */
private val PAGE_FOOTER_GAP = 8.dp

/**
 * "1 of 7" pinned to the bottom of the pages - where you are and how many there are, in one glance,
 * alongside the tab row's chevrons (which say only that there's more). A small gold pill drawn over the
 * pages, not a row of its own, so it costs the pages no height: longer text scrolls behind it, the
 * pill's own background keeping it readable on top. Drawn by the shared [FooterPill].
 *
 * TalkBack hears "Page 1 of 7", and as a polite live region it's announced again whenever the page
 * changes - a swipe through the pager otherwise lands on a new page without a word.
 */
@Composable
private fun PageCountFooter(page: Int, pageCount: Int, modifier: Modifier = Modifier) {
    FooterPill(
        text = "${page + 1} of $pageCount",
        modifier = modifier.semantics {
            contentDescription = "Page ${page + 1} of $pageCount"
            liveRegion = LiveRegionMode.Polite
        },
    )
}

/**
 * Hides a horizontally scrolling row's content at whichever end has more beyond it: fully across
 * [clearWidth], where that end's [TabScrollChevron] sits, so its glyph is never drawn over a label,
 * then fading back in over [fadeWidth]. Both deepen with how far there is left to scroll (up to
 * [easeDistance]), so they ease away as the last tab comes fully into view instead of vanishing at
 * the end. Drawn only: nothing is hidden from touch or from TalkBack.
 */
private fun Modifier.fadeOffscreenEdges(scrollState: ScrollState, clearWidth: Dp, fadeWidth: Dp, easeDistance: Dp): Modifier = this
    // Offscreen so the DstIn masks below cut into the row's own pixels, not the backdrop behind it.
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val width = (clearWidth + fadeWidth).toPx().coerceAtMost(size.width / 2)
        if (width <= 0f) return@drawWithContent
        // Where along the mask the clear zone gives way to the fade, as a fraction of it.
        val clearFraction = (clearWidth.toPx() / width).coerceIn(0f, 1f)
        val ease = easeDistance.toPx()
        val startStrength = scrollState.edgeStrength(forward = false, over = ease)
        val endStrength = scrollState.edgeStrength(forward = true, over = ease)
        if (startStrength > 0f) {
            val edge = Color.Black.copy(alpha = 1f - startStrength)
            drawRect(
                brush = Brush.horizontalGradient(0f to edge, clearFraction to edge, 1f to Color.Black, startX = 0f, endX = width),
                size = Size(width, size.height),
                blendMode = BlendMode.DstIn,
            )
        }
        if (endStrength > 0f) {
            val edge = Color.Black.copy(alpha = 1f - endStrength)
            drawRect(
                brush = Brush.horizontalGradient(
                    0f to Color.Black,
                    (1f - clearFraction) to edge,
                    1f to edge,
                    startX = size.width - width,
                    endX = size.width,
                ),
                topLeft = Offset(size.width - width, 0f),
                size = Size(width, size.height),
                blendMode = BlendMode.DstIn,
            )
        }
    }

/**
 * How much more there is to scroll towards one end, from 0 (none) to 1 ([over] or more) - so an edge
 * hint deepens with the distance left rather than switching on and off, and eases away as the last
 * tab comes fully into view instead of vanishing at the end.
 */
private fun ScrollState.edgeStrength(forward: Boolean, over: Float): Float {
    val remaining = if (forward) maxValue - value else value
    return (remaining / over).coerceIn(0f, 1f)
}

/**
 * A chevron at one end of the tab row while there are more tabs that way - the hint that there's
 * more, which the fade can't give by itself when no label happens to be under it. A tap scrolls the
 * row that way rather than changing page: it points at more tabs, not at the next page.
 *
 * Kept out of TalkBack (`clearAndSetSemantics`): the tabs it scrolls to are TalkBack stops already,
 * each announced with its position and the count ("Tab, 5 of 7"), so it would only add a stop that
 * says nothing new.
 */
@Composable
private fun TabScrollChevron(scrollState: ScrollState, forward: Boolean, modifier: Modifier = Modifier) {
    val coroutineScope = rememberCoroutineScope()
    val shown by remember(scrollState, forward) {
        derivedStateOf { if (forward) scrollState.canScrollForward else scrollState.canScrollBackward }
    }
    // Not composed at all when there's nothing that way, so it never sits over a tab taking its taps.
    if (!shown) return

    IconButton(
        onClick = {
            val distance = scrollState.viewportSize * TAB_CHEVRON_SCROLL_FRACTION
            coroutineScope.launch { scrollState.animateScrollBy(if (forward) distance else -distance) }
        },
        modifier = modifier
            .graphicsLayer { alpha = scrollState.edgeStrength(forward, over = TAB_EDGE_EASE.toPx()) }
            .clearAndSetSemantics {},
    ) {
        Icon(
            imageVector = if (forward) Icons.AutoMirrored.Filled.KeyboardArrowRight else Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}
