package net.zodac.dicefive.model

/**
 * Live state for an in-progress (or just-finished) game. Built via
 * `GameEngine.newGame` and evolved by `GameEngine`'s roll/hold/score
 * reducers; see `net.zodac.dicefive.game`.
 */
data class GameState(
    val gameType: GameType = GameType.CLASSIC,
    val players: List<PlayerState> = emptyList(),
    val currentPlayerIndex: Int = 0,
    val dice: List<Die> = List(5) { Die() },
    val rollsRemaining: Int = 3,
    val phase: TurnPhase = TurnPhase.AWAITING_ROLL,
    val isGameOver: Boolean = false,
) {

    val currentPlayer: PlayerState?
        get() = players.getOrNull(currentPlayerIndex)
}
