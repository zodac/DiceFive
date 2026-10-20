package net.zodac.dicefive.game

import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.model.Die
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.GameState
import net.zodac.dicefive.model.PlayerState
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.model.TurnPhase
import net.zodac.dicefive.model.heldIndices

/**
 * What player 1 - "You", the only seat that earns achievements (see [AchievementEngine]) - did through a game that
 * the finished scorecard can't show afterwards: how each turn's rolls and holds played out, and the running counts
 * the end of the game is judged on ([endOfGameContext]). Each event returns the achievements it earned, for the
 * caller to persist and announce.
 *
 * The caller reports only player 1's own turns, except where an event says otherwise. A resumed game starts its
 * counts from zero ([startGame]): the dice rolled before the app was closed are simply not counted. Only undo carries
 * over, saved with the game: a box an undo took back ([undoneCategory]), and the box of a score Undo can still take back.
 */
class AchievementTracker {

    // Per game.
    private var diceRolledByPlayerOne = 0
    private var trailedIntoFinalRound = false
    private var ledIntoFinalRound = false

    /** Whether player 1 has used a 2nd or 3rd roll on at least one of their own turns - see [Achievement.IMPATIENT]/[Achievement.NATURALLY_GIFTED]. */
    private var playerOneTookExtraRoll = false

    /** How many of player 1's turns the turn timer has run out on. A timed-out score can't be undone, so this never counts down. */
    private var playerOneTimeouts = 0

    /** The box of each zero the turn timer scored for player 1, once per zero - see [GameAchievementContext.playerOneTimedOutZeroes]. */
    private val playerOneTimedOutZeroes = mutableListOf<ScoreCategory>()

    /** Whether player 1's most recently completed turn scored a genuine 5x - see [Achievement.TWICE_IN_A_LIFETIME]. */
    private var playerOnePreviousTurnWasFiveOfAKind = false

    /** The number of each 5x player 1 has scored this game, once per 5x - see [GameAchievementContext.fiveOfAKindNumbers]. */
    private val fiveOfAKindNumbers = mutableListOf<Int>()

    /** The number of the 5x [onCommit] is about to score, until [onCommitted] confirms the commit. */
    private var committingFiveOfAKindNumber: Int? = null

    /** The number of the 5x the last commit scored, if it was player 1's and scored one - what an undo takes back. */
    private var lastCommittedFiveOfAKindNumber: Int? = null

    private var lastCommittedCategory: ScoreCategory? = null
    private var pendingUndoneCategory: ScoreCategory? = null

    // Per turn: reset at the start of every one of player 1's own turns (its first roll).

    /** The die indices that made up a first-roll four of a kind this turn - null if roll 1 wasn't one. See [onRoll]'s Almost Famous. */
    private var fourOfAKindIndicesFromFirstRoll: Set<Int>? = null

    /** Whether [fourOfAKindIndicesFromFirstRoll] has been exactly the held set going into every roll since. Can only be knocked false. */
    private var heldFourOfAKindThroughTurn = false
    private var fiveOfAKindSeenThisTurn = false
    private var facesAfterFirstRoll: List<Die>? = null
    private var loadedDiceHeldIndices: Set<Int>? = null
    private var loadedDiceMatchedSecondRoll = false
    private var heldIndicesBeforeSecondRoll: Set<Int>? = null

    /** Die indices held going into both the 2nd and 3rd roll - see [Achievement.TIME_TO_LET_IT_GO] in [onCommit]. */
    private var heldThroughBothRerolls: Set<Int> = emptySet()
    private var everHeldAllFiveThisTurn = false
    private val holdUnholdCyclesByDieIndex = mutableMapOf<Int, Int>()
    private var hadScoringOptionAfterSecondRoll = false

    /** The value, and die indices, of an exact matching group currently or most recently held - see [onHold]. */
    private var pendingCommitmentGroupValue: Int? = null
    private var pendingCommitmentGroupIndices: Set<Int> = emptySet()

    /** Whether the tracked group passed through a full 5x since it was last fresh - a real 5x is not indecision. */
    private var commitmentGroupTainted = false

