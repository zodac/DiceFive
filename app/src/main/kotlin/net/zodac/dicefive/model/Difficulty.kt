package net.zodac.dicefive.model

/**
 * Selectable per AI player on the setup screen. Drives `AiTurnPlayer`'s hold and category-choice
 * strategy: EASY never holds and picks greedily, MEDIUM holds by rule of thumb, HARD computes
 * exact expected value over every reroll to decide holds and category.
 */
enum class Difficulty {
    EASY,
    MEDIUM,
    HARD,
}
