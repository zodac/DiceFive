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
 * Supplies the background for the scoring area. Deliberately excludes the player header, which
 * always uses the app's base theme background. Independently swappable from [DiceMat], which
 * covers the dice-tray area below it.
 */
interface TableBackground {
    val id: String
    val scoreAreaBrush: Brush
}

/** Supplies the (independently swappable) dice-tray mat, separate from [TableBackground]. */
interface DiceMat {
    val id: String
    val diceTrayBrush: Brush

    /**
     * An optional decorative overlay drawn on top of [diceTrayBrush], e.g. the fire theme's flame
     * trim licking up from the tray's bottom edge. Most mats don't need one, so it's a no-op by
     * default rather than every implementation repeating an empty override.
     */
    @Composable
    fun DiceTrayDecoration(modifier: Modifier) {}
}

/** Bundles the pluggable game-table art. Swap any field to re-skin that piece independently. */
data class GameVisualTheme(
    val diceStyle: DiceStyle = DiceStyles.default,
    val diceCupStyle: DiceCupStyle = DiceCupStyles.default,
    val background: TableBackground = TableBackgrounds.default,
    val mat: DiceMat = DiceMats.default,
)

val LocalGameVisualTheme = staticCompositionLocalOf { GameVisualTheme() }

/**
 * Whether Tricolour's dice/scorecard colours are showing as the Irish flag's green/white/orange
 * right now - true only for [net.zodac.dicefive.model.isLuckOfTheIrish]'s exact condition, provided
 * once per game by `GameScreen` from the live [net.zodac.dicefive.model.GameState]. Kept separate
 * from [GameVisualTheme] (a player's own style picks) since this isn't a choice, it's an easter egg
 * tied to this specific game.
 */
val LocalIrishTricolour = staticCompositionLocalOf { false }
