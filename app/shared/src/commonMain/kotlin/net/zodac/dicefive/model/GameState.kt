package net.zodac.dicefive.model

/**
 * Live state for an in-progress (or just-finished) game. Built via
 * `GameEngine.newGame` and evolved by `GameEngine`'s roll/hold/score
 * reducers; see `net.zodac.dicefive.game`.
 */
data class GameState(
    val gameMode: GameMode = GameMode.default,
    val turnTimer: TurnTimer = TurnTimer.NONE,
    val players: List<PlayerState> = emptyList(),
    val currentPlayerIndex: Int = 0,
    val dice: List<Die> = List(gameMode.diceCount) { Die() },
    val rollsRemaining: Int = gameMode.rollsPerTurn,
    val phase: TurnPhase = TurnPhase.AWAITING_ROLL,
    val isGameOver: Boolean = false,
    /** Seconds that were left on the turn timer when the game was saved mid-turn, so resuming picks
     * the countdown up where it stopped; null - the full [turnSeconds] - everywhere else. Only ever
     * set on the saved copy, never on the live game. */
    val turnSecondsLeft: Int? = null,
) {

    val currentPlayer: PlayerState?
        get() = players.getOrNull(currentPlayerIndex)

    /** The highest total score among every player, including AI - what "winning" is measured against. */
    val topScore: Int
        get() = players.maxOf { it.totalScore }

    /** How long each turn is allowed, in seconds, or null for no limit: the mode's own fixed timer
     * ([GameMode.turnTimerSeconds]) if it has one, otherwise the [turnTimer] picked at setup. */
    val turnSeconds: Int?
        get() = gameMode.turnTimerSeconds ?: turnTimer.seconds

    /** A human's turn, not yet rolled, in a mode that taps the cup for them at the start of it
     * ([GameMode.autoRollAtTurnStart]). An AI's turn is never this - its own turn loop rolls. */
    val awaitsAutoRoll: Boolean
        get() = gameMode.autoRollAtTurnStart &&
            !isGameOver &&
            currentPlayer?.type == PlayerType.HUMAN &&
            phase == TurnPhase.AWAITING_ROLL
}
