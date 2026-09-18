package net.zodac.dicefive.game

import kotlin.random.Random
import net.zodac.dicefive.model.GameState
import net.zodac.dicefive.model.ScoreCategory

/**
 * Placeholder v1 AI strategy: always uses all 3 rolls without holding any
 * dice, then scores the highest-value currently open category. Difficulty
 * (Easy/Medium/Hard) is not yet wired up — see .claude/DESIGN.md.
 */
object AiTurnPlayer {

    /** The category an AI would choose for its current (fully-rolled) dice. */
    fun chooseCategory(state: GameState): ScoreCategory {
        val player = requireNotNull(state.currentPlayer) { "No current player" }
        val available = ScoreCalculator.availableCategories(player, state.dice)
        check(available.isNotEmpty()) { "No available categories to score" }
        return available.maxBy { ScoreCalculator.scoreFor(player, it, state.dice) }
    }

    /** Pure end-to-end simulation of an AI's whole turn: roll x3, then score. Used by tests and as a reference for GameViewModel's animated version. */
    fun playTurn(state: GameState, random: Random = Random.Default): GameState {
        var current = state
        while (current.rollsRemaining > 0) {
            current = GameEngine.rollDice(current, random)
        }
        return GameEngine.commitScore(current, chooseCategory(current))
    }
}
