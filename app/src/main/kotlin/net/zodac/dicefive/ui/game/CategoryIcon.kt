package net.zodac.dicefive.ui.game

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.House
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import net.zodac.dicefive.R
import net.zodac.dicefive.model.PlayerState
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.ui.game.style.PipFace

/**
 * The small glyph shown inside a [CategoryTile]: dice pips for the upper section, and a bespoke
 * mark (Nx badge, house, staircase, "?") for each lower-section category.
 */
@Composable
fun CategoryIcon(
    category: ScoreCategory,
    color: Color,
    modifier: Modifier = Modifier,
    labelFontSize: TextUnit = TextUnit.Unspecified,
    // Only consulted by the run-length badge (Small/Large Straight): whether to fade it in step
    // with the rest of a scored icon. Separate from [color] so the badge can follow the
    // scored-dim state without also following the highlighted-gold state - see StairsWithRunBadge.
    dimmed: Boolean = false,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        when (category) {
            in PlayerState.UPPER_CATEGORIES -> {
                val pipValue = PlayerState.UPPER_CATEGORIES.indexOf(category) + 1
                PipFace(value = pipValue, color = color, modifier = Modifier.fillMaxSize().padding(6.dp))
            }

            ScoreCategory.THREE_OF_A_KIND -> BadgeLabel("3x", color, labelFontSize)
            ScoreCategory.FOUR_OF_A_KIND -> BadgeLabel("4x", color, labelFontSize)
            ScoreCategory.YAHTZEE -> BadgeLabel("5x", color, labelFontSize)
            ScoreCategory.CHANCE -> BadgeLabel("?", color, labelFontSize)
            // A stock glyph rather than a hand-drawn one (a previous roof/body Canvas silhouette
            // read as too tall for the tile). The tile itself is always square (CategoryTile sizes
            // it with a single dp value for both dimensions), so laying this out with fillMaxSize
            // renders it within that same square automatically, with no separate aspect tuning.
            ScoreCategory.FULL_HOUSE -> Icon(
                imageVector = Icons.Filled.House,
                contentDescription = null,
                tint = color,
                modifier = Modifier.fillMaxSize().padding(8.dp),
            )
            // Material has one Stairs glyph, not a short-flight/long-flight pair, so both
            // categories render the same icon - a corner badge carries the run length (4 vs 5)
            // that used to come from the hand-drawn version's step count.
            ScoreCategory.SMALL_STRAIGHT -> StairsWithRunBadge(color, runLength = 4, dimmed = dimmed)
            ScoreCategory.LARGE_STRAIGHT -> StairsWithRunBadge(color, runLength = 5, dimmed = dimmed)
            else -> Unit
        }
    }
}

@Composable
private fun BadgeLabel(text: String, color: Color, fontSize: TextUnit) {
    Text(
        text = text,
        color = color,
        style = TextStyle(fontWeight = FontWeight.Black, fontSize = fontSize, textAlign = TextAlign.Center),
    )
}

/**
 * The stairs icon itself still dims/glows with [color] like every other tile (scored,
 * highlighted). The run-length badge is different in two ways:
 * - Its colors are fixed, baked into `ic_run_badge_4`/`_5` rather than driven by [color]: tying it
 *   to [color] meant Small and Large Straight could show different badge colors at the same
 *   moment purely because the current dice happen to qualify one but not the other, which read as
 *   the two categories having inconsistent, unrelated badge styling rather than one shared design.
 * - It's a flattened vector asset, not a Compose `Text` in a circle: a single digit at 8sp inside
 *   a 14dp circle inherited an ambient line-height much taller than the glyph itself, so it never
 *   sat centered in the circle - a font-metrics problem a real graphic doesn't have, and one that
 *   would only get worse under a user's larger system font-scale setting.
 * It still needs to fade when scored, like the rest of the icon - [dimmed] drives that alpha,
 * independently of [color], so it fades on "scored" but not on "highlighted".
 */
@Composable
private fun StairsWithRunBadge(color: Color, runLength: Int, dimmed: Boolean) {
    val badgeRes = if (runLength == 4) R.drawable.ic_run_badge_4 else R.drawable.ic_run_badge_5
    Box(modifier = Modifier.fillMaxSize()) {
        Icon(
            // A plain custom shape (ic_stairs.xml), not Icons.Filled/Outlined.Stairs: both of
            // Material's variants come from its wayfinding/signage icon family and draw the
            // pictogram framed inside its own square outline (mimicking real accessibility
            // signage) - that frame showed up as a stray border around the icon once placed
            // inside this app's own tile chrome, in either fill style. This shape has no frame,
            // so - same padding as the House icon above - it fills most of the tile instead.
            painter = painterResource(R.drawable.ic_stairs),
            contentDescription = null,
            tint = color,
            modifier = Modifier.fillMaxSize().padding(8.dp),
        )
        Icon(
            painter = painterResource(badgeRes),
            contentDescription = null,
            // Unspecified, not `color`: preserves the asset's own two-tone circle+digit colors
            // instead of Icon flattening them to a single tint.
            tint = Color.Unspecified,
            modifier = Modifier
                .align(Alignment.TopStart)
                // Clears the tile's own edge/border - without it the badge's ring sat flush
                // against the tile boundary with no breathing room.
                .padding(top = 2.dp, start = 2.dp)
                .size(14.dp)
                .alpha(if (dimmed) 0.4f else 1f),
        )
    }
}
