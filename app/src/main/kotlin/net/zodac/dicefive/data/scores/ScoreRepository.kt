package net.zodac.dicefive.data.scores

const val SCORES_PAGE_SIZE = 100

class ScoreRepository(private val scoreDao: ScoreDao) {

    suspend fun recordScore(playerName: String, score: Int, timestampEpochMillis: Long = System.currentTimeMillis()) {
        scoreDao.insert(ScoreEntry(playerName = playerName, score = score, timestampEpochMillis = timestampEpochMillis))
    }

    suspend fun page(pageIndex: Int, pageSize: Int = SCORES_PAGE_SIZE): List<ScoreEntry> =
        scoreDao.pagedScores(limit = pageSize, offset = pageIndex * pageSize)

    suspend fun totalCount(): Int = scoreDao.count()
}
