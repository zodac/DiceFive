package net.zodac.dicefive.ui.common

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.onSizeChanged
import net.zodac.dicefive.ui.game.style.drawPips

/**
 * One scattered watermark die: position and size as fractions of the screen, so the motif scales
 * with the device instead of clustering in a corner on a tablet.
 */
private data class WatermarkDie(val centerX: Float, val centerY: Float, val size: Float, val rotation: Float, val value: Int)

private val WATERMARK_DICE = listOf(
    WatermarkDie(centerX = 0.16f, centerY = 0.80f, size = 0.30f, rotation = -14f, value = 5),
    WatermarkDie(centerX = 0.82f, centerY = 0.92f, size = 0.38f, rotation = 11f, value = 3),
    WatermarkDie(centerX = 0.90f, centerY = 0.42f, size = 0.22f, rotation = 24f, value = 2),
    WatermarkDie(centerX = 0.06f, centerY = 0.40f, size = 0.18f, rotation = -28f, value = 6),
)

/**
 * The app's one piece of brand scenery: a gentle surface gradient, a spotlight tint from above
 * (which is where the logo sits), and a faint scatter of oversized dice for texture.
 *
 * Every colour comes from the M3 scheme - `surfaceContainer` tones for the gradient, `primary`
 * for the light - so it tracks the theme instead of being a second palette running alongside it.
 * It's a *surface treatment*, deliberately quiet enough that ordinary Material components sit on
 * it unmodified, at their normal contrast.
 *
 * With [showDice] off there are no dice at all - the in-game screen, whose table art sits on top.
 *
 * With [driftingDice] (the main menu and the screens off it - not the game itself), the dice don't sit still: they start where they always
 * are, then drift slowly up and across the screen, turning as they go, and come back in from below
 * on new paths (see [DiceDrift]). The drift is shared across those screens, so the dice keep their places and faces from one to the next until a game starts.
 */
