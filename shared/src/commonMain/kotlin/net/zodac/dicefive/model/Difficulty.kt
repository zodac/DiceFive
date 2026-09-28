package net.zodac.dicefive.model

/**
 * Selectable per AI player on the setup screen. Drives `AiTurnPlayer`'s hold and category-choice
 * strategy: EASY never holds individual dice and picks greedily, but stops rerolling as soon as any
 * open category scores above zero, MEDIUM holds by rule of thumb and will bank a good-enough hand
 * (a strong lower-section shape, or a high 3-/4-of-a-kind for the upper section) without using every
 * roll, HARD computes exact expected value over every reroll to decide holds and category.
 */
enum class Difficulty {
    EASY,
    MEDIUM,
    HARD,
}