    /** The value of the exact matching group most recently held-then-fully-released this turn. */
    private var lastReleasedCommitmentGroupValue: Int? = null
    private var outOfRollsCupTaps = 0

    /** The box an undo took back that no score has followed yet - saved with the game, for [startGame] on resuming it. */
    val undoneCategory: ScoreCategory?
        get() = pendingUndoneCategory

    /** The zeroes the turn timer has scored for player 1 so far, for the achievements judged as the game goes. */
    val timedOutZeroes: List<ScoreCategory>
        get() = playerOneTimedOutZeroes.toList()

    /**
     * Forgets everything, for a game starting or resuming - except, for a resumed game, the box an undo in it took back
     * ([resumedUndo], from the saved game - see [undoneCategory]) and the box of the score its Undo can still take back
     * ([resumedLastCommitted]): it's the same game, so I Didn't Mean That still counts.
     */
    fun startGame(resumedUndo: ScoreCategory? = null, resumedLastCommitted: ScoreCategory? = null) {
        forgetUndo()
        pendingUndoneCategory = resumedUndo
        lastCommittedCategory = resumedLastCommitted
        diceRolledByPlayerOne = 0
        trailedIntoFinalRound = false
        ledIntoFinalRound = false
        playerOneTookExtraRoll = false
        playerOneTimeouts = 0
        playerOneTimedOutZeroes.clear()
        playerOnePreviousTurnWasFiveOfAKind = false
        fiveOfAKindNumbers.clear()
        committingFiveOfAKindNumber = null
        outOfRollsCupTaps = 0
        resetTurn()
    }

    private fun resetTurn() {
        fourOfAKindIndicesFromFirstRoll = null
        heldFourOfAKindThroughTurn = false
        fiveOfAKindSeenThisTurn = false
        facesAfterFirstRoll = null
        loadedDiceHeldIndices = null
        loadedDiceMatchedSecondRoll = false
        heldIndicesBeforeSecondRoll = null
        heldThroughBothRerolls = emptySet()
        everHeldAllFiveThisTurn = false
        holdUnholdCyclesByDieIndex.clear()
        hadScoringOptionAfterSecondRoll = false
        pendingCommitmentGroupValue = null
        pendingCommitmentGroupIndices = emptySet()
        commitmentGroupTainted = false
        lastReleasedCommitmentGroupValue = null
        outOfRollsCupTaps = 0
    }

    /** Every published state, whoever's turn: notes whether player 1 went into their last turn ahead or behind. */
    fun onState(state: GameState) {
        if (trailedIntoFinalRound && ledIntoFinalRound) return
        if (state.isGameOver || state.phase != TurnPhase.AWAITING_ROLL) return
        if (state.currentPlayerIndex != 0) return
        val player = state.currentPlayer ?: return
        if (player.type != PlayerType.HUMAN) return
        if (player.turnsLeft != 1) return

        val bestOther = state.players
            .filterIndexed { index, _ -> index != state.currentPlayerIndex }
            .maxOfOrNull { it.totalScore } ?: return
        if (player.totalScore < bestOther) trailedIntoFinalRound = true
        if (player.totalScore > bestOther) ledIntoFinalRound = true
    }

