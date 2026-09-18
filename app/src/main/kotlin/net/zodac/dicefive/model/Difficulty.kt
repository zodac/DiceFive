package net.zodac.dicefive.model

/**
 * Selectable per AI player, but disabled in the setup UI for v1 — every AI
 * plays the same basic strategy regardless of this value until difficulty
 * tuning is implemented.
 */
enum class Difficulty {
    EASY,
    MEDIUM,
    HARD,
}
