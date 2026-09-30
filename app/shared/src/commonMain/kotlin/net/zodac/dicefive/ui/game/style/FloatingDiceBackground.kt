package net.zodac.dicefive.ui.game.style

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import net.zodac.dicefive.ui.common.DriftState
import net.zodac.dicefive.ui.common.LocalReduceMotion
import net.zodac.dicefive.ui.common.drawDiceWatermark
import net.zodac.dicefive.ui.common.isShowing
import net.zodac.dicefive.ui.game.REGULAR_BOARD_HEIGHT
import net.zodac.dicefive.ui.theme.OnSurface
import net.zodac.dicefive.ui.theme.Primary
import net.zodac.dicefive.ui.theme.Surface
import net.zodac.dicefive.ui.theme.SurfaceContainerHigh
import net.zodac.dicefive.ui.theme.SurfaceContainerLowest

// About how tall the main menu is, in dp.
private const val MENU_REFERENCE_HEIGHT_DP = 760f

/**
 * The main menu's backdrop as a table background: the same gradient, the same spotlight from above
 * and the same faint dice drifting slowly up the screen, turning as they go (see
 * [net.zodac.dicefive.ui.common.BrandBackdrop]). The colours are the app theme's own, which is one
 * fixed scheme, so they're used directly rather than read from the theme. The secret background
 * that Not Those Dice unlocks.
 */
object FloatingDiceBackground : TableBackground {
    override val id: String = "floating_dice"

    override val scoreAreaBrush: Brush = Brush.verticalGradient(listOf(SurfaceContainerHigh, Surface, SurfaceContainerLowest))

    // The dice's own drift, shared by everywhere this background is drawn - a Styles tile, the game -
    // so they carry on from where they were rather than starting over.
    private val drift = DriftState()

    @Composable
    override fun Animate() {
        // Still, where they are, under reduced motion - as the menu's own backdrop dice are. (This
        // is also how a Styles tile that isn't on screen is held still.)
        if (LocalReduceMotion.current) return
        LaunchedEffect(Unit) {
            // The infinite-animation frame, as the menu's backdrop uses, so a UI test doesn't wait for it to end.
            while (true) withInfiniteAnimationFrameNanos { now -> drift.onFrame(now) }
        }
    }

    override fun DrawScope.drawScoreAreaDecoration() {
        drift.size = size
        // The drift moves in fractions of the surface a second, at the menu's pace on something as tall
        // as the menu; the game's shorter board runs it faster, so its dice cover the same ground on
        // screen. Anything smaller still - a Styles tile - keeps the board's pace rather than speeding
        // up further, so the preview moves as a miniature of the board rather than racing.
        val height = maxOf(size.height / density, REGULAR_BOARD_HEIGHT.value)
        drift.timeScale = (MENU_REFERENCE_HEIGHT_DP / height).coerceAtLeast(1f)
        drift.frame.longValue // Read here, so each move redraws.
        drawRect(
            Brush.radialGradient(
                colors = listOf(Primary.copy(alpha = 0.16f), Color.Transparent),
                center = Offset(size.width / 2f, size.height * 0.14f),
                radius = size.maxDimension * 0.70f,
            ),
        )
        drawDiceWatermark(OnSurface, drift.dice.dice.filter { isShowing(it, size.width, size.height) })
    }
}
