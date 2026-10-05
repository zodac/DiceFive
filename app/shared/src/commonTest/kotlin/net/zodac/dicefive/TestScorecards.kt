package net.zodac.dicefive

import net.zodac.dicefive.model.ScoreCategory

/**
 * A scorecard written the way a one-score-per-box mode reads - each box's score, or null while it's
 * open - as [net.zodac.dicefive.model.PlayerState.scorecard] holds it: a list of the box's scores.
 */
fun oneScoreEach(scores: Map<ScoreCategory, Int?>): Map<ScoreCategory, List<Int>> = scores.mapValues { listOfNotNull(it.value) }
