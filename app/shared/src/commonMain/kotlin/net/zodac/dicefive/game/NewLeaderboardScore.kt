package net.zodac.dicefive.game

import net.zodac.dicefive.model.GameState
import net.zodac.dicefive.model.PlayerType

/**
 * The seats whose running total is a score the game's own mode's board doesn't hold yet, for the game screen to mark: a
 * human's [net.zodac.dicefive.model.PlayerState.totalScore], above 0 (nothing is scored yet at the start), no lower than
 * the mode's [net.zodac.dicefive.model.GameMode.minPossibleScore] (Standard's 1 to 4 end no game), and not among
 * [onLeaderboard] - that board's scores. Every mode has a board, so any mode's game is marked; one with a modifier on
 * ([GameState.hasModifiers]) is on no board, so isn't. Nothing is marked while [onLeaderboard] is unknown (null), or once
 * the game is over.
 */
fun newLeaderboardScoreSeats(state: GameState, onLeaderboard: Set<Int>?): Set<Int> {
    if (onLeaderboard == null || state.isGameOver || state.hasModifiers) return emptySet()
    return state.players.indices.filterTo(HashSet()) { seat ->
        val player = state.players[seat]
        player.type == PlayerType.HUMAN && player.totalScore > 0 && player.totalScore >= state.gameMode.minPossibleScore &&
            player.totalScore !in onLeaderboard
    }
}
