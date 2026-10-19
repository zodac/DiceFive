package net.zodac.dicefive.data.scores

import androidx.room.RoomRawQuery

/**
 * Hand-written in-memory double for [ScoreDao] - lets [ScoreRepository]'s
 * pagination logic be verified without a Room/SQLite runtime, which would
 * otherwise need Robolectric or an on-device instrumented test (neither
 * available in this sandbox; see .claude/DESIGN.md's Verification section).
 * The DAO's own `@Query` SQL is deliberately simple (ORDER BY/LIMIT/OFFSET).
 */
class InMemoryScoreDao : ScoreDao {
    private val entries = mutableListOf<ScoreEntry>()
    fun rows(): List<ScoreEntry> = entries.toList()
    private val dismissed = mutableMapOf<String, Long>()
    private var nextId = 1L

    override suspend fun insert(entry: ScoreEntry) {
        entries += entry.copy(id = nextId++)
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

    override suspend fun bestScoreForPlayer(playerName: String): Int? =
        leaderboard.filter { it.playerName == playerName }.maxOfOrNull { it.score }

    override suspend fun distinctScores(): List<Int> = leaderboard.map { it.score }.distinct()

    override suspend fun primaryPlayerTotalPoints(): Int? =
        entries.filter { it.isPrimaryPlayer }.takeIf { it.isNotEmpty() }?.sumOf { it.score }

    override suspend fun primaryPlayerGameModeIds(): List<String> = entries.filter { it.isPrimaryPlayer }.map { it.gameModeId }.distinct()
    override suspend fun bestUnpostedScore(gameModeId: String, afterEpochMillis: Long, maxScore: Int): UnpostedScore? = null

    override suspend fun playerGames(): List<PlayerGame> =
        entries.filter { it.playerName !in dismissed }
            // SQLite's COLLATE NOCASE (for the names used here), then the exact name, then newest first.
            .sortedWith(compareBy<ScoreEntry>({ it.playerName.lowercase() }, { it.playerName }).thenByDescending { it.timestampEpochMillis })
            .map { PlayerGame(it.playerName, it.timestampEpochMillis, it.won, it.score, it.fiveOfAKindCount, it.gameModeId) }

    override suspend fun dismissPlayer(playerName: String, atEpochMillis: Long) {
        dismissed[playerName] = atEpochMillis
    }

    override suspend fun clearDismissal(playerName: String) {
        dismissed -= playerName
    }

    override suspend fun clearAllScores() {
        entries.clear()
    }

    override suspend fun clearAllDismissals() {
        dismissed.clear()
    }

    override suspend fun allScores(): List<ScoreEntry> = rows()

    override suspend fun allDismissals(): List<DismissedPlayerStats> = dismissed.map { (name, at) -> DismissedPlayerStats(name, at) }

    override suspend fun insertMissing(entries: List<ScoreEntry>) {
        for (entry in entries) if (this.entries.none { it.uuid == entry.uuid }) insert(entry)
    }

    override suspend fun putDismissals(dismissals: List<DismissedPlayerStats>) {
        for (dismissal in dismissals) dismissed[dismissal.playerName] = dismissal.dismissedAtEpochMillis
    }

    override suspend fun deleteScoresThrough(epochMillis: Long) {
        entries.removeAll { it.timestampEpochMillis <= epochMillis }
    }

    override suspend fun deleteDismissalsThrough(epochMillis: Long) {
        dismissed.entries.removeAll { it.value <= epochMillis }
    }

    override suspend fun deleteUndoneDismissals() {
        dismissed.entries.removeAll { (name, at) -> entries.any { it.playerName.equals(name, ignoreCase = true) && it.timestampEpochMillis > at } }
    }
}
