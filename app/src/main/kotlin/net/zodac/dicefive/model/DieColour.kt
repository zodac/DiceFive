package net.zodac.dicefive.model

/**
 * The colour a die can land on, in a [GameMode] whose [GameMode.dieColours] isn't empty. Declaration
 * order is the order superuser mode cycles through them (see `GameEngine.cycleDieValue`).
 */
enum class DieColour {
    RED,
    YELLOW,
    BLUE,
}
