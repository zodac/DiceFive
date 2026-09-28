package net.zodac.dicefive.navigation

/** Route constants for [DiceFiveNavHost]. */
object Screen {
    const val MENU = "menu"

    const val PLAY_GRAPH = "play"
    const val PLAY_SETUP = "play/setup"
    const val PLAY_SETUP_ROUTE = "$PLAY_SETUP?resume={resume}"
    const val PLAY_GAME = "play/game"

    const val SCORES = "scores"
    const val STATISTICS = "statistics"
    const val ACHIEVEMENTS = "achievements"
    const val SETTINGS = "settings"
    const val STYLES = "styles"

    fun playSetup(resume: Boolean) = "$PLAY_SETUP?resume=$resume"
}
