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
}
