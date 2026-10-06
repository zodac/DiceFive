package net.zodac.dicefive.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.House
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import net.zodac.dicefive.model.PlayerState
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.resources.Res
import net.zodac.dicefive.resources.ic_stairs
import net.zodac.dicefive.ui.game.style.LocalIrishTricolour
import net.zodac.dicefive.ui.game.style.PipFace
import net.zodac.dicefive.ui.game.style.palette
import net.zodac.dicefive.ui.theme.GoldAccent
import net.zodac.dicefive.ui.theme.TileIconColor
import org.jetbrains.compose.resources.painterResource

/**
 * The small glyph shown inside a [CategoryTile]: dice pips for the upper section, and a bespoke
 * mark (Nx badge, house, staircase, "?") for each lower-section category, and a flat colour square
 * (or, for Coloured House, the house again) for each colour-section category.
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
    // Extra 5x beyond the first (see PlayerState.fiveOfAKindBonusCount) - only ever nonzero for
    // ScoreCategory.FIVE_OF_A_KIND, so a corner badge can show the total count once there's more than one.
    fiveOfAKindBonusCount: Int = 0,
    // The bonus those extra 5x are worth, written at the tile's right end - only for the wide 5x tile, which has the room.
    fiveOfAKindBonusAmount: Int = 0,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        when (category) {
            in PlayerState.UPPER_CATEGORIES -> {
                val pipValue = PlayerState.UPPER_CATEGORIES.indexOf(category) + 1
                PipFace(value = pipValue, color = color, modifier = Modifier.fillMaxSize().padding(6.dp))
            }

            ScoreCategory.THREE_OF_A_KIND -> BadgeLabel("3x", color, labelFontSize)
            ScoreCategory.FOUR_OF_A_KIND -> BadgeLabel("4x", color, labelFontSize)
            ScoreCategory.FIVE_OF_A_KIND -> FiveOfAKindIcon(color, labelFontSize, fiveOfAKindBonusCount, fiveOfAKindBonusAmount)
            ScoreCategory.CHANCE -> BadgeLabel("?", color, labelFontSize)
            // A stock glyph rather than a hand-drawn one (a previous roof/body Canvas silhouette
            // read as too tall for the tile). The tile itself is always square (CategoryTile sizes
            // it with a single dp value for both dimensions), so laying this out with fillMaxSize
            // renders it within that same square automatically, with no separate aspect tuning.
            ScoreCategory.FULL_HOUSE -> HouseIcon(color)
            // The same house, over CategoryTile's red/yellow/blue stripes rather than plain teal -
            // with a soft shadow under it, since white or gold alone washes out on the yellow band.
            ScoreCategory.COLOURED_HOUSE -> HouseIcon(color, shadowed = true)
            // Nothing but the colour itself: the box is about the colour, and a flat square of it
            // says so at a glance. Fades like every other glyph once scored.
            ScoreCategory.REDS, ScoreCategory.YELLOWS, ScoreCategory.BLUES -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp)
                    .alpha(if (dimmed) 0.4f else 1f)
                    .background(requireNotNull(category.matchingColour).palette(LocalIrishTricolour.current).swatch),
            )
            // Material has one Stairs glyph, not a short-flight/long-flight pair, so both
            // categories render the same icon - a corner badge carries the run length (4 vs 5)
            // that used to come from the hand-drawn version's step count.
            ScoreCategory.SMALL_STRAIGHT -> StairsWithRunBadge(color, runLength = 4, dimmed = dimmed)
            ScoreCategory.LARGE_STRAIGHT -> StairsWithRunBadge(color, runLength = 5, dimmed = dimmed)
            // Text badges like 3x and ?, since there's no picture that says "two pairs" or "evens" better than
            // the words themselves. The spoken name is on the tile (see BoardSemantics).
            ScoreCategory.TWO_PAIR -> BadgeLabel("2+2", color, labelFontSize)
            ScoreCategory.EVENS -> BadgeLabel("Ev", color, labelFontSize)
            ScoreCategory.ODDS -> BadgeLabel("Od", color, labelFontSize)
            else -> Unit
        }
    }
}

// Both corner badges (run-length and 5x count) size and inset themselves as a fraction of
// their OWN tile's rendered width, not a fixed dp value - a fixed 2dp/14dp reads as generous
// clearance on a 48dp regular tile but sits nearly flush with the edge on a bigger
// tile, since the same absolute gap is proportionally much smaller there. Fractions are
// of the REGULAR_TILE_SIZE case (see CategoryTile.kt) - the original hand-tuned 2dp inset / 14dp
// badge size on a 48dp tile - so both badges keep the same relative position and weight at any
// tile size.
/** The most of a tile's height the 5x count badge is sized from - on the large square 5x tile it would otherwise be huge. */
private val LARGE_TILE_BADGE_BASIS = 76.dp
private const val BADGE_EDGE_INSET_FRACTION = 2f / 48f
private const val BADGE_SIZE_FRACTION = 14f / 48f

