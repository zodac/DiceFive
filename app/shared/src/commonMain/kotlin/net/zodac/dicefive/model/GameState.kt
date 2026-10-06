package net.zodac.dicefive.model

/**
 * Live state for an in-progress (or just-finished) game. Built via
 * `GameEngine.newGame` and evolved by `GameEngine`'s roll/hold/score
 * reducers; see `net.zodac.dicefive.game`.
 */
data class GameState(
    val gameMode: GameMode = GameMode.default,
    val turnTimer: TurnTimer = TurnTimer.NONE,
    val rollModifiers: RollModifiers = RollModifiers(),
    /** Whether the Extended Scores modifier is on: Two Pair, Evens and Odds join every player's card. */
    val extendedScores: Boolean = false,
    val players: List<PlayerState> = emptyList(),
    val currentPlayerIndex: Int = 0,
    val dice: List<Die> = List(gameMode.diceCount) { Die() },
    val rollsRemaining: Int = rollModifiers.rollsPerTurn ?: gameMode.rollsPerTurn,
    /** How many rolls this turn started with: the turn's allowance plus any stored rolls the
     * player brought in. What "the first roll" and "the last roll" of the turn are measured from. */
    val turnRolls: Int = rollsRemaining,
    val phase: TurnPhase = TurnPhase.AWAITING_ROLL,
    val isGameOver: Boolean = false,
    /** Seconds that were left on the turn timer when the game was saved mid-turn, so resuming picks
     * the countdown up where it stopped; null - the full [turnSeconds] - everywhere else. Only ever
     * set on the saved copy, never on the live game. */
    val turnSecondsLeft: Int? = null,
) {

    /** Rolls a turn allows before any stored rolls: the Number of Rolls modifier's, else the mode's. */
    val rollsPerTurn: Int
        get() = rollModifiers.rollsPerTurn ?: gameMode.rollsPerTurn

    /** The boxes on this game's cards - the mode's own, plus the Extended Scores modifier's. */
    val categories: List<ScoreCategory>
        get() = gameMode.categoriesWith(extendedScores)

    val currentPlayer: PlayerState?
        get() = players.getOrNull(currentPlayerIndex)

    /**
     * The dice this turn would be scored with: every die, or - in a mode where only held dice score
     * ([GameMode.scoresHeldDiceOnly]) - just the held ones, in hold-slot order. What the board
     * previews and what a committed score is worked out from.
     */
    val scoringDice: List<Die>
        get() = if (gameMode.scoresHeldDiceOnly) dice.filter { it.isHeld }.sortedBy { it.heldSlot } else dice

    /** Whether [scoringDice] is a whole hand - [GameMode.scoringDiceCount] dice - so the turn can be scored. */
    val hasFullHand: Boolean
        get() = scoringDice.size == gameMode.scoringDiceCount

    /** The highest total score among every player, including AI - what "winning" is measured against. */
    val topScore: Int
        get() = players.maxOf { it.totalScore }

    /** How long each turn is allowed, in seconds, or null for no limit: the mode's own fixed timer
     * ([GameMode.turnTimerSeconds]) if it has one, otherwise the [turnTimer] picked at setup. */
    val turnSeconds: Int?
        get() = gameMode.turnTimerSeconds ?: turnTimer.seconds

    /** Whether this game's scores go on the Leaderboard: its mode must allow it ([GameMode.countsOnLeaderboard])
     * and no modifier, like the [turnTimer], [rollModifiers] or [extendedScores], may be on - modifiers are for fun, not for the records. */
    val countsOnLeaderboard: Boolean
        get() = gameMode.countsOnLeaderboard && turnTimer == TurnTimer.NONE && !rollModifiers.isActive && !extendedScores

    /** A human's turn, not yet rolled, in a mode that taps the cup for them at the start of it
     * ([GameMode.autoRollAtTurnStart]). An AI's turn is never this - its own turn loop rolls. */
    val awaitsAutoRoll: Boolean
        get() = gameMode.autoRollAtTurnStart &&
            !isGameOver &&
            currentPlayer?.type == PlayerType.HUMAN &&
            phase == TurnPhase.AWAITING_ROLL
}
