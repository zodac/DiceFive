package net.zodac.dicefive.game

import net.zodac.dicefive.model.Die
import net.zodac.dicefive.model.GameState
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.model.TurnPhase

/**
 * Finishing a hand for the player, in a mode where only the held dice score ([net.zodac.dicefive.model.GameMode.scoresHeldDiceOnly]).
 *
 * Once a turn's last roll is down, holding is only a way of telling the game which dice make the hand - and the box the player
 * taps already says what they mean by it. So a box can be scored with fewer dice held than the hand needs: every way of
 * completing the held dice to a whole hand is tried in that box, and the best one is the hand. Holds still matter
 * mid-turn, where they decide what is rolled again.
 *
 * Where several completions score the same in the tapped box, the first - the one using the leftmost dice - is taken: they are
 * worth the same, and the choice is only ever between dice that make no difference to the score.
 * In a mode where every die scores, a hand is already whole, and everything here returns it unchanged.
 */
object HandCompletion {

    /**
     * Every distinct way to hold dice up to a whole hand, keeping what is already held: [state] itself when it already
     * has a whole hand (or no roll has landed yet). Empty only if there aren't enough dice that can be held.
     */
    fun completions(state: GameState): List<GameState> {
        if (state.phase != TurnPhase.ROLLED || state.hasFullHand) return listOf(state)
        val free = state.dice.indices.filter { !state.dice[it].isHeld && !state.dice[it].isUnlucky }
        val needed = state.handSize - state.scoringDice.size
        val completions = ArrayList<GameState>()
        forEachCombination(free, needed) { picked -> completions += picked.fold(state) { held, index -> GameEngine.toggleHold(held, index) } }
        return completions
    }

    /** The completion of [state]'s held dice that is worth most in [category], or null if no completion can score there. */
    fun bestCompletion(state: GameState, category: ScoreCategory): GameState? {
        val player = state.currentPlayer ?: return null
        return completions(state)
            .filter { category in ScoreCalculator.availableCategories(player, it.scoringDice) }
            // maxByOrNull keeps the first of equal values: the leftmost dice.
            .maxByOrNull { worth(it, category) }
    }

    /**
     * The hand each box that can be scored would be scored with: its best completion of [state]'s held dice. A box that
     * can't be scored by any completion isn't in it. Every box, in a mode where [state] already has a whole hand.
     */
    fun projectedHands(state: GameState): Map<ScoreCategory, ProjectedHand> {
        val player = state.currentPlayer ?: return emptyMap()
        val completions = completions(state)
        return player.categories.mapNotNull { category ->
            val scoring = completions.filter { category in ScoreCalculator.availableCategories(player, it.scoringDice) }
            val best = scoring.maxByOrNull { worth(it, category) } ?: return@mapNotNull null
            val bestWorth = worth(best, category)
            // Other hands worth just as much, but of different faces: swapping dice for the same faces changes nothing to see.
            val used = best.heldIndices()
            val tied = scoring.filter { worth(it, category) == bestWorth }.distinctBy { it.faces() }
            val alternatives = tied.flatMap { it.heldIndices() }.toSet() - used
            category to ProjectedHand(best.scoringDice, used, alternatives)
        }.toMap()
    }

    /** The first box, in scorecard order, that any completion of [state]'s held dice can score in - where a turn that ran out of time goes. */
    fun timeoutCategory(state: GameState): ScoreCategory? {
        val player = state.currentPlayer ?: return null
        val firstFor = completions(state).map { ScoreCalculator.timeoutCategory(player, it.scoringDice) }
        return player.categories.firstOrNull { it in firstFor }
    }

    private fun GameState.heldIndices(): Set<Int> = dice.indices.filterTo(HashSet()) { dice[it].isHeld }

    // The held faces, in an order that doesn't depend on which dice they are: two hands with the same faces look the same.
    private fun GameState.faces(): List<Pair<Int, Int>> = scoringDice.map { it.value to (it.colour?.ordinal ?: -1) }.sortedWith(compareBy({ it.first }, { it.second }))

    private fun worth(completion: GameState, category: ScoreCategory): Int {
        val player = requireNotNull(completion.currentPlayer)
        val hand = completion.scoringDice
        return ScoreCalculator.scoreFor(player, category, hand) + ScoreCalculator.fiveOfAKindBonusFor(player, hand)
    }

    private fun forEachCombination(items: List<Int>, size: Int, action: (List<Int>) -> Unit) {
        fun recurse(start: Int, picked: List<Int>) {
            if (picked.size == size) {
                action(picked)
                return
            }
            for (at in start until items.size) {
                if (items.size - at < size - picked.size) return
                recurse(at + 1, picked + items[at])
            }
        }
        recurse(0, emptyList())
    }
}

/**
 * The hand a box would be scored with before the player has held all of it: [dice] (the hand, in slot order), which of the dice on
 * the table it takes ([used], by index, including any already held) and which others would have done just as well ([alternatives]).
 */
data class ProjectedHand(val dice: List<Die>, val used: Set<Int>, val alternatives: Set<Int>)
