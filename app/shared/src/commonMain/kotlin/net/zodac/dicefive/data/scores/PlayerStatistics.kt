package net.zodac.dicefive.data.scores

import net.zodac.dicefive.model.GameMode

/**
 * One player's stats - the Statistics screen's card. Its own numbers are every mode's games together (the Combined page);
 * [modes] has each mode's, which a mode's page shows ([forMode]). "Player" here means a distinct `playerName`, not a
 * stable identity: renaming a player starts a new card, the same way it starts a new leaderboard entry.
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
    /** Every point scored, solo or multiplayer. */
    val totalScore: Int,
    /** How many 5x this player has scored. */
    val fiveOfAKindCount: Int,
    /** How many dice this player has rolled; games recorded before dice were counted add none. */
    val diceRolled: Int = 0,
    /** Games with nobody to beat - the ones recorded with a null outcome, so counted in [gamesPlayed] only. */
    val soloGames: Int,
    /** The player's games in each mode, in [GameMode.entries] order; a mode never played is absent. */
    val modes: List<ModeStatistics> = emptyList(),
) {

    /** [totalScore] over [gamesPlayed], rounded half up to a whole number; 0 for a player with no games. */
    val averageScore: Int
        get() = averageOf(totalScore, gamesPlayed)

    /** See [winRatePercent]. */
    val winRatePercent: Int?
        get() = winRatePercent(gamesWon, gamesLost)

    /** The card on [mode]'s page: that mode's games alone, its own numbers - or null if the player has none in it. */
    fun forMode(mode: GameMode): PlayerStatistics? = modes.firstOrNull { it.gameMode == mode }?.let { inMode ->
        PlayerStatistics(
            playerName = playerName,
            firstPlayedEpochMillis = inMode.firstPlayedEpochMillis,
            gamesPlayed = inMode.gamesPlayed,
            gamesWon = inMode.gamesWon,
            gamesLost = inMode.gamesLost,
            currentWinStreak = inMode.currentWinStreak,
            bestWinStreak = inMode.bestWinStreak,
            maxScore = inMode.maxScore,
            totalScore = inMode.totalScore,
            fiveOfAKindCount = inMode.fiveOfAKindCount,
            diceRolled = inMode.diceRolled,
            soloGames = inMode.soloGames,
            modes = listOf(inMode),
        )
    }
}

/** One player's games in one [gameMode]: that mode's Statistics page, and its row in the Combined page's table. */
data class ModeStatistics(
    val gameMode: GameMode,
    /** Every game in the mode, solo ones included. */
    val gamesPlayed: Int,
    val gamesWon: Int,
    val gamesLost: Int,
    val soloGames: Int,
    val maxScore: Int,
    /** Every point scored in the mode's games. */
    val totalScore: Int = 0,
    val fiveOfAKindCount: Int = 0,
    /** How many dice were rolled in the mode's games. */
    val diceRolled: Int = 0,
    /** When the player's first game in the mode was played. */
    val firstPlayedEpochMillis: Long = 0L,
    /** The win streaks of the mode's games alone. */
    val currentWinStreak: Int = 0,
    val bestWinStreak: Int = 0,
) {

    /** See [winRatePercent]. */
    val winRatePercent: Int?
        get() = winRatePercent(gamesWon, gamesLost)
}

/** [total] over [games], rounded half up to a whole number; 0 for no games. */
private fun averageOf(total: Int, games: Int): Int = if (games == 0) 0 else ((total.toLong() * 2 + games) / (games * 2L)).toInt()

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
    val diceRolled: Int = 0,
)

/** The spelling of a player's latest game, by [ScoreEntry.playerKey] - [ScoreDao.latestPlayerNames]'s row. */
data class PlayerLatestName(val playerKey: String, val playerName: String)

/** Whether the player [playerKey] names won one game of [gameModeId] with someone to beat, and when - [ScoreDao.playerOutcomes]'s row. */
data class PlayerOutcome(val playerKey: String, val gameModeId: String, val won: Boolean, val timestampEpochMillis: Long)

/** What [ScoreDao.playerRows] reads, every player's together. */
data class PlayerRows(val names: List<PlayerLatestName>, val outcomes: List<PlayerOutcome>, val modeTotals: List<PlayerModeTotals>)

/**
 * Everything the Statistics screen needs about one player, as [ScoreDao.playerRecords] reads it: the spelling of their
 * latest game, their games added up per mode, and their win streaks - which depend on game order, which a `GROUP BY`
 * can't see.
 */
data class PlayerRecord(
    val playerName: String,
    val modes: List<PlayerModeTotals>,
    /** The win streaks of every mode's games together. */
    val streaks: WinStreaks,
    /** The win streaks of each mode's games alone, by [ScoreEntry.gameModeId]; a mode with no game to beat anyone in is absent. */
    val modeStreaks: Map<String, WinStreaks>,
)

/** A player's win streak as it stands, and their longest. */
data class WinStreaks(val current: Int = 0, val best: Int = 0)

/**
 * Walks [outcomesMostRecentFirst] once to find both streaks. A solo game (recorded with a
 * null outcome - nobody to beat) neither extends nor breaks a run, the same rule the
 * device-wide WIN_STREAK achievement counter already follows, so it isn't in the list; a loss ends one.
 *
 * The *current* streak is only "locked in" by the first loss encountered - after that, later
 * (i.e. older) wins are still counted toward [WinStreaks.best] but can no longer be part of
 * the current one.
 */
internal fun winStreaks(outcomesMostRecentFirst: List<Boolean>): WinStreaks {
    var running = 0
    var best = 0
    var current = 0
    var currentLocked = false
    for (won in outcomesMostRecentFirst) {
        if (won) {
            running++
            if (running > best) best = running
            if (!currentLocked) current = running
        } else {
            running = 0
            currentLocked = true
        }
    }
    return WinStreaks(current = current, best = best)
}
