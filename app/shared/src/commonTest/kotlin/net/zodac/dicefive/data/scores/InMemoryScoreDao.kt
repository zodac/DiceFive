package net.zodac.dicefive.data.scores

import androidx.room.RoomRawQuery

/**
 * Hand-written in-memory double for [ScoreDao] - lets [ScoreRepository]'s
 * pagination logic be verified without a Room/SQLite runtime, which would
 * otherwise need Robolectric or an on-device instrumented test (neither
 * available in this sandbox; see .claude/DESIGN.md's Verification section).
 * The DAO's own `@Query` SQL is deliberately simple (ORDER BY/LIMIT/OFFSET).
 *
 * Starts with [initial]'s rows, for a screen test that only reads them.
 */
class InMemoryScoreDao(initial: List<ScoreEntry> = emptyList()) : ScoreDao {
    private val entries = initial.toMutableList()
    fun rows(): List<ScoreEntry> = entries.toList()
    /** By [DismissedPlayerStats.playerKey], as the real table is. */
    private val dismissed = mutableMapOf<String, DismissedPlayerStats>()
    override suspend fun insert(entry: ScoreEntry) {
        entries += entry
    }

    // As the real queries do, only Leaderboard rows (ScoreEntry.onLeaderboard) are paged, counted,
    // best or collected; career points and Statistics count every row.
    private val leaderboard get() = entries.filter { it.onLeaderboard }

    override suspend fun rawPagedScores(query: RoomRawQuery): List<ScoreEntry> = error("Only the real DAO runs the SQL")

    override suspend fun pagedScores(limit: Int, offset: Int): List<ScoreEntry> =
        leaderboard.sortedByDescending { it.score }.drop(offset).take(limit)

    private fun forMode(gameModeId: String) =
        entries.filter { it.playedUnmodified && it.gameModeId == gameModeId }

    override suspend fun pagedScoresForMode(gameModeId: String, limit: Int, offset: Int): List<ScoreEntry> =
        forMode(gameModeId).sortedByDescending { it.score }.drop(offset).take(limit)

    override suspend fun count(): Int = leaderboard.size

    override suspend fun countForMode(gameModeId: String): Int = forMode(gameModeId).size

    override suspend fun bestScoreForPlayer(playerKey: String): Int? =
        leaderboard.filter { it.playerKey == playerKey }.maxOfOrNull { it.score }

    override suspend fun distinctScores(): List<Int> = leaderboard.map { it.score }.distinct()

    override suspend fun primaryPlayerTotalPoints(): Int? =
        entries.filter { it.isPrimaryPlayer }.takeIf { it.isNotEmpty() }?.sumOf { it.score }

    override suspend fun primaryPlayerGameModeIds(): List<String> = entries.filter { it.isPrimaryPlayer }.map { it.gameModeId }.distinct()
    override suspend fun bestUnpostedScore(gameModeId: String, afterEpochMillis: Long, maxScore: Int): UnpostedScore? = null

    override suspend fun playerModeTotals(): List<PlayerModeTotals> =
        entries.filter { it.playerKey !in dismissed }
            .groupBy { it.playerKey to it.gameModeId }
            .entries.sortedWith(compareBy({ it.key.first }, { it.key.second }))
            .map { (key, games) ->
                PlayerModeTotals(
                    playerKey = key.first,
                    gameModeId = key.second,
                    gamesPlayed = games.size,
                    gamesWon = games.count { it.won == true },
                    gamesLost = games.count { it.won == false },
                    soloGames = games.count { it.won == null },
                    maxScore = games.maxOf { it.score },
                    totalScore = games.sumOf { it.score },
                    fiveOfAKindCount = games.sumOf { it.fiveOfAKindCount },
                    firstPlayedEpochMillis = games.minOf { it.timestampEpochMillis },
                )
            }

    override suspend fun latestPlayerNames(): List<PlayerLatestName> =
        entries.filter { it.playerKey !in dismissed }.groupBy { it.playerKey }.map { (playerKey, games) ->
            PlayerLatestName(playerKey, games.last { it.timestampEpochMillis == games.maxOf { game -> game.timestampEpochMillis } }.playerName)
        }

    override suspend fun playerOutcomes(): List<PlayerOutcome> =
        entries.filter { it.playerKey !in dismissed }
            .sortedWith(compareBy<ScoreEntry> { it.playerKey }.thenByDescending { it.timestampEpochMillis })
            .mapNotNull { entry -> entry.won?.let { PlayerOutcome(entry.playerKey, it) } }

    override suspend fun clearDismissal(playerKey: String) {
        dismissed -= playerKey
    }

    override suspend fun clearAllScores() {
        entries.clear()
    }

    override suspend fun clearAllDismissals() {
        dismissed.clear()
    }

    override suspend fun allScores(): List<ScoreEntry> = rows()

    override suspend fun allDismissals(): List<DismissedPlayerStats> = dismissed.values.toList()

    override suspend fun insertMissing(entries: List<ScoreEntry>) {
        for (entry in entries) if (this.entries.none { it.uuid == entry.uuid }) insert(entry)
    }

    override suspend fun putDismissals(dismissals: List<DismissedPlayerStats>) {
        for (dismissal in dismissals) dismissed[dismissal.playerKey] = dismissal
    }

    override suspend fun deleteScoresThrough(epochMillis: Long) {
        entries.removeAll { it.timestampEpochMillis <= epochMillis }
    }

    override suspend fun deleteDismissalsThrough(epochMillis: Long) {
        dismissed.values.removeAll { it.dismissedAtEpochMillis <= epochMillis }
    }

    override suspend fun deleteUndoneDismissals() {
        dismissed.values.removeAll { dismissal -> entries.any { it.playerKey == dismissal.playerKey && it.timestampEpochMillis > dismissal.dismissedAtEpochMillis } }
    }
}
