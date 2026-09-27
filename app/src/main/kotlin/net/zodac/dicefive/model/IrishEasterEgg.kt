package net.zodac.dicefive.model

import java.text.Normalizer

private val DIACRITICS_REGEX = "\\p{Mn}+".toRegex()
private val IRISH_PLAYER_NAMES = setOf("ireland", "eire")

/**
 * True for "Ireland", "Eire", or "Éire" - case- and accent-insensitive, so "IRELAND", "eire" and
 * "Éire" (however a player's keyboard happens to produce it) all match. [Achievement
 * .LUCK_OF_THE_IRISH]'s trigger, and what [isLuckOfTheIrish] re-skins Tricolour's dice/scorecard
 * colours for - see `ui.game.style.LocalIrishTricolour`.
 */
fun isIrishPlayerName(name: String): Boolean {
    val normalized = Normalizer.normalize(name.trim(), Normalizer.Form.NFD)
        .replace(DIACRITICS_REGEX, "")
        .lowercase()
    return normalized in IRISH_PLAYER_NAMES
}

/**
 * Whether this game should show Tricolour's red/yellow/blue as the Irish flag's green/white/orange
 * instead: Tricolour mode, with the primary player (P1 - always human) named [isIrishPlayerName].
 * Purely cosmetic - every achievement and scoring rule keyed on [DieColour.RED]/[DieColour.YELLOW]/
 * [DieColour.BLUE] still fires exactly as it always did, green/white/orange or not.
 */
val GameState.isLuckOfTheIrish: Boolean
    get() = gameMode == GameMode.TRICOLOUR && players.firstOrNull()?.name?.let(::isIrishPlayerName) == true
