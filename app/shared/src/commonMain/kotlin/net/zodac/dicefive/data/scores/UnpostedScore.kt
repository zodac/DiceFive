package net.zodac.dicefive.data.scores

/** [ScoreDao.bestUnpostedScore]'s answer: a best score, and when the newest of the games it was chosen from was played. */
data class UnpostedScore(val score: Int, val playedEpochMillis: Long)
