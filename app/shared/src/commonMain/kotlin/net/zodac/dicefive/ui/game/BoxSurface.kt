package net.zodac.dicefive.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.zodac.dicefive.data.settings.MotionGroup
import net.zodac.dicefive.ui.common.BoundedArt
import net.zodac.dicefive.ui.common.PulsingArt
import net.zodac.dicefive.ui.style.BoxPaint
import net.zodac.dicefive.ui.style.BoxTone
import net.zodac.dicefive.ui.style.ScoreBoxStyle
import net.zodac.dicefive.ui.style.rememberArtSeconds
import net.zodac.dicefive.ui.theme.GoldAccent

// The board's buttons (Totals, Undo) drawn as an open box of the player's box style, so they sit in its last row as part
// of the same card.

/** Each button is its own box, for a texture or a Wave's colour that differs from every category's. */
private const val BUTTON_SEED = 101

/** Gold dark enough to read on a light box style's fill (Paper, White Marble), where the board's gold washes out. */
private val DarkGold = Color(0xFF8A6200)

/** Whether [paint]'s fill is light - its ink dark. */
internal val BoxPaint.isLight: Boolean get() = ink.luminance() < 0.5f

/** The gold a lit glyph takes on [paint]'s fill: the board's gold, or a darker one on a light fill. */
internal fun goldOn(paint: BoxPaint): Color = if (paint.isLight) DarkGold else GoldAccent

/** [style]'s open box behind a button of this [shape]: its fill, its texture and its rim, clipped to the shape. */
internal fun Modifier.boxSurface(style: ScoreBoxStyle, shape: Shape, corner: Dp): Modifier {
    val paint = style.paint(BoxTone.OPEN)
    return this
        .clip(shape)
        .background(Brush.linearGradient(paint.fill))
        .drawBehind { with(style) { drawTexture(paint, seed = BUTTON_SEED, lit = true, cornerPx = corner.toPx()) } }
        .then(if (style.rimWidth > 0.dp) Modifier.border(style.rimWidth, paint.border, shape) else Modifier)
}

/**
 * [style]'s moving art over an open box in [paint] (a [tone] of D20's, or neither): RGB's lights, re-recorded in a node of
 * their own as the art clock ticks, and Neon's glow, drawn once and only faded. The art clock runs only at the High
 * animation level, so below it both hold still, as the RGB and Neon dice do. Nothing for a style with neither.
 */
@Composable
internal fun BoxScope.MovingBoxArt(style: ScoreBoxStyle, paint: BoxPaint, tone: BoxTone, seed: Int, corner: Dp) {
    if (!style.animated && !style.pulses) return
    val seconds = rememberArtSeconds(MotionGroup.SCORE_BOX)
    if (style.animated) {
        BoundedArt(modifier = Modifier.matchParentSize()) {
            with(style) { drawLights(paint, tone, seed = seed, seconds = seconds.value, cornerPx = corner.toPx()) }
        }
    }
    if (style.pulses) {
        val alpha = remember(style, seed, seconds) { derivedStateOf { style.glowAlpha(seconds.value, seed) } }
        PulsingArt(alpha = alpha, modifier = Modifier.matchParentSize()) { glow ->
            with(style) { drawGlow(paint, corner.toPx(), glow) }
        }
    }
}

/** [style]'s moving art over a button drawn with [boxSurface]. */
@Composable
internal fun BoxScope.BoxSurfaceLights(style: ScoreBoxStyle, corner: Dp) =
    MovingBoxArt(style, style.paint(BoxTone.OPEN), BoxTone.OPEN, BUTTON_SEED, corner)