@Composable
private fun HouseIcon(color: Color, shadowed: Boolean = false) {
    if (shadowed) {
        Icon(
            imageVector = Icons.Filled.House,
            contentDescription = null,
            tint = Color.Black.copy(alpha = 0.45f),
            modifier = Modifier.fillMaxSize().padding(8.dp).offset(x = 1.dp, y = 1.dp),
        )
    }
    Icon(
        imageVector = Icons.Filled.House,
        contentDescription = null,
        tint = color,
        modifier = Modifier.fillMaxSize().padding(8.dp),
    )
}

@Composable
private fun BadgeLabel(text: String, color: Color, fontSize: TextUnit, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = color,
        style = TextStyle(fontWeight = FontWeight.Black, fontSize = fontSize, textAlign = TextAlign.Center),
        modifier = modifier,
    )
}

/**
 * The "5x" badge, plus - once a player has rolled more than one 5x this game - a small corner
 * badge showing the total count. Unlike the straight run-length badge, this one can safely share
 * [color] with the main glyph: it only ever appears once the box is already scored (the bonus
 * requires a genuine 50 already on the card - see ScoreCalculator.awardsFiveOfAKindBonus), a stable
 * state for the rest of the game, not one that flips between two categories independently the way
 * highlighted/scored does for Small vs Large Straight.
 */
@Composable
private fun FiveOfAKindIcon(color: Color, fontSize: TextUnit, bonusCount: Int, bonusAmount: Int) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        BadgeLabel("5x", color, fontSize, modifier = Modifier.align(Alignment.Center))
        if (bonusAmount > 0) {
            Text(
                text = "+$bonusAmount",
                color = GoldAccent,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 10.dp),
            )
        }
        if (bonusCount > 0) {
            // Of the tile's height, which the wide 5x tile shares with a square one.
            val basis = minOf(maxHeight, LARGE_TILE_BADGE_BASIS)
            val inset = basis * BADGE_EDGE_INSET_FRACTION
            val badgeSize = basis * BADGE_SIZE_FRACTION
            SegmentBadge(
                // Total 5x, not just the bonus count: the first one (the 50 itself) counts
                // too.
                count = bonusCount + 1,
                color = color,
                // Top-left, matching the Small/Large Straight run-length badge's position (see
                // StairsWithRunBadge) - same proportional clearance off the tile edge.
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = inset, start = inset)
                    .size(badgeSize),
            )
        }
    }
}

/**
 * The stairs icon itself still dims/glows with [color] like every other tile (scored,
 * highlighted). The run-length badge is different: its color is fixed (always [color] at full
 * strength, only ever faded via [dimmed]) rather than driven by whatever [color] the icon itself
 * currently has - tying it directly to that would let Small and Large Straight show different
 * badge colors at the same moment purely because the current dice happen to qualify one but not
 * the other, which read as the two categories having inconsistent, unrelated badge styling rather
 * than one shared design. It still needs to fade when scored, like the rest of the icon - [dimmed]
 * drives that alpha, independently of [color], so it fades on "scored" but not on "highlighted".
 */
@Composable
private fun StairsWithRunBadge(color: Color, runLength: Int, dimmed: Boolean) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        Icon(
            // A plain custom shape (ic_stairs.xml), not Icons.Filled/Outlined.Stairs: both of
            // Material's variants come from its wayfinding/signage icon family and draw the
            // pictogram framed inside its own square outline (mimicking real accessibility
            // signage) - that frame showed up as a stray border around the icon once placed
            // inside this app's own tile chrome, in either fill style. This shape has no frame,
            // so - same padding as the House icon above - it fills most of the tile instead.
            painter = painterResource(Res.drawable.ic_stairs),
            contentDescription = null,
            tint = color,
            modifier = Modifier.fillMaxSize().padding(8.dp),
        )
        SegmentBadge(
            count = runLength,
            color = TileIconColor,
            modifier = Modifier
                .align(Alignment.TopStart)
                // Clears the tile's own edge/border - without it the badge's ring sat flush
                // against the tile boundary with no breathing room. This is a REGULAR (48dp)
                // tile, so this is exactly the reference 2dp/14dp the proportional fractions above
                // were derived from.
                .padding(top = maxWidth * BADGE_EDGE_INSET_FRACTION, start = maxWidth * BADGE_EDGE_INSET_FRACTION)
                .size(maxWidth * BADGE_SIZE_FRACTION)
                .alpha(if (dimmed) 0.4f else 1f),
        )
    }
}
