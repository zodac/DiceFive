package net.zodac.dicefive.data.scores

import net.zodac.dicefive.game.TieBreakStats
import net.zodac.dicefive.game.nowEpochMillis
import net.zodac.dicefive.model.GameMode

/** [ScoreRepository.recordGame] for a game with one human player. */
suspend fun ScoreRepository.recordScore(
    playerName: String,
    stats: TieBreakStats,
    won: Boolean?,
    isPrimaryPlayer: Boolean,
    gameMode: GameMode = GameMode.STANDARD,
    playedUnmodified: Boolean = true,
    timestampEpochMillis: Long = nowEpochMillis(),
) = recordGame(
    listOf(PlayerResult(playerName, stats, won, isPrimaryPlayer)),
    gameMode = gameMode,
    playedUnmodified = playedUnmodified,
    timestampEpochMillis = timestampEpochMillis,
)