    /** Player 1 rolled [before] into [rolled]. */
    fun onRoll(before: GameState, rolled: GameState): Set<Achievement> = buildSet {
        if (before.dice.all { it.isHeld }) add(Achievement.WHAT_WAS_THE_POINT_OF_THAT)
        if (before.rollsRemaining == before.fullRolls) resetTurn()
        // Rainy Day Fund: a turn started with this many rolls, Stored Rolls having brought in what its own allowance didn't.
        if (before.rollsRemaining == before.fullRolls && before.rollModifiers.storedRolls && before.turnRolls >= RAINY_DAY_FUND_ROLLS) {
            add(Achievement.RAINY_DAY_FUND)
        }
        // Bad Omen: every die on the table landed in Unlucky Dice's chains.
        if (rolled.dice.all { it.isUnlucky }) add(Achievement.BAD_OMEN)
        if (before.rollsRemaining < before.fullRolls) playerOneTookExtraRoll = true

        // Decisions, Decisions: all three hold/unhold cycles on a die have to land in the same gap between rolls.
        holdUnholdCyclesByDieIndex.clear()

        // Loaded Dice: the held set right before the 2nd roll, and whether it's still the held set right before the 3rd -
        // a proper, non-empty, non-full subset only, both times.
        if (before.rollsRemaining == before.rollsRemainingAfterFirst) {
            val held = before.dice.heldIndices()
            heldIndicesBeforeSecondRoll = held
            loadedDiceHeldIndices = held.takeIf { it.isNotEmpty() && it.size < before.dice.size }
        } else if (before.rollsRemaining == before.rollsRemainingAfterSecond) {
            val heldNow = before.dice.heldIndices()
            if (heldNow != heldIndicesBeforeSecondRoll) loadedDiceHeldIndices = null
            heldThroughBothRerolls = (heldIndicesBeforeSecondRoll ?: emptySet()).intersect(heldNow)
        }

        // Counted before the roll, while it's clear which dice moved.
        diceRolledByPlayerOne += before.dice.count { !it.isHeld }

        addAll(firstRollAchievements(rolled))
        addAll(postRollAchievements(rolled, before.rollsRemaining, before.dice))
    }

    /**
     * The feats rolled straight out of the cup on a turn's first roll: a full house or a large straight, each only
     * while that box is still a legal option.
     */
    private fun firstRollAchievements(state: GameState): Set<Achievement> {
        if (state.rollsRemaining != state.rollsRemainingAfterFirst) return emptySet()
        // Where only held dice score, a roll isn't a hand - nothing is held straight out of the cup.
        if (state.gameMode.scoresHeldDiceOnly) return emptySet()
        val player = state.currentPlayer ?: return emptySet()

        // Not a die locked by Unlucky Dice: a hand with one missing is none of these.
        val dice = state.scoringDice
        val available = ScoreCalculator.availableCategories(player, dice)
        return buildSet {
            if (ScoreCategory.FULL_HOUSE in available && DiceScoring.score(ScoreCategory.FULL_HOUSE, dice) > 0) add(Achievement.HOUSE_CALL)
            if (ScoreCategory.LARGE_STRAIGHT in available && DiceScoring.score(ScoreCategory.LARGE_STRAIGHT, dice) > 0) add(Achievement.STRAIGHT_AWAY)
        }
    }

    /**
     * Whether a 5x just rolled could be scored as one - the 5x box still open, or a genuine 50 already there and this
     * roll earning the joker bonus (see [ScoreCalculator.awardsFiveOfAKindBonus]).
     */
    private fun fiveOfAKindScorable(player: PlayerState, dice: List<Die>, available: List<ScoreCategory>): Boolean =
        ScoreCategory.FIVE_OF_A_KIND in available || ScoreCalculator.awardsFiveOfAKindBonus(player, dice)

