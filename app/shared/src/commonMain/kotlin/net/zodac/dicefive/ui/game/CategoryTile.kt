package net.zodac.dicefive.ui.game

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.ceil
import net.zodac.dicefive.model.DieColour
import net.zodac.dicefive.model.HitTarget
import net.zodac.dicefive.model.PlaceMatch
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.model.ScoreSection
import net.zodac.dicefive.ui.common.LocalReduceMotion
import net.zodac.dicefive.ui.common.PulsingArt
import net.zodac.dicefive.ui.common.scorePulse
import net.zodac.dicefive.ui.style.BoxTone
import net.zodac.dicefive.ui.style.LocalGameVisualTheme
import net.zodac.dicefive.ui.style.LocalIrishTricolour
import net.zodac.dicefive.ui.style.palette
import net.zodac.dicefive.ui.theme.GoldAccent
import net.zodac.dicefive.ui.theme.TileHighlightBottom
import net.zodac.dicefive.ui.theme.TileHighlightTop
import net.zodac.dicefive.ui.theme.TileIconColor
import net.zodac.dicefive.ui.theme.TileTealBottom

/** Fixed intrinsic sizes - deliberately NOT derived from ambient row height/aspectRatio, which
 * blew up unpredictably (the 5x tile filling the screen, the grid starving its score text).
 * [COMPACT_TILE_SIZE] is for a grid with more rows than Standard's six - see `scoreBoardHeight`. */
internal val REGULAR_TILE_SIZE = 48.dp
internal val COMPACT_TILE_SIZE = 40.dp

/** How faint the Coloured House tile's stripes go once it's scored, matching the dimmed glyphs. */
private const val SCORED_STRIPE_ALPHA = 0.35f

/**
 * Where the Coloured House tile's diagonal stripes meet, as a fraction of the tile's side along each
 * edge: two-thirds cuts the diagonal into three equal lengths, so the red corner, yellow band and blue
 * corner are equally *wide*. An earlier split made them equal in *area* instead (legs of
 * `sqrt(2/3)`), which left the middle band visibly thinner than the corners - a diagonal band spends
 * its area on length, not width.
 */
private const val STRIPE_CORNER_LEG = 2f / 3f

/**
 * One scoring-category slot in the grid: an icon tile that glows gold and pulses when the
 * player's current dice can legally score it, or reads as visibly "spent" once [scored]. A [disabled] one
 * is the odd one out on purpose: every other look is a filled tile, and this is only an outline.
 */
