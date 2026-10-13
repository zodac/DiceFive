package net.zodac.dicefive.model

/**
 * Where the current player is within their turn. [AWAITING_ROLL] means no
 * roll has happened yet this turn (dice can't be held or scored); once
 * rolled, the player may hold dice and re-roll (while rolls remain) or
 * score at any point.
 */
enum class TurnPhase(override val id: String) : StoredId {
    AWAITING_ROLL("AWAITING_ROLL"),
    ROLLED("ROLLED"),
}