    /**
     * Everything about a roll that can only be judged from the roll itself - [rollsRemainingBeforeRoll] is the value
     * from just before it (so `== fullRolls` means "this was roll 1"), and [diceBeforeRoll] the dice going into it.
     */
    private fun postRollAchievements(state: GameState, rollsRemainingBeforeRoll: Int, diceBeforeRoll: List<Die>): Set<Achievement> = buildSet {
        val player = state.currentPlayer ?: return@buildSet
        val dice = state.dice
        val values = dice.map { it.value }
        // Number and colour together: in a mode with coloured dice, "the same result" means both.
        val faces = dice.faces()
        // Where only held dice score, a roll isn't a hand: the feats judged on one as it lands (Natural 5x, The Dice
        // Hate Me, Almost Famous) aren't earned there. A roll with a die locked by Unlucky Dice isn't a whole hand either.
        val rollIsHand = !state.gameMode.scoresHeldDiceOnly && dice.none { it.isUnlucky }

        // Lucky Seven: every one of Stud's seven dice showing the same number - an achievement for that one mode.
        if (state.gameMode == GameMode.STUD && values.toSet().size == 1) add(Achievement.LUCKY_SEVEN)

        // I Can Count!: 1,2,3,4,5 in that order, first roll of the turn only.
        if (rollsRemainingBeforeRoll == state.fullRolls && values == COUNTING_DICE_VALUES) add(Achievement.I_CAN_COUNT)
        // Product Placement: the menu logo's own dice, in its own order - any roll of any turn.
        if (values == LOGO_DICE_VALUES) add(Achievement.PRODUCT_PLACEMENT)

        // Natural 5x: landed without holding anything for this roll, not on the first one, and scorable as a 5x.
        if (rollIsHand &&
            rollsRemainingBeforeRoll < state.fullRolls &&
            diceBeforeRoll.none { it.isHeld } &&
            DiceScoring.isFiveOfAKind(dice) &&
            fiveOfAKindScorable(player, dice, ScoreCalculator.availableCategories(player, dice))
        ) {
            add(Achievement.NATURAL_5X)
        }

        // Almost Famous: a first-roll four of a kind's own die indices, whether they're still exactly what's held going
        // into every roll since, and whether a real 5x ever turns up anyway.
        if (rollsRemainingBeforeRoll == state.fullRolls) {
            val fourOfAKindValue = values.groupingBy { it }.eachCount().entries.firstOrNull { it.value == FOUR_OF_A_KIND_COUNT }?.key
            fourOfAKindIndicesFromFirstRoll = fourOfAKindValue?.takeIf { rollIsHand }?.let { value ->
                values.withIndex().filter { it.value == value }.map { it.index }.toSet()
            }
            heldFourOfAKindThroughTurn = fourOfAKindIndicesFromFirstRoll != null
            facesAfterFirstRoll = faces
        } else {
            fourOfAKindIndicesFromFirstRoll?.let { required ->
                if (diceBeforeRoll.heldIndices() != required) heldFourOfAKindThroughTurn = false
            }
        }
        if (DiceScoring.isFiveOfAKind(dice)) fiveOfAKindSeenThisTurn = true

        // The Dice Hate Me: a real (non-zero) scoring option existed after roll 2, but none at all after roll 3.
        if (!rollIsHand) {
            hadScoringOptionAfterSecondRoll = false
        } else if (rollsRemainingBeforeRoll == state.rollsRemainingAfterFirst) {
            hadScoringOptionAfterSecondRoll = hasScoringOption(player, dice)
        } else if (rollsRemainingBeforeRoll == state.rollsRemainingAfterSecond) {
            if (hadScoringOptionAfterSecondRoll && !hasScoringOption(player, dice)) add(Achievement.THE_DICE_HATE_ME)
        }

        // Are These Loaded Dice?: the dice not held since roll 1 keep landing on exactly the same faces, roll after roll.
        val firstRollFaces = facesAfterFirstRoll
        val heldIndices = loadedDiceHeldIndices
        if (firstRollFaces != null && heldIndices != null && rollsRemainingBeforeRoll < state.fullRolls) {
            val unheldStillMatches = faces.indices.filter { it !in heldIndices }.all { faces[it] == firstRollFaces[it] }
            when {
                !unheldStillMatches -> loadedDiceHeldIndices = null
                rollsRemainingBeforeRoll == state.rollsRemainingAfterFirst -> loadedDiceMatchedSecondRoll = true
                rollsRemainingBeforeRoll == state.rollsRemainingAfterSecond && loadedDiceMatchedSecondRoll -> add(Achievement.DEJA_VU)
            }
        }
    }

    private fun hasScoringOption(player: PlayerState, dice: List<Die>): Boolean =
        ScoreCalculator.availableCategories(player, dice).any { ScoreCalculator.scoreFor(player, it, dice) > 0 }