@Composable
fun CategoryTile(
    category: ScoreCategory,
    highlighted: Boolean,
    modifier: Modifier = Modifier,
    // A solid gold flash, ink inverted to the tile's dark green: a player's first 5x of the game.
    flashing: Boolean = false,
    // Stretched across the width it's given (5x spans two columns), at the usual height.
    wide: Boolean = false,
    // A square of this side instead of the usual size: 5x, over two rows, when nothing sits under it.
    squareSize: Dp? = null,
    // With [squareSize], a different height: the tile is [squareSize] wide and this tall.
    squareHeight: Dp? = null,
    compact: Boolean = false,
    scored: Boolean = false,
    /** Switched off for this game (see [net.zodac.dicefive.model.PlayerState.disabledCategories]): drawn as an empty, dashed outline with a slash through it. */
    disabled: Boolean = false,
    fiveOfAKindBonusCount: Int = 0,
    /** A solid outline in this colour in place of the usual border - the box a player last scored in (see [LastScoredHighlight]). */
    outlineColor: Color? = null,
    /** The target a Hit List target box calls, drawn in place of a glyph - and with [matches], how the dice stand against it. */
    target: HitTarget? = null,
    matches: List<PlaceMatch>? = null,
    onClick: (() -> Unit)? = null,
    /** Told when a finger goes down on, and comes off, a tile that can be tapped - for showing what a tap would take. */
    onPressChange: ((Boolean) -> Unit)? = null,
) {
    val irishTricolour = LocalIrishTricolour.current
    val cornerRadius = if (squareSize != null) 16.dp else 10.dp
    val interactionSource = remember { MutableInteractionSource() }
    if (onPressChange != null) {
        val pressed by interactionSource.collectIsPressedAsState()
        val currentOnPressChange by rememberUpdatedState(onPressChange)
        LaunchedEffect(pressed) { currentOnPressChange(pressed) }
        // A tile that goes (scored, or the turn moves on) with a finger on it never sees the lift.
        DisposableEffect(Unit) { onDispose { currentOnPressChange(false) } }
    }
    // The player's box style (Styles): D20's halves (1-10, 11-20) take its two half fills, every other open box its open one.
    val boxStyle = LocalGameVisualTheme.current.box
    val tone = when (category.section) {
        ScoreSection.FIRST_HALF -> BoxTone.FIRST_HALF
        ScoreSection.SECOND_HALF -> BoxTone.SECOND_HALF
        else -> BoxTone.OPEN
    }
    val paint = if (scored) boxStyle.scored else boxStyle.paint(tone)
    val shape = boxStyle.shape(cornerRadius)
    val tileSize = squareSize ?: if (compact) COMPACT_TILE_SIZE else REGULAR_TILE_SIZE
    // Three distinct looks, never overlapping in practice (a scored category is never a legal,
    // highlightable choice): gold glow for "score this now", the style's spent fill (Classic's flat grey)
    // for "already used", its open fill otherwise. Without the spent state a scored tile looked identical
    // to an ordinary open one - only the (small, low-contrast) score text beside it showed anything had
    // happened. The gold is the same in every style.
    val backgroundColors = when {
        disabled -> listOf(Color.Transparent, Color.Transparent)
        highlighted -> listOf(TileHighlightTop, TileHighlightBottom)
        else -> paint.fill
    }
    // What a glyph is drawn in on this fill: the style's ink, or the light ink on the gold and on a disabled outline.
    val ink = if (highlighted || disabled) TileIconColor else paint.ink
    // A highlighted tile's pulsing gold border is drawn by GlowBorder instead, over the content.
    val iconColor = when {
        disabled -> TileIconColor.copy(alpha = DISABLED_ICON_ALPHA)
        highlighted -> GoldAccent
        scored -> paint.ink.copy(alpha = 0.4f)
        else -> paint.ink
    }

    // Gold in and out over the same 400 ms as an achievement row's flash; straight on and off under reduced motion.
    val flash by animateFloatAsState(
        targetValue = if (flashing) 1f else 0f,
        animationSpec = if (LocalReduceMotion.current) snap() else tween(FLASH_TRANSITION_MILLIS),
        label = "tileFlash", // i18n: not translated - an animation label, not shown
    )
    val flashedColors = if (flash > 0f) backgroundColors.map { lerp(it, GoldAccent, flash) } else backgroundColors
    val flashedIconColor = if (flash > 0f) lerp(iconColor, TileTealBottom, flash) else iconColor

    Box(
        modifier = modifier
            .then(if (wide) Modifier.height(tileSize) else if (squareSize != null && squareHeight != null) Modifier.size(squareSize, squareHeight) else Modifier.size(tileSize))
            .clip(shape)
            .background(Brush.linearGradient(flashedColors))
            .then(
                // The style's pattern, on its own fills only: never on the gold, nor while it flashes.
                if (!disabled && !highlighted && flash == 0f) {
                    Modifier.drawBehind {
                        with(boxStyle) { drawTexture(paint, seed = category.ordinal, lit = !scored, cornerPx = cornerRadius.toPx()) }
                    }
                } else {
                    Modifier
                },
            )
            .then(
                if (category == ScoreCategory.COLOURED_HOUSE) {
                    Modifier.drawBehind { drawColourStripes(alpha = if (scored) SCORED_STRIPE_ALPHA else 1f, irish = irishTricolour) }
                } else {
                    Modifier
                },
            )
            .then(
                when {
                    disabled -> Modifier.drawBehind { drawDashedOutline() }
                    highlighted -> Modifier
                    outlineColor != null -> Modifier.border(width = 2.dp, color = outlineColor, shape = shape)
                    boxStyle.rimWidth > 0.dp -> Modifier.border(width = boxStyle.rimWidth, color = paint.border, shape = shape)
                    else -> Modifier
                },
            )
            .then(if (onClick != null) Modifier.clickable(interactionSource = interactionSource, indication = LocalIndication.current, role = Role.Button, onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        // A style's moving art (RGB's lights, Neon's pulse) - on an open box only: the gold, a scored box and a disabled one
        // don't light up.
        if (!disabled && !highlighted && !scored && flash == 0f) MovingBoxArt(boxStyle, paint, tone, category.ordinal, cornerRadius)
        CompositionLocalProvider(LocalBoxInk provides ink, LocalBoxMarkFont provides boxStyle.markFont) {
            CategoryIcon(
                category = category,
                color = flashedIconColor,
                modifier = Modifier.fillMaxSize(),
                labelFontSize = when {
                    squareSize != null -> 36.sp
                    wide -> if (compact) 20.sp else 24.sp
                    compact -> 16.sp
                    else -> 18.sp
                },
                dimmed = scored || disabled,
                fiveOfAKindBonusCount = fiveOfAKindBonusCount,
                target = target,
                matches = matches,
            )
        }
        if (disabled) Box(modifier = Modifier.matchParentSize().drawBehind { drawSlash() })
        if (highlighted) GlowBorder(cornerRadius, shape)
    }
}

private const val FLASH_TRANSITION_MILLIS = 400

/** The ink a box's glyph is drawn in on its fill, at full strength - for a part of the glyph that keeps its own colour (a Hit List place). */
internal val LocalBoxInk = staticCompositionLocalOf { TileIconColor }

/** The typeface of a box's text marks, from the box style - null for the brand font. */
internal val LocalBoxMarkFont = staticCompositionLocalOf<FontFamily?> { null }

/** How faint a disabled tile's glyph, dashes and slash are - the glyph stays readable, but unmistakably not in play. */
private const val DISABLED_ICON_ALPHA = 0.3f

/** The dashed outline of a disabled tile, inside the tile's own edge - the one look with no fill and a broken line. */
private fun DrawScope.drawDashedOutline() {
    val stroke = DISABLED_STROKE.toPx()
    val inset = stroke / 2
    val radius = DISABLED_CORNER_RADIUS_FRACTION * minOf(size.width, size.height)
    drawRoundRect(
        color = TileIconColor.copy(alpha = DISABLED_OUTLINE_ALPHA),
        topLeft = Offset(inset, inset),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(radius),
        style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH_ON.toPx(), DASH_OFF.toPx()))),
    )
}

