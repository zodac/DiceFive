package net.zodac.dicefive.model

/**
 * An optional limit on how long a player has to complete their whole turn (not each individual
 * roll) - chosen on the setup screen and carried on [GameState] for the life of the game.
 */
enum class TurnTimer(val seconds: Int?) {
    NONE(null),
    SECONDS_30(30),
    SECONDS_60(60),
    SECONDS_120(120),
}