    /**
     * Player 1 held or released [dieIndex] - [wasHeld] is its state just before - leaving [state].
     *
     * Commitment Issues' state machine lives here: [pendingCommitmentGroupValue] is the value of an exact matching
     * group - 1 to 4 dice, all one value, nothing else held (5 is a 5x, not indecision) - currently or most recently
     * held, tracked with the dice that make it up so releasing that same group one tap at a time doesn't lose it. Only
     * holding something that isn't a subset of the group being released breaks the chain early. Once the held set
     * reaches empty having been released from a real group, that value becomes [lastReleasedCommitmentGroupValue], the
     * baseline the next group has to differ from. [onCommit] awards it, on the matching upper box.
     */
    fun onHold(dieIndex: Int, wasHeld: Boolean, state: GameState): Set<Achievement> = buildSet {
        val dice = state.dice
        val heldIndices = dice.heldIndices()

        if (wasHeld) {
            val cycles = (holdUnholdCyclesByDieIndex[dieIndex] ?: 0) + 1
            holdUnholdCyclesByDieIndex[dieIndex] = cycles
            if (cycles >= HOLD_UNHOLD_CYCLE_TARGET) add(Achievement.DECISIONS_DECISIONS)
        }

        // A Cunning Strategy: reached all-five-held at some point this turn, then all-none-held afterward. "All five" is
        // every hold slot - every die, unless the mode rolls more dice than it scores.
        val fullHand = state.gameMode.scoringDiceCount
        when {
            heldIndices.size == fullHand -> everHeldAllFiveThisTurn = true
            heldIndices.isEmpty() && everHeldAllFiveThisTurn -> add(Achievement.A_CUNNING_STRATEGY)
        }

        val heldValues = heldIndices.map { dice[it].value }
        val allOneValue = heldValues.isNotEmpty() && heldValues.toSet().size == 1
        when {
            heldIndices.isEmpty() -> {
                // Only credited if it never passed through a full 5x on the way here.
                if (pendingCommitmentGroupValue != null && !commitmentGroupTainted) {
                    lastReleasedCommitmentGroupValue = pendingCommitmentGroupValue
                }
                pendingCommitmentGroupValue = null
                pendingCommitmentGroupIndices = emptySet()
                commitmentGroupTainted = false
            }
            allOneValue -> {
                // Building up, or shrinking back down, within the same tracked group keeps its taint; anything else
                // starts fresh.
                val continuingSameGroup = pendingCommitmentGroupIndices.isNotEmpty() && heldIndices.all { it in pendingCommitmentGroupIndices }
                if (!continuingSameGroup) {
                    pendingCommitmentGroupValue = heldValues.first()
                    commitmentGroupTainted = false
                }
                pendingCommitmentGroupIndices = heldIndices
                if (heldIndices.size == fullHand) commitmentGroupTainted = true
            }
            else -> {
                pendingCommitmentGroupValue = null
                pendingCommitmentGroupIndices = emptySet()
                commitmentGroupTainted = false
            }
        }
    }

