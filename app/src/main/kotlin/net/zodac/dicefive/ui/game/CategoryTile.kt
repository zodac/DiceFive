package net.zodac.dicefive.ui.game

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.ui.theme.GoldAccent
import net.zodac.dicefive.ui.theme.TileHighlightBottom
import net.zodac.dicefive.ui.theme.TileHighlightTop
import net.zodac.dicefive.ui.theme.TileIconColor
import net.zodac.dicefive.ui.theme.TileScoredBorder
import net.zodac.dicefive.ui.theme.TileScoredBottom
import net.zodac.dicefive.ui.theme.TileScoredTop
import net.zodac.dicefive.ui.theme.TileTealBorder
import net.zodac.dicefive.ui.theme.TileTealBottom
import net.zodac.dicefive.ui.theme.TileTealTop

/** Fixed intrinsic sizes - deliberately NOT derived from ambient row height/aspectRatio, which
 * blew up unpredictably (the Yahtzee tile filling the screen, the grid starving its score text). */
private val REGULAR_TILE_SIZE = 48.dp
private val PROMINENT_TILE_SIZE = 76.dp

/**
 * One scoring-category slot in the grid: an icon tile that glows gold and pulses when the
 * player's current dice can legally score it, or reads as visibly "spent" once [scored].
 */
@Composable
fun CategoryTile(
    category: ScoreCategory,
    highlighted: Boolean,
    modifier: Modifier = Modifier,
    prominent: Boolean = false,
    scored: Boolean = false,
    yahtzeeBonusCount: Int = 0,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(if (prominent) 16.dp else 10.dp)
    val tileSize = if (prominent) PROMINENT_TILE_SIZE else REGULAR_TILE_SIZE
    val infiniteTransition = rememberInfiniteTransition(label = "tileGlow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(900, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "tileGlowAlpha",
    )

    // Three distinct looks, never overlapping in practice (a scored category is never a legal,
    // highlightable choice): gold glow for "score this now", flat grey for "already used", teal
    // otherwise. Without the grey state a scored tile looked identical to an ordinary open one -
    // only the (small, low-contrast) score text beside it showed anything had happened.
    val backgroundColors = when {
        highlighted -> listOf(TileHighlightTop, TileHighlightBottom)
        scored -> listOf(TileScoredTop, TileScoredBottom)
        else -> listOf(TileTealTop, TileTealBottom)
    }
    val borderColor = when {
        highlighted -> GoldAccent.copy(alpha = glowAlpha)
        scored -> TileScoredBorder
        else -> TileTealBorder
    }
    val iconColor = when {
        highlighted -> GoldAccent
        scored -> TileIconColor.copy(alpha = 0.4f)
        else -> TileIconColor
    }

    Box(
        modifier = modifier
            .size(tileSize)
            .clip(shape)
            .background(Brush.linearGradient(backgroundColors))
            .border(width = if (highlighted) 2.dp else 1.dp, color = borderColor, shape = shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        CategoryIcon(
            category = category,
            color = iconColor,
            modifier = Modifier.fillMaxSize(),
            labelFontSize = if (prominent) 30.sp else 18.sp,
            dimmed = scored,
            yahtzeeBonusCount = yahtzeeBonusCount,
        )
    }
}
