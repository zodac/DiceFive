package net.zodac.dicefive.data.scores

/** How many leaderboard rows make up one page. Was 100; halved so a page is a shorter scroll. */
const val SCORES_PAGE_SIZE = 50

class ScoreRepository(private val scoreDao: ScoreDao) {

    suspend fun recordScore(playerName: String, score: Int, timestampEpochMillis: Long = System.currentTimeMillis()) {
        scoreDao.insert(ScoreEntry(playerName = playerName, score = score, timestampEpochMillis = timestampEpochMillis))
    }

    suspend fun page(pageIndex: Int, pageSize: Int = SCORES_PAGE_SIZE): List<ScoreEntry> =
        scoreDao.pagedScores(limit = pageSize, offset = pageIndex * pageSize)

    suspend fun totalCount(): Int = scoreDao.count()

    /**
     * The highest score on the leaderboard, or null if there isn't one yet. Read *before* a
     * finished game's own rows are inserted, or "beat your best score" would be comparing the new
     * score against itself - see `GameViewModel.finishGame`.
     */
    suspend fun bestScore(): Int? = scoreDao.bestScore()

    /**
     * Every distinct score on the leaderboard. The score-collection achievements are measured
     * against this rather than a stored counter, so they are retroactive and always agree with
     * what the Scores screen actually shows.
     */
    suspend fun distinctScores(): Set<Int> = scoreDao.distinctScores().toSet()
}
