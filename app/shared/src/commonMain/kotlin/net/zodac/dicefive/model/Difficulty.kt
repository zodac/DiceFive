package net.zodac.dicefive.model

/**
 * Selectable per AI player on the setup screen. Picks the CPU's strategy (`game/CpuStrategy`) for holds and
 * category choice: EASY never holds individual dice and picks greedily, but stops rerolling as soon as any
 * open category scores above zero, MEDIUM holds by rule of thumb and will bank a good-enough hand
 * (a strong lower-section shape, or a high 3-/4-of-a-kind for the upper section) without using every
 * roll, HARD computes exact expected value over every reroll to decide holds and category.
 */
enum class Difficulty(override val id: String) : StoredId {
    EASY("EASY"),
    MEDIUM("MEDIUM"),
    HARD("HARD"),
}