    /**
     * Player 1 is about to score [category] from [state], before `GameEngine.commitScore` changes anything: a genuine
     * 5x about to be scored as a zero, and everything else judged at the moment of commit.
     */
    fun onCommit(state: GameState, category: ScoreCategory): Set<Achievement> = buildSet {
        val player = state.currentPlayer ?: return@buildSet
        if (player.type != PlayerType.HUMAN) return@buildSet
        // The hand being scored - every die, or where only held dice score, the held ones.
        val dice = state.scoringDice

        // Wasted Fortune: a 5x scored as a zero, deliberately or by the joker rule's forced-zero fallback.
        // ScoreCalculator.scoreFor is joker-aware, so it covers both.
        if (DiceScoring.isFiveOfAKind(dice) && ScoreCalculator.scoreFor(player, category, dice) == 0) add(Achievement.WASTED_FORTUNE)

        // Size Isn't Everything: the small straight scored while the large straight sat right there, open and legal.
        if (category == ScoreCategory.SMALL_STRAIGHT &&
            ScoreCategory.LARGE_STRAIGHT in ScoreCalculator.availableCategories(player, dice) &&
            DiceScoring.score(ScoreCategory.LARGE_STRAIGHT, dice) > 0
        ) {
            add(Achievement.SIZE_ISNT_EVERYTHING)
        }

        // Almost Famous: a first-roll four of a kind held as-is through every roll since, never turning into a real 5x,
        // with every roll of the turn spent - and more than one to spend.
        if (state.fullRolls > 1 &&
            fourOfAKindIndicesFromFirstRoll != null &&
            heldFourOfAKindThroughTurn &&
            !fiveOfAKindSeenThisTurn &&
            state.rollsRemaining == 0
        ) {
            add(Achievement.ALMOST_FAMOUS)
        }

        // Probability? Never Heard of Her: the last box on the card is an upper one that one die would have turned into the
        // bonus, and every roll of the turn - there has to be more than one - still left it a zero.
        if (state.fullRolls > 1 &&
            state.rollsRemaining == 0 &&
            state.unluckyDice == null &&
            player.turnsLeft == 1 &&
            player.isOpen(category) &&
            player.needsOneDieForBonus(category) &&
            ScoreCalculator.scoreFor(player, category, dice) == 0
        ) {
            add(Achievement.PROBABILITY_NEVER_HEARD_OF_HER)
        }

        // Empty House and Fuller House: the worst and best full houses there are, which a 5x-as-joker can never produce.
        if (category == ScoreCategory.FULL_HOUSE && dice.map { it.value }.sorted() == EMPTY_HOUSE_VALUES) add(Achievement.EMPTY_HOUSE)
        if (category == ScoreCategory.FULL_HOUSE && dice.map { it.value }.sorted() == FULLER_HOUSE_VALUES) add(Achievement.FULLER_HOUSE)

        // Commitment Issues: committing the upper box that matches the second, different exact group.
        val committingValue = pendingCommitmentGroupValue
        if (lastReleasedCommitmentGroupValue != null &&
            committingValue != null &&
            committingValue != lastReleasedCommitmentGroupValue &&
            category == PlayerState.UPPER_CATEGORIES.getOrNull(committingValue - 1)
        ) {
            add(Achievement.COMMITMENT_ISSUES)
        }

        // Time To Let It Go: a die held after both the 1st and 2nd rolls whose value the committed upper box never
        // counted. A zero means the box failed outright rather than the die being let go, so it doesn't count.
        if (category in PlayerState.UPPER_CATEGORIES && DiceScoring.score(category, dice) > 0) {
            val target = PlayerState.UPPER_CATEGORIES.indexOf(category) + 1
            if (heldThroughBothRerolls.any { state.dice[it].value != target }) add(Achievement.TIME_TO_LET_IT_GO)
        }

        // Twice in a Lifetime: this turn scores a 5x, and so did player 1's last one - the SCORED_5X counter's own
        // definition of "scored a 5x" (the box, or a bonus chip).
        val scoresFiveOfAKindNow = (category == ScoreCategory.FIVE_OF_A_KIND && DiceScoring.isFiveOfAKind(dice)) ||
            ScoreCalculator.awardsFiveOfAKindBonus(player, dice)
        // Not where the best five of seven dice score (Stud): a 5x is no feat there.
        if (!state.gameMode.scoresHeldDiceOnly && playerOnePreviousTurnWasFiveOfAKind && scoresFiveOfAKindNow) {
            add(Achievement.TWICE_IN_A_LIFETIME)
        }
        playerOnePreviousTurnWasFiveOfAKind = scoresFiveOfAKindNow
        // Full Set: the same "scored a 5x", and not Stud's either - noted once the commit goes through (onCommitted).
        committingFiveOfAKindNumber = dice.first().value.takeIf { !state.gameMode.scoresHeldDiceOnly && scoresFiveOfAKindNow }
    }

    /**
     * Any human seat's [category] has just been scored ([byPlayerOne] if it was player 1's): I Didn't Mean That, for
     * player 1 scoring a different box from the one an undo just took back - in the same game only (see [onGameOver]).
     */
    fun onCommitted(category: ScoreCategory, byPlayerOne: Boolean): Set<Achievement> {
        val undone = pendingUndoneCategory
        pendingUndoneCategory = null
        lastCommittedCategory = category
        lastCommittedFiveOfAKindNumber = committingFiveOfAKindNumber.takeIf { byPlayerOne }
        committingFiveOfAKindNumber = null
        lastCommittedFiveOfAKindNumber?.let { fiveOfAKindNumbers += it }
        return if (byPlayerOne && undone != null && category != undone) setOf(Achievement.I_DIDNT_MEAN_THAT) else emptySet()
    }