@Composable
fun BrandBackdrop(
    modifier: Modifier = Modifier,
    driftingDice: Boolean = false,
    showDice: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    // The app's one shared drift where there is one (see DiceFiveApp), so the dice carry on from where
    // the last screen left them; a backdrop of its own otherwise.
    val shared = LocalDriftState.current
    val drift = if (driftingDice && showDice) shared ?: remember { DriftState() } else null
    // Still, where they are, under reduced motion - a watermark rather than a drift.
    if (drift != null && !LocalReduceMotion.current) {
        LaunchedEffect(drift) {
            // The infinite-animation frame, not a plain one: it never ends, and this is what tells a
            // UI test not to wait for it to (a plain frame loop would keep the menu from ever idling).
            while (true) withInfiniteAnimationFrameNanos { now -> drift.onFrame(now) }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { drift?.size = Size(it.width.toFloat(), it.height.toFloat()) }
            // drawWithCache, not layered background() modifiers: the brushes depend on the measured
            // size (the spotlight is placed and sized relative to it), so they're built once per
            // size change rather than on every recomposition.
            .drawWithCache {
                val base = Brush.verticalGradient(
                    listOf(colorScheme.surfaceContainerHigh, colorScheme.surface, colorScheme.surfaceContainerLowest),
                )
                val spotlight = Brush.radialGradient(
                    colors = listOf(colorScheme.primary.copy(alpha = 0.16f), Color.Transparent),
                    center = Offset(size.width / 2f, size.height * 0.14f),
                    radius = size.maxDimension * 0.70f,
                )

                onDrawBehind {
                    drawRect(base)
                    drawRect(spotlight)
                    if (!showDice) return@onDrawBehind
                    if (drift == null) {
                        drawDiceWatermark(colorScheme.onSurface, WATERMARK_DICE.map { it.toDrifting() })
                    } else {
                        drift.frame.longValue // Read here, so each move redraws.
                        // Only the dice actually showing - one sliding in from below isn't drawn until it's on screen.
                        drawDiceWatermark(colorScheme.onSurface, drift.dice.dice.filter { isShowing(it, size.width, size.height) })
                    }
                }
            },
        content = content,
    )
}

/**
 * The drifting dice's state, kept outside any one screen so it outlives navigation: every screen
 * that shows the drift draws this same one, and it only starts over on [reset] (or a fresh launch).
 */
internal class DriftState {

    private companion object {
        const val MIN_DRIFT_FRAME_NANOS = 30_000_000L
    }

    var dice = newDrift()
        private set

    /** Bumped every frame the dice move, and read only while drawing - so they redraw without anything recomposing. */
    val frame = mutableLongStateOf(0L)

    var size = Size.Zero

    /**
     * How much faster than the menu's pace the dice move: 1 on the menu. The drift is in fractions of the
     * screen a second, so on something much smaller - a Styles tile - the same pace would be a crawl
     * you can't see; whoever draws a small surface sets this to keep it moving at the same speed in dp.
     */
    var timeScale = 1f

    private var lastFrameNanos: Long? = null

    /** Puts the dice back where they start, moving from the beginning again. */
    fun reset() {
        dice = newDrift()
        lastFrameNanos = null
    }

    /**
     * Moves the dice on to the frame at [now]. Two backdrops can be on screen at once mid-transition,
     * each running this every frame; only the first to reach a given frame moves them.
     */
    fun onFrame(now: Long) {
        val last = lastFrameNanos
        if (last == now) return
        // The drift is slow, so about 30 redraws a second look the same as 60 or 120 and cost half or less: a
        // frame arriving sooner than that is left for the next, and the dice then move on by the whole time
        // since the last one they did move on for, so their speed is unchanged.
        if (last != null && now - last < MIN_DRIFT_FRAME_NANOS) return
        // A stalled frame (or the app coming back from the background) moves them on by
        // no more than a tenth of a second, never jumping them across the screen.
        val seconds = last?.let { ((now - it) / 1e9f).coerceIn(0f, MAX_DRIFT_STEP_SECONDS) * timeScale } ?: 0f
        lastFrameNanos = now
        dice.advance(seconds, size.width, size.height)
        frame.longValue = now
    }

    private fun newDrift() = DiceDrift(WATERMARK_DICE.map { it.toDrifting() })
}

internal val LocalDriftState = staticCompositionLocalOf<DriftState?> { null }

// The longest step the drifting dice are moved on by in one frame.
private const val MAX_DRIFT_STEP_SECONDS = 0.1f

private fun WatermarkDie.toDrifting() = DriftingDie(x = centerX, y = centerY, size = size, rotation = rotation, value = value)

/** Draws [dice] as barely-there outlined die faces - texture, never a readable element. */
internal fun DrawScope.drawDiceWatermark(color: Color, dice: List<DriftingDie>) {
    val outline = color.copy(alpha = 0.05f)
    val pips = color.copy(alpha = 0.04f)

    for (die in dice) {
        val dieSize = size.minDimension * die.size
        val topLeft = Offset(die.x * size.width - dieSize / 2f, die.y * size.height - dieSize / 2f)

        rotate(degrees = die.rotation, pivot = Offset(topLeft.x + dieSize / 2f, topLeft.y + dieSize / 2f)) {
            drawRoundRect(
                color = outline,
                topLeft = topLeft,
                size = Size(dieSize, dieSize),
                cornerRadius = CornerRadius(dieSize * 0.22f),
                style = Stroke(width = dieSize * 0.035f),
            )
            // inset() rather than a translate: drawPips lays its pips out across the draw scope's
            // own size, so shrinking that scope to the die's square is what positions them.
            inset(
                left = topLeft.x + dieSize * 0.16f,
                top = topLeft.y + dieSize * 0.16f,
                right = size.width - topLeft.x - dieSize * 0.84f,
                bottom = size.height - topLeft.y - dieSize * 0.84f,
            ) {
                drawPips(value = die.value, color = pips, pipRadiusFraction = 0.11f)
            }
        }
    }
}
