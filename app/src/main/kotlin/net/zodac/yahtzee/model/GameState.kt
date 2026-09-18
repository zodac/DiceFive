package net.zodac.yahtzee.model

/**
 * Placeholder top-level game state. Will grow to include scorecard entries,
 * turn/round tracking, and multi-player support as requirements are defined.
 */
data class GameState(
    val dice: List<Die> = List(5) { Die() },
    val rollsRemaining: Int = 3,
    val scores: Map<ScoreCategory, Int?> = ScoreCategory.entries.associateWith { null },
)
