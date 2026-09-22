package net.zodac.dicefive.ui.game.style

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush

/**
 * Renders a single die face. Implementations are swapped wholesale via
 * [GameVisualTheme.diceStyle] - e.g. a future "user picked a dice skin"
 * setting would just provide a different [DiceStyle] instance.
 */
interface DiceStyle {
    val id: String

    @Composable
    fun Die(value: Int, held: Boolean, modifier: Modifier)
}

/**
 * Renders the shaker cup, including its shake/tilt animation.
 *
 * [rolling] should be true for the brief moment a roll is being resolved
 * (drives the shake), [tilted] reflects whether this turn's dice have
 * already been poured out at least once (drives the resting pose).
 */
interface DiceCupStyle {
    val id: String

    @Composable
    fun Cup(rolling: Boolean, tilted: Boolean, modifier: Modifier)
}

/**
 * Supplies the (independently swappable) backgrounds for the scoring area
 * and the dice-tray area. Deliberately excludes the player header, which
 * always uses the app's base theme background.
 */
interface TableBackground {
    val id: String
    val scoreAreaBrush: Brush
    val diceTrayBrush: Brush
}

/** Bundles the pluggable game-table art. Swap any field to re-skin that piece independently. */
data class GameVisualTheme(
    val diceStyle: DiceStyle = IvoryDiceStyle,
    val diceCupStyle: DiceCupStyle = LeatherDiceCupStyle,
    val background: TableBackground = MidnightFeltBackground,
)

val LocalGameVisualTheme = staticCompositionLocalOf { GameVisualTheme() }
