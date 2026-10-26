package net.zodac.dicefive.data.scores

import net.zodac.dicefive.model.GameMode

/**
 * One player's aggregate stats across every game recorded for them - the Statistics screen's row
 * shape. "Player" here means a distinct `playerName`, not a stable identity: renaming a player
 * starts a new row, the same way it starts a new leaderboard entry.
 */
data class PlayerStatistics(
    val playerName: String,
    val firstPlayedEpochMillis: Long,
    val gamesPlayed: Int,
    val gamesWon: Int,
    val gamesLost: Int,
    val currentWinStreak: Int,
    val bestWinStreak: Int,
    val maxScore: Int,
    /** Every point scored across every recorded game, solo or multiplayer. */
    val totalScore: Int,
    /** How many 5x this player has scored, summed over every recorded game. */
    val fiveOfAKindCount: Int,
    /** Games with nobody to beat - the ones recorded with a null outcome, so counted in [gamesPlayed] only. */
    val soloGames: Int,
    /** The same player's games split by mode, in [GameMode.entries] order; a mode never played is absent. */
    val modes: List<ModeStatistics> = emptyList(),
) {

    /** [totalScore] over [gamesPlayed], rounded half up to a whole number; 0 for a player with no games. */
    val averageScore: Int
        get() = if (gamesPlayed == 0) 0 else ((totalScore.toLong() * 2 + gamesPlayed) / (gamesPlayed * 2L)).toInt()

    /** See [winRatePercent]. */
    val winRatePercent: Int?
        get() = winRatePercent(gamesWon, gamesLost)
}

/** One player's games in one [gameMode]: the Statistics card's By mode row. */
data class ModeStatistics(
    val gameMode: GameMode,
    /** Every game in the mode, solo ones included. */
    val gamesPlayed: Int,
    val gamesWon: Int,
    val gamesLost: Int,
    val soloGames: Int,
    val maxScore: Int,
) {

    /** See [winRatePercent]. */
    val winRatePercent: Int?
        get() = winRatePercent(gamesWon, gamesLost)
}

/**
 * The share of the games with someone to beat that were won, as a whole percentage rounded half up. Solo games aren't
 * in it. Null when every game was solo, so there's no rate to give.
 */
internal fun winRatePercent(won: Int, lost: Int): Int? {
    val decided = won + lost
    return if (decided == 0) null else ((won * 200L + decided) / (decided * 2L)).toInt()
}

/**
 * One player's games in one mode, added up by SQLite - [ScoreDao.playerModeTotals]'s row. [gameModeId] is whatever was
 * stored, a mode this build no longer knows included.
 */
data class PlayerModeTotals(
    /** The player's [ScoreEntry.playerKey]: the games of one player, however the name was cased, share it. */
    val playerKey: String,
    val gameModeId: String,
    val gamesPlayed: Int,
    val gamesWon: Int,
    val gamesLost: Int,
    val soloGames: Int,
    val maxScore: Int,
    val totalScore: Int,
    val fiveOfAKindCount: Int,
    val firstPlayedEpochMillis: Long,
)

/** The spelling of a player's latest game, by [ScoreEntry.playerKey] - [ScoreDao.latestPlayerNames]'s row. */
data class PlayerLatestName(val playerKey: String, val playerName: String)

/** Whether the player [playerKey] names won one game with someone to beat - [ScoreDao.playerOutcomes]'s row. */
data class PlayerOutcome(val playerKey: String, val won: Boolean)

/**
 * Everything the Statistics screen needs about one player, as [ScoreDao.playerRecords] reads it: the spelling of their
 * latest game, their games added up per mode, and the outcome of each game with someone to beat, most recent first - the
 * win streaks depend on game order, which a `GROUP BY` can't see.
 */
data class PlayerRecord(
    val playerName: String,
    val modes: List<PlayerModeTotals>,
    val outcomesMostRecentFirst: List<Boolean>,
)
