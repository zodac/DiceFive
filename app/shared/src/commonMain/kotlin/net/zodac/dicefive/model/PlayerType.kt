package net.zodac.dicefive.model

enum class PlayerType(override val id: String) : StoredId {
    HUMAN("HUMAN"),
    AI("AI"),
}
