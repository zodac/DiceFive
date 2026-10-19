package net.zodac.dicefive.game

import kotlin.random.Random
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.GameState
import net.zodac.dicefive.model.ScoreCategory

/**
 * What the coach card says at a [TutorialStep] - the words themselves are the UI's (`ui/tutorial`), one string per
 * entry, so the script stays pure.
 */
enum class TutorialMessage {
    WELCOME,
    HOLD_SIXES,
    ROLL_AGAIN,
    HOLD_FOURTH_SIX,
    ROLL_LAST,
    SCORE_FIVE_X,
    ROLL_NEW_TURN,
    ROLL_OTHER_DICE,
    ROLL_LAST_TRY,
    HOLD_IT_TOO,
    HOLD_CHASE_STRAIGHT,
    ROLL_FOR_FIVE,
    ROLL_FOR_FIVE_AGAIN,
    SCORE_CHANCE,
    HOLD_RUN,
    HOLD_RUN_FIVE,
    ROLL_FOR_RUN_END,
    SCORE_LARGE_STRAIGHT,
    HOLD_THREES,
    SCORE_THREES,
    HOLD_STRAIGHT_AGAIN,
    ROLL_FOR_FOUR,
    ROLL_FOR_FOUR_AGAIN,
    SCORE_ZERO,
    SCORE_FOURS_EARLY,
    HOLD_FIVES,
    SCORE_FIVES_MISTAKE,
    UNDO_MISTAKE,
    SCORE_FOUR_X,
    HOLD_TWOS,
    SCORE_TWOS,
    HOLD_FULL_HOUSE_START,
    HOLD_FULL_HOUSE_PAIR,
    SCORE_FULL_HOUSE,
    HOLD_FIVE,
    SCORE_FIVES,
    OPEN_TOTALS,
    UPPER_PROGRESS,
    HOLD_ONES,
    SCORE_JOKER,
    HOLD_SIX,
    SCORE_SIXES,
    OPEN_TOTALS_BONUS,
    BONUS_EARNED,
    HOLD_FOURS_THREE_X,
    HOLD_HIGH_DICE,
    SCORE_THREE_X,
}

/** One step of a [TutorialScript]: what the coach card says, and what moves the tutorial on. */
sealed interface TutorialStep {
    val message: TutorialMessage

    /** The player taps the cup, and the five dice land as [dice] (the held ones are already showing their values). */
    data class Roll(override val message: TutorialMessage, val dice: List<Int>) : TutorialStep

    /** The player holds each of [dice] (by position), in any order; nothing else can be held. */
    data class Hold(override val message: TutorialMessage, val dice: Set<Int>) : TutorialStep

    /** The player scores the turn in [category], the only box that can be tapped. */
    data class Score(override val message: TutorialMessage, val category: ScoreCategory) : TutorialStep

    /** The player takes back the score just made, with Undo - which only this step allows. */
    data class Undo(override val message: TutorialMessage) : TutorialStep

    /** The player opens the totals: the step moves on once they're showing. */
    data class OpenTotals(override val message: TutorialMessage) : TutorialStep
}

/**
 * A guided game: every roll and every move fixed in [steps], for [gameMode], played solo. Each step must be done before
 * the next; the game is over once the last is.
 */
class TutorialScript(val gameMode: GameMode, val steps: List<TutorialStep>) {

    /** How many turns the guided game has: one per score that isn't then taken back with Undo. */
    val turns: Int = steps.indices.count { endsTurn(it) }

    /** The turn (from 1) the step at [stepIndex] is part of. */
    fun turnOf(stepIndex: Int): Int = (0 until stepIndex).count { endsTurn(it) } + 1

    private fun endsTurn(index: Int): Boolean = steps[index] is TutorialStep.Score && steps.getOrNull(index + 1) !is TutorialStep.Undo

    /** Where each turn starts in [steps]: the first step, and the one after each step that ends a turn. */
    val turnStarts: List<Int> = listOf(0) + steps.indices.filter { endsTurn(it) }.map { it + 1 }.filter { it < steps.size }

