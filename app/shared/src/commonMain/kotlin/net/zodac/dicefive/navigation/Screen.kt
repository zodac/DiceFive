package net.zodac.dicefive.navigation

import net.zodac.dicefive.game.style.StyleCategory

/** Route constants for [DiceFiveNavHost]. */
object Screen {
    const val MENU = "menu"

    const val PLAY_GRAPH = "play"
    const val PLAY_SETUP = "play/setup"
    const val PLAY_GAME = "play/game"

    const val SCORES = "scores"
    const val STATISTICS = "statistics"
    const val ACHIEVEMENTS = "achievements"
    const val SETTINGS = "settings"
    const val STYLES = "styles"

    /** A Styles category's own page, opened from its field on the Styles page: "styles/dice_cup". */
    fun styleCategory(category: StyleCategory): String = "$STYLES/${category.name.lowercase()}"

    const val RULES = "rules"
    const val TUTORIAL = "tutorial"
}
