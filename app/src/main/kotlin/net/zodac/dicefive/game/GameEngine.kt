package net.zodac.dicefive.game

import kotlin.random.Random
import net.zodac.dicefive.model.Die
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.GameState
import net.zodac.dicefive.model.PlayerConfig
import net.zodac.dicefive.model.PlayerState
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.model.TurnPhase
import net.zodac.dicefive.model.TurnTimer

/**
 * Pure reducers for the turn flow. None of these touch Android APIs
 * or persistence — [net.zodac.dicefive.ui.game.GameViewModel] is the only
 * caller and owns all side effects (AI pacing, score persistence).
 */
object GameEngine {

    fun newGame(
        players: List<PlayerConfig>,
        gameMode: GameMode = GameMode.default,
        turnTimer: TurnTimer = TurnTimer.NONE,
    ): GameState {
        require(players.isNotEmpty()) { "At least one player is required" }
        return GameState(
            gameMode = gameMode,
            turnTimer = turnTimer,
            players = players.map {
                PlayerState(name = it.name, type = it.type, difficulty = it.difficulty, gameMode = gameMode)
            },
        )
    }

    /**
     * Rerolls every unheld die: a number from [GameMode.dieValues], then - only in a mode whose dice
     * have colours - a colour from [GameMode.dieColours], each equally likely. A colourless mode
     * never draws that second number, so its dice come out of [random] exactly as they always have.
     */
    fun rollDice(state: GameState, random: Random = Random.Default): GameState {
        check(state.rollsRemaining > 0) { "No rolls remaining this turn" }
        val values = state.gameMode.dieValues
        val colours = state.gameMode.dieColours
        val newDice = state.dice.map { die ->
            if (die.isHeld) {
                die
            } else {
                val value = random.nextInt(values.first, values.last + 1)
                val colour = if (colours.isEmpty()) null else colours[random.nextInt(colours.size)]
                die.copy(value = value, colour = colour)
            }
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
     * Superuser-mode-only: advances a single held die to the next face, ignoring the normal
     * "already rolled this turn" / "rolls remaining" rules that [rollDice] enforces. Held state,
     * every other die, and rolls-remaining are all left untouched - this only ever changes the one
     * die. Deterministic (not random) so holding down cycles through every face in a predictable
     * order: the numbers in turn (1 to 6), and - in a mode with coloured dice - on to the next
     * colour's 1 after its 6 (red 1..6, yellow 1..6, blue 1..6, back to red 1).
     */
    fun cycleDieValue(state: GameState, dieIndex: Int): GameState {
        val newDice = state.dice.mapIndexed { index, die ->
            if (index == dieIndex) nextFace(die, state.gameMode) else die
        }
        return state.copy(dice = newDice)
    }

    private fun nextFace(die: Die, gameMode: GameMode): Die {
        val values = gameMode.dieValues
        if (die.value < values.last) return die.copy(value = die.value + 1)

        val colours = gameMode.dieColours
        val nextColour = if (colours.isEmpty()) null else colours[(colours.indexOf(die.colour) + 1) % colours.size]
        return die.copy(value = values.first, colour = nextColour)
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
            lastRoll = state.dice,
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
            dice = List(state.gameMode.diceCount) { Die() },
            rollsRemaining = state.gameMode.rollsPerTurn,
            phase = TurnPhase.AWAITING_ROLL,
        )
    }
}
