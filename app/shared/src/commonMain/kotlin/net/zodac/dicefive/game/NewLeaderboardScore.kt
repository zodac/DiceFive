package net.zodac.dicefive.game

import net.zodac.dicefive.model.GameState
import net.zodac.dicefive.model.PlayerType

/**
 * The seats whose running total is a score the Leaderboard doesn't hold yet, for the game screen to mark: a human's
 * [net.zodac.dicefive.model.PlayerState.totalScore] in a game that will go on the Leaderboard
 * ([GameState.countsOnLeaderboard]), above 0 (nothing is scored yet at the start), and not among [onLeaderboard].
 * Nothing is marked while [onLeaderboard] is unknown (null), or once the game is over.
 */
fun newLeaderboardScoreSeats(state: GameState, onLeaderboard: Set<Int>?): Set<Int> {
    if (onLeaderboard == null || state.isGameOver || !state.countsOnLeaderboard) return emptySet()
    return state.players.indices.filterTo(HashSet()) { seat ->
        val player = state.players[seat]
        player.type == PlayerType.HUMAN && player.totalScore > 0 && player.totalScore !in onLeaderboard
    }
}
