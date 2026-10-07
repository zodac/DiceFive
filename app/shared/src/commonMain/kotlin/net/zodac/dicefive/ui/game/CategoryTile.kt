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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.zodac.dicefive.model.DieColour
import net.zodac.dicefive.model.HitTarget
import net.zodac.dicefive.model.PlaceMatch
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.ui.common.LocalReduceMotion
import net.zodac.dicefive.ui.common.scorePulse
import net.zodac.dicefive.ui.game.style.LocalIrishTricolour
import net.zodac.dicefive.ui.game.style.palette
import net.zodac.dicefive.ui.theme.GoldAccent
import net.zodac.dicefive.ui.theme.TileHighlightBottom
import net.zodac.dicefive.ui.theme.TileHighlightTop
import net.zodac.dicefive.ui.theme.TileIconColor
import net.zodac.dicefive.ui.theme.TileScoredBorder
import net.zodac.dicefive.ui.theme.TileScoredBottom
import net.zodac.dicefive.ui.theme.TileScoredTop
import net.zodac.dicefive.ui.theme.TileTealBottom
import net.zodac.dicefive.ui.theme.TileTealBorder
import net.zodac.dicefive.ui.theme.TileTealTop

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
    compact: Boolean = false,
    scored: Boolean = false,
    /** Switched off for this game (see [net.zodac.dicefive.model.PlayerState.disabledCategories]): drawn as an empty, dashed outline with a slash through it. */
    disabled: Boolean = false,
    fiveOfAKindBonusCount: Int = 0,
    /** The 5x bonus so far, written on a [wide] tile - 0 for none. */
    fiveOfAKindBonusAmount: Int = 0,
    /** A solid outline in this colour in place of the usual border - the box a player last scored in (see [LastScoredHighlight]). */
    outlineColor: Color? = null,
    /** The target a Hit List target box calls, drawn in place of a glyph - and with [matches], how the dice stand against it. */
    target: HitTarget? = null,
    matches: List<PlaceMatch>? = null,
    onClick: (() -> Unit)? = null,
) {
    val irishTricolour = LocalIrishTricolour.current
    val shape = RoundedCornerShape(if (squareSize != null) 16.dp else 10.dp)
    val tileSize = squareSize ?: if (compact) COMPACT_TILE_SIZE else REGULAR_TILE_SIZE
    // Three distinct looks, never overlapping in practice (a scored category is never a legal,
    // highlightable choice): gold glow for "score this now", flat grey for "already used", teal
    // otherwise. Without the grey state a scored tile looked identical to an ordinary open one -
    // only the (small, low-contrast) score text beside it showed anything had happened.
    val backgroundColors = when {
        disabled -> listOf(Color.Transparent, Color.Transparent)
        highlighted -> listOf(TileHighlightTop, TileHighlightBottom)
        scored -> listOf(TileScoredTop, TileScoredBottom)
        else -> listOf(TileTealTop, TileTealBottom)
    }
    // A highlighted tile's pulsing gold border is drawn by GlowBorder instead, over the content.
    val borderColor = if (scored) TileScoredBorder else TileTealBorder
    val iconColor = when {
        disabled -> TileIconColor.copy(alpha = DISABLED_ICON_ALPHA)
        highlighted -> GoldAccent
        scored -> TileIconColor.copy(alpha = 0.4f)
        else -> TileIconColor
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
            .then(if (wide) Modifier.height(tileSize) else Modifier.size(tileSize))
            .clip(shape)
            .background(Brush.linearGradient(flashedColors))
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
                    else -> Modifier.border(width = 1.dp, color = borderColor, shape = shape)
                },
            )
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
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
            fiveOfAKindBonusAmount = fiveOfAKindBonusAmount,
            target = target,
            matches = matches,
        )
        if (disabled) Box(modifier = Modifier.matchParentSize().drawBehind { drawSlash() })
        if (highlighted) GlowBorder(shape)
    }
}

private const val FLASH_TRANSITION_MILLIS = 400

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
 * applied in a graphics layer rather than read while composing, so the pulse is a repaint of this
 * border alone: a glow clock on every tile, read into the border colour, used to recompose the
 * whole grid every frame for the entire game, highlighted or not.
 */
@Composable
private fun BoxScope.GlowBorder(shape: Shape) {
    // At the Low and Off animation levels (and under reduced motion), just a plain gold border at full strength -
    // no pulse, and no layer for one: still the gold that marks a good pick.
    if (!scorePulse) {
        Box(modifier = Modifier.matchParentSize().border(width = 2.dp, color = GoldAccent, shape = shape))
        return
    }
    val glowAlpha = rememberInfiniteTransition(label = "tileGlow").animateFloat( // i18n: not translated - an animation label, not shown
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(900, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "tileGlowAlpha", // i18n: not translated - an animation label, not shown
    )
    Box(
        modifier = Modifier
            .matchParentSize()
            .graphicsLayer { alpha = glowAlpha.value }
            .border(width = 2.dp, color = GoldAccent, shape = shape),
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
