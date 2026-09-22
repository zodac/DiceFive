package net.zodac.dicefive.game

import kotlin.random.Random
import net.zodac.dicefive.model.Die
import net.zodac.dicefive.model.GameState
import net.zodac.dicefive.model.GameType
import net.zodac.dicefive.model.PlayerConfig
import net.zodac.dicefive.model.PlayerState
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.model.TurnPhase

/**
 * Pure reducers for the turn flow. None of these touch Android APIs
 * or persistence — [net.zodac.dicefive.ui.game.GameViewModel] is the only
 * caller and owns all side effects (AI pacing, score persistence).
 */
object GameEngine {

    private const val DICE_COUNT = 5
    private const val ROLLS_PER_TURN = 3

    fun newGame(players: List<PlayerConfig>, gameType: GameType = GameType.CLASSIC): GameState {
        require(players.isNotEmpty()) { "At least one player is required" }
        return GameState(
            gameType = gameType,
            players = players.map { PlayerState(name = it.name, type = it.type, difficulty = it.difficulty) },
        )
    }

    fun rollDice(state: GameState, random: Random = Random.Default): GameState {
        check(state.rollsRemaining > 0) { "No rolls remaining this turn" }
        val newDice = state.dice.map { die ->
            if (die.isHeld) die else die.copy(value = random.nextInt(1, 7))
        }
        return state.copy(
            dice = newDice,
            rollsRemaining = state.rollsRemaining - 1,
            phase = TurnPhase.ROLLED,
        )
    }

    // No rollsRemaining check (there used to be one, forbidding it after the final roll): holding
    // has no effect on a roll that will never happen, but forbidding it bought nothing either -
    // it just made the UI look broken (dice suddenly stop responding to taps) for zero functional
    // reason, and blocked superuser cycling at exactly the point in a turn it's most likely to be
    // used (right after seeing the final roll).
    fun toggleHold(state: GameState, dieIndex: Int): GameState {
        check(state.phase == TurnPhase.ROLLED) { "Cannot hold dice before rolling" }
        val newDice = state.dice.mapIndexed { index, die ->
            if (index == dieIndex) die.copy(isHeld = !die.isHeld) else die
        }
        return state.copy(dice = newDice)
    }

    /**
     * Superuser-mode-only: advances a single held die to the next face (wrapping 6 back to 1),
     * ignoring the normal "already rolled this turn" / "rolls remaining" rules that [rollDice]
     * enforces. Held state, every other die, and rolls-remaining are all left untouched - this
     * only ever changes the one die's value. Deterministic (not random) so holding down cycles
     * through every face in a predictable order.
     */
    fun cycleDieValue(state: GameState, dieIndex: Int): GameState {
        val newDice = state.dice.mapIndexed { index, die ->
            if (index == dieIndex) die.copy(value = if (die.value >= 6) 1 else die.value + 1) else die
        }
        return state.copy(dice = newDice)
    }

    fun commitScore(state: GameState, category: ScoreCategory): GameState {
        check(state.phase == TurnPhase.ROLLED) { "Cannot score before rolling" }
        val player = requireNotNull(state.currentPlayer) { "No current player" }
        check(category in ScoreCalculator.availableCategories(player, state.dice)) {
            "$category is not available for the current dice"
        }

        val value = ScoreCalculator.scoreFor(player, category, state.dice)
        val bonus = ScoreCalculator.awardsFiveOfAKindBonus(player, state.dice)
        val updatedPlayer = player.copy(
            scorecard = player.scorecard + (category to value),
            fiveOfAKindBonusCount = player.fiveOfAKindBonusCount + if (bonus) 1 else 0,
        )
        val updatedPlayers = state.players.toMutableList().apply { this[state.currentPlayerIndex] = updatedPlayer }
        return advanceTurn(state.copy(players = updatedPlayers))
    }

    private fun advanceTurn(state: GameState): GameState {
        if (state.players.all { it.isScorecardComplete }) {
            return state.copy(isGameOver = true)
        }
        return state.copy(
            currentPlayerIndex = (state.currentPlayerIndex + 1) % state.players.size,
            dice = List(DICE_COUNT) { Die() },
            rollsRemaining = ROLLS_PER_TURN,
            phase = TurnPhase.AWAITING_ROLL,
        )
    }
}