    /**
     * [start] with the first [stepCount] steps played as the script says: each roll landing as it's written, each hold
     * made, each score entered - and an Undo taking the last one back.
     */
    fun replay(start: GameState, stepCount: Int): GameState {
        var state = start
        var beforeScore = state
        for (step in steps.take(stepCount)) {
            when (step) {
                is TutorialStep.Roll -> state = GameEngine.rollDice(state, TutorialDice(step.dice, state.dice.map { it.isHeld }))
                is TutorialStep.Hold -> step.dice.forEach { state = GameEngine.toggleHold(state, it) }
                is TutorialStep.Score -> {
                    beforeScore = state
                    state = GameEngine.commitScore(state, step.category)
                }
                is TutorialStep.Undo -> state = beforeScore
                is TutorialStep.OpenTotals -> Unit
            }
        }
        return state
    }

    companion object {
        /**
         * Standard, for a player new to the rules. Their 13 turns show, in order: holding and rerolling to a 5x, Chance for
         * a straight that didn't come, a Large Straight, the upper section, a zero for the next missed straight (Chance
         * used), scoring on the first roll, 4x adding all five dice (after a score in the wrong box, taken back with Undo),
         * a Full House, the totals, a joker (a second 5x, +100), the upper bonus and 3x, ending on 382. Every turn but the
         * first-roll one takes all three rolls to get there.
         */
        val STANDARD = TutorialScript(
            GameMode.STANDARD,
            listOf(
                // 1: rolling, holding, all three rolls, 5x.
                TutorialStep.Roll(TutorialMessage.WELCOME, listOf(6, 2, 6, 5, 6)),
                TutorialStep.Hold(TutorialMessage.HOLD_SIXES, setOf(0, 2, 4)),
                TutorialStep.Roll(TutorialMessage.ROLL_AGAIN, listOf(6, 3, 6, 6, 6)),
                TutorialStep.Hold(TutorialMessage.HOLD_FOURTH_SIX, setOf(3)),
                TutorialStep.Roll(TutorialMessage.ROLL_LAST, listOf(6, 6, 6, 6, 6)),
                TutorialStep.Score(TutorialMessage.SCORE_FIVE_X, ScoreCategory.FIVE_OF_A_KIND),
                // 2: chasing a straight that doesn't come, into Chance.
                TutorialStep.Roll(TutorialMessage.ROLL_NEW_TURN, listOf(4, 6, 2, 6, 3)),
                TutorialStep.Hold(TutorialMessage.HOLD_CHASE_STRAIGHT, setOf(0, 1, 2, 4)),
                TutorialStep.Roll(TutorialMessage.ROLL_FOR_FIVE, listOf(4, 6, 2, 2, 3)),
                TutorialStep.Roll(TutorialMessage.ROLL_FOR_FIVE_AGAIN, listOf(4, 6, 2, 3, 3)),
                TutorialStep.Score(TutorialMessage.SCORE_CHANCE, ScoreCategory.CHANCE),
                // 3: building a Large Straight - the spare 4 and 2 rerolled.
                TutorialStep.Roll(TutorialMessage.ROLL_NEW_TURN, listOf(4, 2, 4, 3, 2)),
                TutorialStep.Hold(TutorialMessage.HOLD_RUN, setOf(0, 1, 3)),
                TutorialStep.Roll(TutorialMessage.ROLL_OTHER_DICE, listOf(4, 2, 5, 3, 5)),
                TutorialStep.Hold(TutorialMessage.HOLD_RUN_FIVE, setOf(2)),
                TutorialStep.Roll(TutorialMessage.ROLL_FOR_RUN_END, listOf(4, 2, 5, 3, 6)),
                TutorialStep.Score(TutorialMessage.SCORE_LARGE_STRAIGHT, ScoreCategory.LARGE_STRAIGHT),
                // 4: the upper section.
                TutorialStep.Roll(TutorialMessage.ROLL_NEW_TURN, listOf(1, 5, 3, 6, 2)),
                TutorialStep.Hold(TutorialMessage.HOLD_THREES, setOf(2)),
                TutorialStep.Roll(TutorialMessage.ROLL_OTHER_DICE, listOf(4, 3, 3, 1, 6)),
                TutorialStep.Hold(TutorialMessage.HOLD_IT_TOO, setOf(1)),
                TutorialStep.Roll(TutorialMessage.ROLL_LAST_TRY, listOf(2, 3, 3, 3, 6)),
                TutorialStep.Score(TutorialMessage.SCORE_THREES, ScoreCategory.THREES),
                // 5: another straight that doesn't come - with Chance used, a zero.
                TutorialStep.Roll(TutorialMessage.ROLL_NEW_TURN, listOf(6, 3, 2, 6, 5)),
                TutorialStep.Hold(TutorialMessage.HOLD_STRAIGHT_AGAIN, setOf(1, 2, 4)),
                TutorialStep.Roll(TutorialMessage.ROLL_FOR_FOUR, listOf(1, 3, 2, 6, 5)),
                TutorialStep.Roll(TutorialMessage.ROLL_FOR_FOUR_AGAIN, listOf(1, 3, 2, 1, 5)),
                TutorialStep.Score(TutorialMessage.SCORE_ZERO, ScoreCategory.SMALL_STRAIGHT),
                // 6: scoring on the first roll.
                TutorialStep.Roll(TutorialMessage.ROLL_NEW_TURN, listOf(4, 2, 4, 1, 4)),
                TutorialStep.Score(TutorialMessage.SCORE_FOURS_EARLY, ScoreCategory.FOURS),
                // 7: 4x adds up all five dice - scored first in Fives, and taken back with Undo.
                TutorialStep.Roll(TutorialMessage.ROLL_NEW_TURN, listOf(3, 5, 1, 5, 2)),
                TutorialStep.Hold(TutorialMessage.HOLD_FIVES, setOf(1, 3)),
                TutorialStep.Roll(TutorialMessage.ROLL_OTHER_DICE, listOf(5, 5, 6, 5, 2)),
                TutorialStep.Hold(TutorialMessage.HOLD_IT_TOO, setOf(0)),
                TutorialStep.Roll(TutorialMessage.ROLL_LAST_TRY, listOf(5, 5, 6, 5, 5)),
                TutorialStep.Score(TutorialMessage.SCORE_FIVES_MISTAKE, ScoreCategory.FIVES),
                TutorialStep.Undo(TutorialMessage.UNDO_MISTAKE),
                TutorialStep.Score(TutorialMessage.SCORE_FOUR_X, ScoreCategory.FOUR_OF_A_KIND),
                // 8: Twos.
                TutorialStep.Roll(TutorialMessage.ROLL_NEW_TURN, listOf(4, 6, 2, 5, 1)),
                TutorialStep.Hold(TutorialMessage.HOLD_TWOS, setOf(2)),
                TutorialStep.Roll(TutorialMessage.ROLL_OTHER_DICE, listOf(1, 2, 2, 6, 3)),
                TutorialStep.Hold(TutorialMessage.HOLD_IT_TOO, setOf(1)),
                TutorialStep.Roll(TutorialMessage.ROLL_LAST_TRY, listOf(4, 2, 2, 5, 2)),
                TutorialStep.Score(TutorialMessage.SCORE_TWOS, ScoreCategory.TWOS),
                // 9: building a Full House.
                TutorialStep.Roll(TutorialMessage.ROLL_NEW_TURN, listOf(1, 3, 6, 3, 2)),
                TutorialStep.Hold(TutorialMessage.HOLD_FULL_HOUSE_START, setOf(1, 3)),
                TutorialStep.Roll(TutorialMessage.ROLL_OTHER_DICE, listOf(3, 3, 5, 3, 2)),
                TutorialStep.Hold(TutorialMessage.HOLD_FULL_HOUSE_PAIR, setOf(0, 4)),
                TutorialStep.Roll(TutorialMessage.ROLL_LAST_TRY, listOf(3, 3, 2, 3, 2)),
                TutorialStep.Score(TutorialMessage.SCORE_FULL_HOUSE, ScoreCategory.FULL_HOUSE),
                // 10: Fives - every other die a number whose box is already filled.
                TutorialStep.Roll(TutorialMessage.ROLL_NEW_TURN, listOf(2, 4, 4, 2, 5)),
                TutorialStep.Hold(TutorialMessage.HOLD_FIVE, setOf(4)),
                TutorialStep.Roll(TutorialMessage.ROLL_OTHER_DICE, listOf(5, 4, 2, 4, 5)),
                TutorialStep.Hold(TutorialMessage.HOLD_IT_TOO, setOf(0)),
                TutorialStep.Roll(TutorialMessage.ROLL_LAST_TRY, listOf(5, 3, 5, 2, 5)),
                TutorialStep.Score(TutorialMessage.SCORE_FIVES, ScoreCategory.FIVES),
                // 11: the totals so far, then a joker.
                TutorialStep.OpenTotals(TutorialMessage.OPEN_TOTALS),
                TutorialStep.Roll(TutorialMessage.UPPER_PROGRESS, listOf(4, 1, 6, 2, 1)),
                TutorialStep.Hold(TutorialMessage.HOLD_ONES, setOf(1, 4)),
                TutorialStep.Roll(TutorialMessage.ROLL_OTHER_DICE, listOf(1, 1, 5, 3, 1)),
                TutorialStep.Hold(TutorialMessage.HOLD_IT_TOO, setOf(0)),
                TutorialStep.Roll(TutorialMessage.ROLL_LAST_TRY, listOf(1, 1, 1, 1, 1)),
                TutorialStep.Score(TutorialMessage.SCORE_JOKER, ScoreCategory.ONES),
                // 12: Sixes, which takes the upper section past 63.
                TutorialStep.Roll(TutorialMessage.ROLL_NEW_TURN, listOf(2, 5, 4, 6, 1)),
                TutorialStep.Hold(TutorialMessage.HOLD_SIX, setOf(3)),
                TutorialStep.Roll(TutorialMessage.ROLL_OTHER_DICE, listOf(6, 1, 2, 6, 5)),
                TutorialStep.Hold(TutorialMessage.HOLD_IT_TOO, setOf(0)),
                TutorialStep.Roll(TutorialMessage.ROLL_LAST_TRY, listOf(6, 2, 6, 6, 3)),
                TutorialStep.Score(TutorialMessage.SCORE_SIXES, ScoreCategory.SIXES),
                // 13: the upper bonus, then 3x - keeping high dice, since it adds up all five.
                TutorialStep.OpenTotals(TutorialMessage.OPEN_TOTALS_BONUS),
                TutorialStep.Roll(TutorialMessage.BONUS_EARNED, listOf(1, 4, 2, 4, 1)),
                TutorialStep.Hold(TutorialMessage.HOLD_FOURS_THREE_X, setOf(1, 3)),
                TutorialStep.Roll(TutorialMessage.ROLL_OTHER_DICE, listOf(6, 4, 2, 4, 5)),
                TutorialStep.Hold(TutorialMessage.HOLD_HIGH_DICE, setOf(0, 4)),
                TutorialStep.Roll(TutorialMessage.ROLL_LAST_TRY, listOf(6, 4, 4, 4, 5)),
                TutorialStep.Score(TutorialMessage.SCORE_THREE_X, ScoreCategory.THREE_OF_A_KIND),
            ),
        )
    }
}

/**
 * The dice a [TutorialStep.Roll] lands on, as [GameEngine.rollDice] draws them: each
 * unheld die in turn, left to right, takes its value from [dice]. Only for a mode with plain six-sided dice and no
 * modifier that draws more.
 */
internal class TutorialDice(private val dice: List<Int>, held: List<Boolean>) : Random() {
    private val values = dice.indices.filter { !held[it] }.map { dice[it] }.iterator()

    override fun nextBits(bitCount: Int): Int = error("A tutorial roll draws only its dice's values")

    override fun nextInt(from: Int, until: Int): Int = values.next().also { require(it in from until until) { "$it isn't a face of $dice" } }
}
