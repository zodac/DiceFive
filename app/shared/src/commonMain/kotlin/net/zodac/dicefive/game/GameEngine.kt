package net.zodac.dicefive.game

import kotlin.math.abs
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
        val players = state.players.mapIndexed { index, player ->
            if (index == state.currentPlayerIndex) player.copy(rollCount = player.rollCount + 1) else player
        }
        return state.copy(
            players = players,
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
    //
    // In a mode where only held dice score (GameMode.scoresHeldDiceOnly), a die that's held goes to the
    // free hold slot nearest its own column (see placeInSlots) and keeps it until it's let go; with
    // every slot full, holding another die does nothing - see canHold.
    fun toggleHold(state: GameState, dieIndex: Int): GameState {
        check(state.phase == TurnPhase.ROLLED) { "Cannot hold dice before rolling" }
        val die = state.dice[dieIndex]
        if (!die.isHeld && !canHold(state)) return state
        val newDice = state.dice.mapIndexed { index, current ->
            if (index == dieIndex) current.copy(isHeld = !current.isHeld, heldSlot = null) else current
        }
        val placed = if (!die.isHeld && state.gameMode.scoresHeldDiceOnly) placeInSlots(newDice, state.gameMode.scoringDiceCount) else newDice
        return state.copy(dice = placed)
    }

    /** Whether another die can be held: always, unless every hold slot of a mode with fewer slots than dice is taken. */
    fun canHold(state: GameState): Boolean =
        !state.gameMode.scoresHeldDiceOnly || state.dice.count { it.isHeld } < state.gameMode.scoringDiceCount

    /**
     * Holds unheld dice, left to right, until the hand is full ([GameState.hasFullHand]) - what a turn
     * that runs out of time is scored with in a mode where only held dice score. Nothing changes in a
     * mode where every die scores.
     */
    fun fillHand(state: GameState): GameState {
        var current = state
        for (index in state.dice.indices) {
            if (current.hasFullHand) break
            if (!current.dice[index].isHeld) current = toggleHold(current, index)
        }
        return current
    }

    /**
     * Gives each held die without a slot one of [slotCount] slots: the free one nearest the slot under
     * its own column, measured from the middle of that column. A die already in a slot never moves, so
     * with seven dice and five slots die 1 goes in slot 1 if it's free, and otherwise in the leftmost
     * slot that is; die 4 prefers the middle slot, and die 7 the last. Between two free slots equally
     * near, the one that keeps the held dice in column order wins.
     */
    private fun placeInSlots(dice: List<Die>, slotCount: Int): List<Die> {
        // Where the middle of a die's column falls, measured in slots.
        fun underColumn(index: Int): Float = (index + 0.5f) * slotCount / dice.size - 0.5f

        val placed = dice.toMutableList()
        for (index in dice.indices) {
            if (!dice[index].isHeld || dice[index].heldSlot != null) continue
            val taken = placed.mapNotNullTo(HashSet()) { it.heldSlot }
            // How many dice already in a slot would be out of column order with this one in [slot].
            fun outOfOrder(slot: Int): Int = placed.indices.count { other ->
                val otherSlot = placed[other].heldSlot
                otherSlot != null && (other < index) != (otherSlot < slot)
            }
            val slot = (0 until slotCount).filter { it !in taken }
                .minWith(compareBy<Int> { abs(it - underColumn(index)) }.thenBy { outOfOrder(it) })
            placed[index] = dice[index].copy(heldSlot = slot)
        }
        return placed
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

    /** Scores [GameState.scoringDice] in [category] and moves on to the next turn. */
    fun commitScore(state: GameState, category: ScoreCategory): GameState {
        check(state.phase == TurnPhase.ROLLED) { "Cannot score before rolling" }
        check(state.hasFullHand) { "Cannot score before every hold slot is filled" }
        val player = requireNotNull(state.currentPlayer) { "No current player" }
        val hand = state.scoringDice
        check(category in ScoreCalculator.availableCategories(player, hand)) {
            "$category is not available for the current dice"
        }

        val value = ScoreCalculator.scoreFor(player, category, hand)
        val bonus = ScoreCalculator.awardsFiveOfAKindBonus(player, hand)
        val updatedPlayer = player.copy(
            scorecard = player.scorecard + (category to value),
            fiveOfAKindBonusCount = player.fiveOfAKindBonusCount + if (bonus) 1 else 0,
            lastRoll = state.dice,
            lastScoredCategory = category,
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