/** A diagonal slash through the middle of a disabled tile, as long as the tile is high however wide it is. */
private fun DrawScope.drawSlash() {
    val half = minOf(size.width, size.height) * SLASH_HALF_FRACTION
    val centre = Offset(size.width / 2, size.height / 2)
    drawLine(
        color = TileIconColor.copy(alpha = DISABLED_OUTLINE_ALPHA),
        start = Offset(centre.x - half, centre.y + half),
        end = Offset(centre.x + half, centre.y - half),
        strokeWidth = DISABLED_STROKE.toPx(),
        cap = StrokeCap.Round,
    )
}

private val DISABLED_STROKE = 2.dp
private val DASH_ON = 5.dp
private val DASH_OFF = 4.dp
private const val DISABLED_OUTLINE_ALPHA = 0.5f
private const val SLASH_HALF_FRACTION = 0.34f
private const val DISABLED_CORNER_RADIUS_FRACTION = 0.2f

/**
 * A highlighted tile's gold border, pulsing. Only composed on a highlighted tile, and its alpha is
 * applied by [PulsingArt] rather than read while composing, so the pulse is a repaint of this
 * border alone: a glow clock on every tile, read into the border colour, used to recompose the
 * whole grid every frame for the entire game, highlighted or not.
 */
@Composable
private fun BoxScope.GlowBorder(cornerRadius: Dp, shape: Shape) {
    // At the Low and Off animation levels (and under reduced motion), just a plain gold border at full strength -
    // no pulse, and no layer for one: still the gold that marks a good pick.
    if (!scorePulse) {
        Box(modifier = Modifier.matchParentSize().border(width = GLOW_WIDTH, color = GoldAccent, shape = shape))
        return
    }
    val glowAlpha = rememberInfiniteTransition(label = "tileGlow").animateFloat( // i18n: not translated - an animation label, not shown
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(900, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "tileGlowAlpha", // i18n: not translated - an animation label, not shown
    )
    if (shape is RoundedCornerShape) {
        PulsingArt(alpha = glowAlpha, modifier = Modifier.matchParentSize()) { alpha -> drawGlowRing(cornerRadius, alpha) }
    } else {
        // A box style's own outline (Pixel's stepped corners): made once for the tile's size, not on every pulse.
        val outline = remember(shape) { OutlineCache(shape) }
        PulsingArt(alpha = glowAlpha, modifier = Modifier.matchParentSize()) { alpha ->
            // Twice as wide, centred on the edge: the tile's clip leaves the inner half, as the steady border draws it.
            drawOutline(outline.of(size, layoutDirection, this), GoldAccent, alpha = alpha, style = Stroke(ceil(GLOW_WIDTH.toPx()) * 2))
        }
    }
}

/** [shape]'s outline at the last size asked for, kept until the size changes. */
private class OutlineCache(private val shape: Shape) {
    private var size: Size? = null
    private var outline: Outline? = null

    fun of(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        outline?.takeIf { size == this.size } ?: shape.createOutline(size, layoutDirection, density).also {
            this.size = size
            outline = it
        }
}

private val GLOW_WIDTH = 2.dp

/** The ring of a tile's glow at [alpha], just inside the tile's edge: where `Modifier.border` draws the steady one. */
private fun DrawScope.drawGlowRing(cornerRadius: Dp, alpha: Float) {
    val width = ceil(GLOW_WIDTH.toPx())
    val inset = width / 2
    drawRoundRect(
        color = GoldAccent,
        topLeft = Offset(inset, inset),
        size = Size(size.width - width, size.height - width),
        cornerRadius = CornerRadius(cornerRadius.toPx() - inset),
        alpha = alpha,
        style = Stroke(width),
    )
}

/**
 * The Coloured House tile's background: red, yellow and blue in three equal diagonal stripes, top-left
 * to bottom-right - the yellow band fills the tile first, then the red and blue corners go over it.
 */
private fun DrawScope.drawColourStripes(alpha: Float, irish: Boolean) {
    val leg = STRIPE_CORNER_LEG
    drawRect(color = DieColour.YELLOW.palette(irish).stripe, alpha = alpha)
    drawPath(
        path = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width * leg, 0f)
            lineTo(0f, size.height * leg)
            close()
        },
        color = DieColour.RED.palette(irish).stripe,
        alpha = alpha,
    )
    drawPath(
        path = Path().apply {
            moveTo(size.width, size.height)
            lineTo(size.width * (1 - leg), size.height)
            lineTo(size.width, size.height * (1 - leg))
            close()
        },
        color = DieColour.BLUE.palette(irish).stripe,
        alpha = alpha,
    )
}
