package net.zodac.dicefive.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import net.zodac.dicefive.model.HitTarget
import net.zodac.dicefive.model.PlaceMatch
import net.zodac.dicefive.model.PlayerState
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.resources.Res
import net.zodac.dicefive.resources.ic_stairs
import net.zodac.dicefive.ui.common.SoraFontFamily
import net.zodac.dicefive.ui.game.style.LocalIrishTricolour
import net.zodac.dicefive.ui.game.style.PipFace
import net.zodac.dicefive.ui.game.style.palette
import net.zodac.dicefive.ui.theme.GoldAccent
import net.zodac.dicefive.ui.theme.TileIconColor
import org.jetbrains.compose.resources.painterResource

/**
 * The small glyph shown inside a [CategoryTile]: dice pips for the upper section, and a bespoke
 * mark (Nx badge, house, staircase, "?") for each lower-section category, and a flat colour square
 * (or, for Coloured House, the house again) for each colour-section category. A Hit List target shows the
 * [target] it calls, and how the dice stand against it ([matches]) - see [TargetIcon].
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
    // The target a Hit List target box calls, and - while there are dice to compare - how each place stands.
    target: HitTarget? = null,
    matches: List<PlaceMatch>? = null,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (target != null) {
            TargetIcon(target, matches, color)
            return@Box
        }
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
            // A word, like the text badges: there's no picture of an alibi. Smaller than 5x's two characters.
            ScoreCategory.ALIBI -> BadgeLabel("Alibi", color, labelFontSize * ALIBI_LABEL_SCALE)
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

/** How much smaller the Alibi's five letters are than 5x's two, so they fit the same tile. */
private const val ALIBI_LABEL_SCALE = 0.6f

/** A target's places as a fraction of its tile's width, and its points line's. */
private const val TARGET_PLACE_TEXT_FRACTION = 0.25f
private const val TARGET_POINTS_TEXT_FRACTION = 0.2f
private const val TARGET_PLACE_WIDTH_FRACTION = 0.165f
private const val ANY_PLACE_ALPHA = 0.45f

/** How faint a number the dice don't show is, while they're being compared - below a rolled one's full white. */
private const val MISSING_PLACE_ALPHA = 0.55f
private const val TARGET_POINTS_ALPHA = 0.75f

/** The bar under a matched place: how much of the place's width it spans, how thick it is, and its gap above. */
private const val MATCH_BAR_WIDTH_FRACTION = 0.7f
private val MATCH_BAR_HEIGHT = 1.5.dp
private val MATCH_BAR_GAP = 1.dp

/**
 * A Hit List target: its five places in a row, left to right - a number, or a dot for any die - over its points.
 * Sized from the tile, not the system font: it's part of the tile's art, five places in a fixed width, and the box's
 * spoken name says it in full (see `CategoryCell`).
 *
 * With [matches], each named place shows how the dice stand against it: a short bar under it once a die shows its
 * number, and the number and bar gold once that die is in its place, so how close the dice are can be seen at a
 * glance. A bar of its own under each place, not a text underline, which runs neighbouring places together. A number
 * the dice don't show is faint white, never the tile's own colour: that's gold on a lit tile, the colour of in place.
 */
@Composable
private fun TargetIcon(target: HitTarget, matches: List<PlaceMatch>?, color: Color) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val density = LocalDensity.current
        val placeSize = with(density) { (maxWidth * TARGET_PLACE_TEXT_FRACTION).toSp() }
        val pointsSize = with(density) { (maxWidth * TARGET_POINTS_TEXT_FRACTION).toSp() }
        val placeWidth = maxWidth * TARGET_PLACE_WIDTH_FRACTION
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row {
                target.places.forEachIndexed { index, value ->
                    val match = matches?.getOrNull(index)
                    val barColor = when (match) {
                        PlaceMatch.IN_PLACE -> GoldAccent
                        PlaceMatch.ROLLED -> TileIconColor
                        else -> null
                    }
                    Text(
                        text = value?.toString() ?: "\u00B7",
                        color = when {
                            value == null -> color.copy(alpha = color.alpha * ANY_PLACE_ALPHA)
                            match == PlaceMatch.IN_PLACE -> GoldAccent
                            match == PlaceMatch.ROLLED -> TileIconColor
                            // Not the tile's own colour, which is gold on a lit tile - a number the dice don't show
                            // would read as one in place.
                            match == PlaceMatch.MISSING -> TileIconColor.copy(alpha = MISSING_PLACE_ALPHA)
                            else -> color
                        },
                        style = TextStyle(fontFamily = SoraFontFamily, fontWeight = FontWeight.Bold, fontSize = placeSize, lineHeight = placeSize, textAlign = TextAlign.Center),
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier
                            .width(placeWidth)
                            .padding(bottom = MATCH_BAR_HEIGHT + MATCH_BAR_GAP)
                            .drawBehind {
                                if (barColor != null) {
                                    val barWidth = size.width * MATCH_BAR_WIDTH_FRACTION
                                    drawRect(
                                        color = barColor,
                                        topLeft = Offset((size.width - barWidth) / 2, size.height + MATCH_BAR_GAP.toPx()),
                                        size = Size(barWidth, MATCH_BAR_HEIGHT.toPx()),
                                    )
                                }
                            },
                    )
                }
            }
            Text(
                text = target.points.toString(),
                color = color.copy(alpha = color.alpha * TARGET_POINTS_ALPHA),
                style = TextStyle(fontFamily = SoraFontFamily, fontWeight = FontWeight.Bold, fontSize = pointsSize, lineHeight = pointsSize, textAlign = TextAlign.Center),
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

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
        style = TextStyle(fontFamily = SoraFontFamily, fontWeight = FontWeight.Bold, fontSize = fontSize, textAlign = TextAlign.Center),
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
