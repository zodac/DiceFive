package net.zodac.dicefive.game

import kotlin.math.abs
import kotlin.random.Random
import kotlin.uuid.Uuid
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.model.Die
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.GameState
import net.zodac.dicefive.model.PlayerConfig
import net.zodac.dicefive.model.PlayerState
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.model.RollModifiers
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.model.TurnPhase
import net.zodac.dicefive.model.TurnTimer
import net.zodac.dicefive.model.UnluckyDice
import net.zodac.dicefive.model.duplicateHumanNameSlots

/**
 * Pure reducers for the turn flow. None of these touch Android APIs
 * or persistence — [net.zodac.dicefive.ui.game.GameViewModel] is the only
 * caller and owns all side effects (AI pacing, score persistence).
 */
object GameEngine {

    /** The odds, one in this many per die rolled, of a golden egg where one can turn up ([rollDice]'s goldenOneIn). */
    const val GOLDEN_EGG_ONE_IN = 500

    fun newGame(
        players: List<PlayerConfig>,
        gameMode: GameMode = GameMode.default,
        turnTimer: TurnTimer = TurnTimer.NONE,
        rollModifiers: RollModifiers = RollModifiers(),
        extendedScores: Boolean = false,
        unluckyDice: UnluckyDice? = null,
        random: Random = Random.Default,
        id: Uuid = Uuid.random(),
    ): GameState {
        require(players.size in GameState.MIN_PLAYERS..GameState.MAX_PLAYERS) { "A game seats ${GameState.MIN_PLAYERS} to ${GameState.MAX_PLAYERS} players, not ${players.size}" }
        // Seat 1 is the player holding the phone, whose achievements and records are "You". Only a table of CPUs alone (a
        // simulation; the app never makes one) has nobody there.
        require(players.none { it.type == PlayerType.HUMAN } || players.first().type == PlayerType.HUMAN) { "A Human sits in seat 1" }
        require(players.map { it.colour }.toSet().size == players.size) { "Every player's colour differs" }
        require(duplicateHumanNameSlots(players).isEmpty()) { "Every Human player's name differs" }
        val disabledCategories = gameMode.drawDisabledCategories(random)
        val hitList = gameMode.drawHitList(random)
        // A mode that doesn't allow the Extended Scores boxes plays without them, whatever was asked for.
        val extended = extendedScores && gameMode.allowsExtendedScores
        return GameState(
            id = id,
            gameMode = gameMode,
            turnTimer = turnTimer,
            rollModifiers = rollModifiers,
            extendedScores = extended,
            unluckyDice = unluckyDice,
            disabledCategories = disabledCategories,
            hitList = hitList,
            players = players.map {
                PlayerState(
                    name = it.name,
                    type = it.type,
                    difficulty = it.difficulty,
                    colour = it.colour,
                    gameMode = gameMode,
                    extendedScores = extended,
                    disabledCategories = disabledCategories,
                    hitList = hitList,
                    rollsModified = rollModifiers.isActive,
                )
            },
        )
    }

