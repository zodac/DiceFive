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
) {

    val currentPlayer: PlayerState?
        get() = players.getOrNull(currentPlayerIndex)

    /** The highest total score among every player, including AI - what "winning" is measured against. */
    val topScore: Int
        get() = players.maxOf { it.totalScore }
}
