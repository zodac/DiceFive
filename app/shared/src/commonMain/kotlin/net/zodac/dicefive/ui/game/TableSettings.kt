package net.zodac.dicefive.ui.game

import net.zodac.dicefive.ui.game.style.GameVisualTheme

/**
 * Everything about how the table looks and feels that comes from the player's saved Styles picks
 * and Settings rather than from the game itself - see [GameViewModel.tableSettings].
 */
data class TableSettings(
    val visualTheme: GameVisualTheme = GameVisualTheme(),
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
)