    /**
     * Rerolls every unheld die: a number from [GameMode.dieValues], then - only in a mode whose dice
     * have colours - a colour from [GameMode.dieColours], each equally likely. A colourless mode
     * never draws that second number, so its dice come out of [random] exactly as they always have.
     *
     * With the Unlucky Dice modifier on ([GameState.unluckyDice]), each die just rolled is then locked with its
     * odds, [UnluckyDice.maxDice] of them at most (chosen at random when more come up). A die that was
     * unlucky and is rolled again starts clear. Without the modifier nothing more is drawn from [random].
     *
     * With [goldenOneIn] set, each die rolled also has that one-in chance of coming up golden
     * ([Die.isGolden]) - drawn after its value and colour, and only then, so without it [random] is
     * used exactly as it always was.
     */
    fun rollDice(state: GameState, random: Random = Random.Default, goldenOneIn: Int? = null): GameState {
        check(state.rollsRemaining > 0) { "No rolls remaining this turn" }
        val values = state.gameMode.dieValues
        val colours = state.gameMode.dieColours
        val newDice = state.dice.map { die ->
            if (die.isHeld) {
                die
            } else {
                val value = random.nextInt(values.first, values.last + 1)
                val colour = if (colours.isEmpty()) null else colours[random.nextInt(colours.size)]
                die.copy(value = value, colour = colour, isUnlucky = false, isGolden = goldenOneIn != null && random.nextInt(goldenOneIn) == 0)
            }
        }.let { dice -> state.unluckyDice?.let { curse(dice, state.dice, it, random) } ?: dice }
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

    /**
     * Locks the dice that were just rolled ([rolled], each one that wasn't held in [before]) by [unlucky]'s
     * odds, no more than its cap - a die held through the roll is never touched.
     */
    private fun curse(rolled: List<Die>, before: List<Die>, unlucky: UnluckyDice, random: Random): List<Die> {
        val cursed = rolled.indices
            .filter { !before[it].isHeld && random.nextInt(PERCENT) < unlucky.oddsPercent }
            .shuffled(random)
            .take(unlucky.maxDice)
            .toSet()
        return rolled.mapIndexed { index, die -> if (index in cursed) die.copy(isUnlucky = true) else die }
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
        if (!die.isHeld && !canHold(state, dieIndex)) return state
        val newDice = state.dice.mapIndexed { index, current ->
            if (index == dieIndex) current.copy(isHeld = !current.isHeld, heldSlot = null) else current
        }
        val placed = if (!die.isHeld && state.gameMode.scoresHeldDiceOnly) placeInSlots(newDice, state.gameMode.scoringDiceCount) else newDice
        return state.copy(dice = placed)
    }

    /**
     * Whether another die can be held: always, unless every hold slot of a mode with fewer slots than dice is
     * taken - and never an unlucky die ([Die.isUnlucky]), when [dieIndex] says which one is meant.
     */
    fun canHold(state: GameState, dieIndex: Int? = null): Boolean =
        state.dice.getOrNull(dieIndex ?: -1)?.isUnlucky != true &&
            (!state.gameMode.scoresHeldDiceOnly || state.dice.count { it.isHeld } < state.gameMode.scoringDiceCount)

    /**
     * Holds unheld dice, left to right, until the hand is full ([GameState.hasFullHand]). Nothing changes in a mode where
     * every die scores.
     */
    fun fillHand(state: GameState): GameState {
        var current = state
        for (index in state.dice.indices) {
            if (current.hasFullHand) break
            if (!current.dice[index].isHeld && !current.dice[index].isUnlucky) current = toggleHold(current, index)
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

    /** Scores [GameState.scoringDice] in [category]'s next open slot and moves on to the next turn. */
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
        // A 5x scored, in the box or as a chip - not where held dice alone score (Stud), where it's no feat.
        val scoresFiveOfAKind = !state.gameMode.scoresHeldDiceOnly &&
            ((category == ScoreCategory.FIVE_OF_A_KIND && DiceScoring.isFiveOfAKind(hand)) || bonus)
        // The worst and best Full Houses there are - which a 5x as a joker never is.
        val fullHouseHand = Achievement.FULL_HOUSES_HANDS.indexOf(hand.map { it.value }.sorted()) + 1
        val updatedPlayer = player.copy(
            scorecard = player.scorecard + (category to player.scoresIn(category) + value),
            fiveOfAKindBonusCount = player.fiveOfAKindBonusCount + if (bonus) 1 else 0,
            jokerBonusSlots = if (bonus) {
                player.jokerBonusSlots + (category to player.jokerBonusSlots[category].orEmpty() + player.scoresIn(category).size)
            } else {
                player.jokerBonusSlots
            },
            lastRoll = state.dice,
            lastScoredCategory = category,
            firstTurnScore = if (player.turnsTaken == 0) value else player.firstTurnScore,
            fiveOfAKindNumbers = if (scoresFiveOfAKind) player.fiveOfAKindNumbers + hand.first().value else player.fiveOfAKindNumbers,
            fullHouseHands = if (category == ScoreCategory.FULL_HOUSE && fullHouseHand > 0) player.fullHouseHands + fullHouseHand else player.fullHouseHands,
            // Whatever is left of this turn's rolls waits for their next one (none, unless Stored Rolls is on).
            storedRolls = state.rollModifiers.stored(state.rollsRemaining),
        )
        val updatedPlayers = state.players.toMutableList().apply { this[state.currentPlayerIndex] = updatedPlayer }
        return advanceTurn(state.copy(players = updatedPlayers))
    }

    /**
     * The turn timer running out: the rest of the turn's rolls are forfeited, and a box is filled - a forced miss, never
     * the best option picked for the player. A turn not yet rolled scores a zero ([forfeitTurn]): nothing is rolled the
     * player didn't roll. A rolled one goes in the first box, in scorecard order, its dice can go in
     * ([HandCompletion.timeoutCategory]), as they lie - or, where only held dice score, with the held ones completed for
     * it - scoring nothing if they don't match. Null only if no box can take them.
     */
    fun timeOut(state: GameState): GameState? {
        if (state.phase == TurnPhase.AWAITING_ROLL) return forfeitTurn(state)
        val category = HandCompletion.timeoutCategory(state) ?: return null
        return commitScore(HandCompletion.bestCompletion(state, category) ?: return null, category)
    }

    /**
     * A turn the timer ran out on before its first roll: a zero in the first open box, in scorecard order, and on to the
     * next turn. There's no hand, so no [PlayerState.lastRoll] to show for it, and none of the turn's rolls are kept for
     * later (Stored Rolls) - letting the clock run down is never a way to bank rolls.
     */
    fun forfeitTurn(state: GameState): GameState {
        check(state.phase == TurnPhase.AWAITING_ROLL) { "Only a turn not yet rolled is forfeited" }
        val player = requireNotNull(state.currentPlayer) { "No current player" }
        val category = player.categories.first { player.isOpen(it) }
        val updatedPlayer = player.copy(
            scorecard = player.scorecard + (category to player.scoresIn(category) + 0),
            lastRoll = null,
            lastScoredCategory = category,
            storedRolls = 0,
        )
        val updatedPlayers = state.players.toMutableList().apply { this[state.currentPlayerIndex] = updatedPlayer }
        return advanceTurn(state.copy(players = updatedPlayers))
    }

    private const val PERCENT = 100

    /** A round just ended: every player now behind another player's total has been behind at the end of a round. */
    private fun noteBehind(state: GameState): GameState {
        val players = state.players.mapIndexed { index, player ->
            val bestOther = state.players.filterIndexed { other, _ -> other != index }.maxOfOrNull { it.totalScore }
            if (bestOther != null && player.totalScore < bestOther) player.copy(wasBehindAfterARound = true) else player
        }
        return state.copy(players = players)
    }

    private fun advanceTurn(turnEnded: GameState): GameState {
        val nextIndex = (turnEnded.currentPlayerIndex + 1) % turnEnded.players.size
        val state = if (nextIndex == 0) noteBehind(turnEnded) else turnEnded
        if (state.players.all { it.isScorecardComplete }) {
            return state.copy(isGameOver = true)
        }
        val turnRolls = state.rollsPerTurn + state.players[nextIndex].storedRolls
        return state.copy(
            currentPlayerIndex = nextIndex,
            dice = List(state.gameMode.diceCount) { Die() },
            rollsRemaining = turnRolls,
            turnRolls = turnRolls,
            phase = TurnPhase.AWAITING_ROLL,
        )
    }
}