    /** The last score was undone. */
    fun onUndo() {
        pendingUndoneCategory = lastCommittedCategory
        lastCommittedFiveOfAKindNumber?.let { fiveOfAKindNumbers.remove(it) }
        lastCommittedFiveOfAKindNumber = null
    }

    /** The game has ended: an undo in it can't lead to I Didn't Mean That in the next one. */
    fun onGameOver() {
        forgetUndo()
    }

    private fun forgetUndo() {
        lastCommittedCategory = null
        pendingUndoneCategory = null
    }

    /** Player 1 tapped the cup with no rolls left - see [Achievement.NO_MORE_ROLLS]. */
    fun onCupTapWithNoRollsLeft(): Set<Achievement> {
        outOfRollsCupTaps++
        return if (outOfRollsCupTaps >= NO_MORE_ROLLS_TAP_TARGET) setOf(Achievement.NO_MORE_ROLLS) else emptySet()
    }

    /** The turn timer ran out on player 1 and scored [scored]'s last box for them. */
    fun onTimeout(scored: GameState) {
        playerOneTimeouts++
        val playerOne = scored.players.first()
        val category = requireNotNull(playerOne.lastScoredCategory)
        if (playerOne.scoresIn(category).last() == 0) playerOneTimedOutZeroes += category
    }

    /** What the end of the game is judged on besides the final state, with the board as it was before this game. */
    fun endOfGameContext(previousBestScore: Int?, previousLeaderboard: LeaderboardTotals): GameAchievementContext = GameAchievementContext(
        previousBestScore = previousBestScore,
        previousLeaderboard = previousLeaderboard,
        trailedIntoFinalRound = trailedIntoFinalRound,
        ledIntoFinalRound = ledIntoFinalRound,
        diceRolledByPlayerOne = diceRolledByPlayerOne,
        playerOneTookExtraRoll = playerOneTookExtraRoll,
        playerOneTimeouts = playerOneTimeouts,
        playerOneTimedOutZeroes = playerOneTimedOutZeroes.toList(),
        fiveOfAKindNumbers = fiveOfAKindNumbers.toSet(),
    )

    private companion object {
        /** What `rollsRemaining` reads before any roll has happened this turn - the turn's full allowance, stored rolls included. */
        val GameState.fullRolls: Int
            get() = turnRolls

        /** What `rollsRemaining` reads once the first of a turn's rolls has been used. */
        val GameState.rollsRemainingAfterFirst: Int
            get() = turnRolls - 1

        /** What `rollsRemaining` reads once the second of a turn's rolls has been used. */
        val GameState.rollsRemainingAfterSecond: Int
            get() = turnRolls - 2

        /** Each die's face - number and colour - without whether it's held, for comparing one roll to another. */
        fun List<Die>.faces(): List<Die> = map { Die(value = it.value, colour = it.colour) }

        const val FOUR_OF_A_KIND_COUNT = 4
        const val HOLD_UNHOLD_CYCLE_TARGET = 3
        const val NO_MORE_ROLLS_TAP_TARGET = 3

        /** The rolls a turn has to start with for Rainy Day Fund. */
        const val RAINY_DAY_FUND_ROLLS = 100

        /** The exact roll "I Can Count!" is named for. */
        val COUNTING_DICE_VALUES = listOf(1, 2, 3, 4, 5)

        /** The menu logo's own dice (`AppLogo`), in its own order - "Product Placement". */
        val LOGO_DICE_VALUES = listOf(2, 4, 5, 3, 6)

        /** Three 1s and two 2s, sorted - the exact roll "Empty House" is named for. */
        val EMPTY_HOUSE_VALUES = listOf(1, 1, 1, 2, 2)

        /** Three 6s and two 5s, sorted - the exact roll "Fuller House" is named for. */
        val FULLER_HOUSE_VALUES = listOf(5, 5, 6, 6, 6)
    }
}
