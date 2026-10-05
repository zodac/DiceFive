package net.zodac.dicefive.ui.game

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.zodac.dicefive.game.ScoreCalculator
import net.zodac.dicefive.model.Die
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.PlayerState
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.model.ScoreSection
import net.zodac.dicefive.ui.game.style.LocalIrishTricolour
import net.zodac.dicefive.ui.theme.GoldAccent
import net.zodac.dicefive.ui.theme.TileIconColor

/** Standard's grid: six rows, upper section beside lower. More than this and the tiles go compact. */
private const val REGULAR_GRID_ROWS = 6
private val GRID_ROW_SPACING = 6.dp

/** The board's height for a Standard-sized grid - see [scoreBoardHeight]. */
internal val REGULAR_BOARD_HEIGHT = 380.dp

/**
 * The grid's rows for [gameMode], top to bottom, each one or two categories wide: the upper section
 * down the left column beside the lower section, then anything left over (Tricolour's colour boxes)
 * two to a row underneath. 5x is left out - it has its own prominent tile beside the cup.
 */
internal fun scoreGridRows(gameMode: GameMode): List<List<ScoreCategory>> {
    val gridCategories = gameMode.categories.filter { it != ScoreCategory.FIVE_OF_A_KIND }
    val upper = gridCategories.filter { it.section == ScoreSection.UPPER }
    val others = gridCategories - upper.toSet()
    val sideBySide = upper.zip(others) { left, right -> listOf(left, right) }
    val leftOver = upper.drop(others.size) + others.drop(upper.size)
    return sideBySide + leftOver.chunked(2)
}

/**
 * How tall the scoring area is for [gameMode]'s grid. Standard's six rows keep the board's original
 * height; more rows than that switch to compact tiles (see [CategoryTile]) and grow the board just
 * enough to fit one per row: [COMPACT_TILE_SIZE] plus the row gap each, inside [padding] top and
 * bottom. At Tricolour's eight rows that's 396dp, only 16dp taller than Standard.
 */
internal fun scoreBoardHeight(gameMode: GameMode, padding: Dp): Dp {
    val rows = scoreGridRows(gameMode).size
    if (rows <= REGULAR_GRID_ROWS) return REGULAR_BOARD_HEIGHT
    return maxOf(REGULAR_BOARD_HEIGHT, (COMPACT_TILE_SIZE + GRID_ROW_SPACING) * rows + padding * 2)
}

/** The grid's tile size for [rowCount] rows - regular for Standard's six, compact beyond that. */
private fun gridTileSize(rowCount: Int): Dp = if (rowCount > REGULAR_GRID_ROWS) COMPACT_TILE_SIZE else REGULAR_TILE_SIZE

/**
 * How far below the top of [gameMode]'s [gridHeight]-tall grid its first row's tiles sit: the rows
 * share the height equally and centre their tile vertically in it. The 5x tile beside the grid uses
 * this to put its top level with Ones and 3x in every mode, rather than a hand-tuned nudge that would
 * only suit one mode's row count and tile size.
 */
internal fun firstRowTileInset(gameMode: GameMode, gridHeight: Dp): Dp {
    val rows = scoreGridRows(gameMode).size
    val rowHeight = (gridHeight - GRID_ROW_SPACING * (rows - 1)) / rows
    return ((rowHeight - gridTileSize(rows)) / 2).coerceAtLeast(0.dp)
}

/**
 * The two-column scorecard grid for the active player only - other players' progress is
 * summarized in the header tabs instead, matching the reference layout. [canScore] and
 * [available] are precomputed once by the caller and shared with the 5x tile beside the cup.
 * Which boxes it shows, and how they're laid out, come from [gameMode] (see [scoreGridRows]).
 */
@Composable
fun ScoreGrid(
    gameMode: GameMode,
    player: PlayerState?,
    dice: List<Die>,
    canScore: Boolean,
    showPreview: Boolean,
    available: Set<ScoreCategory>,
    onScoreCategory: (ScoreCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = scoreGridRows(gameMode)
    val compact = gridTileSize(rows.size) == COMPACT_TILE_SIZE
    Column(modifier = modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(GRID_ROW_SPACING)) {
        for (row in rows) {
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                // Gives the first column's score text (up to 2 digits) clearance before the
                // second column's tile starts - otherwise they visually touch/overlap.
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                for (category in row) {
                    CategoryCell(
                        category = category,
                        player = player,
                        canScore = canScore,
                        showPreview = showPreview,
                        available = available,
                        dice = dice,
                        onScoreCategory = onScoreCategory,
                        compact = compact,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
                // A lone category on the last row keeps to the left column's width.
                if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
internal fun CategoryCell(
    category: ScoreCategory,
    player: PlayerState?,
    canScore: Boolean,
    showPreview: Boolean,
    available: Set<ScoreCategory>,
    dice: List<Die>,
    onScoreCategory: (ScoreCategory) -> Unit,
    modifier: Modifier = Modifier,
    prominent: Boolean = false,
    compact: Boolean = false,
) {
    val scores = player?.scoresIn(category).orEmpty()
    // How many scores the box takes - three in Third Wind, which stacks them (see StackedScores).
    val slotCount = player?.gameMode?.scoresPerCategory ?: 1
    // A one-slot box's score; a box with several slots is "filled" once every one of them is.
    val filled = if (slotCount == 1) scores.firstOrNull() else null
    val boxFull = scores.size >= slotCount
    // Legal-to-tap (canScore, human-only) and legal-to-preview (showPreview, any player whose
    // dice have actually been rolled) are deliberately separate: tapping a box to score it only
    // ever makes sense for the human at the controls, but the gold "worth picking" glow and the
    // number preview are just information about the dice that already landed - an AI's own roll
    // is exactly as previewable as a human's, it's just never the human tapping it in.
    val isLegalChoice = player != null && canScore && category in available
    val canPreview = player != null && showPreview && category in available
    val previewScore = if (canPreview) ScoreCalculator.scoreFor(player, category, dice) else null
    val isGoodChoice = previewScore != null && previewScore > 0
    // Every 5x after the first earns a +100 bonus chip tracked separately from the scorecard
    // entry itself (which stays 50) - see PlayerState.fiveOfAKindBonusCount/Total and
    // ScoreCalculator.awardsFiveOfAKindBonus. Zero for every other category.
    val fiveOfAKindBonusCount = if (category == ScoreCategory.FIVE_OF_A_KIND) player?.fiveOfAKindBonusCount ?: 0 else 0
    // Whether this roll would earn the +100 bonus - unconditional on which category ends up
    // chosen, per the official joker rule (see ScoreCalculator's class doc): a repeat 5x
    // always pays the bonus, it only dictates/restricts which box the roll can go in.
    val bonusThisTurn = player != null && showPreview && ScoreCalculator.awardsFiveOfAKindBonus(player, dice)
    // The 5x box itself is never a "legal choice" again once filled (it's not in `available`,
    // so isGoodChoice above is always false for it) - but a repeat 5x still means the bonus
    // will be earned this turn, so without this, rolling one gave no visual sign anything special
    // was about to happen. This preview only ever shows on the 5x tile - not on whichever
    // category the roll ends up scored in - since the bonus is a 5x-box concept, and showing
    // it a second time on the scoring category tile implied it depended on that specific category,
    // when per the official joker rule it doesn't (see ScoreCalculator's class doc).
    val fiveOfAKindTileBonusPreview = category == ScoreCategory.FIVE_OF_A_KIND && bonusThisTurn
    val pendingBonusAmount = if (category == ScoreCategory.FIVE_OF_A_KIND) {
        (player?.fiveOfAKindBonusTotal ?: 0) + if (fiveOfAKindTileBonusPreview) player.gameMode.fiveOfAKindBonusAmount else 0
    } else {
        0
    }

    // One node for a screen reader - the tile's name and the score beside it together, rather than an
    // unlabelled button and a stray number - replacing the tile's own click with the same action.
    val irish = LocalIrishTricolour.current
    // Only on a read-only scorecard (see ReadOnlyScoreboard), and only ever a filled box - or slot.
    val lastScored = LocalLastScoredHighlight.current?.takeIf { it.category == category && scores.isNotEmpty() }
    val spokenState = when {
        slotCount > 1 -> stackedSpokenState(scores, slotCount, previewScore, if (fiveOfAKindTileBonusPreview || fiveOfAKindBonusCount > 0) pendingBonusAmount else 0, lastScored != null)
        filled != null -> buildString {
            append("Scored $filled")
            if (pendingBonusAmount > 0) append(", plus $pendingBonusAmount bonus")
            if (lastScored != null) append(", last turn's score")
        }
        previewScore != null -> buildString {
            append("Would score $previewScore")
            if (fiveOfAKindTileBonusPreview) append(", plus $pendingBonusAmount bonus")
        }
        else -> "Open"
    }
    Row(
        modifier = modifier.clearAndSetSemantics {
            contentDescription = category.spokenName(irish)
            stateDescription = spokenState
            if (isLegalChoice) {
                role = Role.Button
                onClick(label = "Score") {
                    onScoreCategory(category)
                    true
                }
            }
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CategoryTile(
            category = category,
            // Not `|| fiveOfAKindTileBonusPreview`: the 5x tile is never actually pickable again
            // once scored (it isn't a legal choice), so glowing it like an open, scorable box
            // would be misleading - the +score line below is the preview, the tile's look doesn't
            // change.
            highlighted = isGoodChoice,
            prominent = prominent,
            compact = compact,
            scored = boxFull,
            fiveOfAKindBonusCount = fiveOfAKindBonusCount,
            outlineColor = lastScored?.color,
            onClick = if (isLegalChoice) { { onScoreCategory(category) } } else null,
        )
        if (slotCount > 1) {
            StackedScores(
                scores = scores,
                slotCount = slotCount,
                previewScore = previewScore,
                previewGold = isGoodChoice,
                lastScoredColor = lastScored?.color,
                bonusAmount = if (fiveOfAKindBonusCount > 0 || fiveOfAKindTileBonusPreview) pendingBonusAmount else 0,
                prominent = prominent,
                // No taller than the tile beside it - see StackedScores.
                maxHeight = when {
                    prominent -> PROMINENT_TILE_SIZE
                    compact -> COMPACT_TILE_SIZE
                    else -> REGULAR_TILE_SIZE
                },
                modifier = Modifier.weight(1f),
            )
        } else if (fiveOfAKindBonusCount > 0 || fiveOfAKindTileBonusPreview) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = (filled ?: previewScore ?: 0).toString(),
                    color = when {
                        isGoodChoice -> GoldAccent
                        lastScored != null -> lastScored.color
                        else -> TileIconColor
                    },
                    fontWeight = if (isGoodChoice || lastScored != null) FontWeight.Bold else FontWeight.Normal,
                    style = if (prominent) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Visible,
                    softWrap = false,
                )
                Text(
                    // The total bonus on the 5x tile, not one line per extra 5x - ten of
                    // them is still just one "+900" line, not ten "+100"s.
                    text = "+$pendingBonusAmount",
                    color = GoldAccent,
                    fontWeight = FontWeight.Bold,
                    // Smaller than the 5x tile's own bonus line for a regular (non-prominent)
                    // category cell - those rows are much shorter, with far less vertical room to
                    // spare for a second line than the big prominent 5x tile has.
                    style = if (prominent) MaterialTheme.typography.bodySmall else MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Visible,
                    softWrap = false,
                )
            }
        } else {
            val style = if (prominent) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium
            // The score or preview on show - or, while there isn't one, the last one that was, kept
            // laid out but hidden behind the "-". Every cell's preview hides the moment the cup is
            // tapped for a reroll, and laying all their text out again (a number to "-", bold to
            // plain) on that one frame made it the heaviest of the roll; now it's just a swap of
            // which is visible. The number is laid out again only when it changes, as the dice
            // settle. Screen readers hear the cell's stateDescription, never these.
            val shown = (filled ?: previewScore)?.let { ShownScore(it.toString(), gold = isGoodChoice, scored = filled != null, accent = lastScored?.color) }
            val lastShown = remember { arrayOfNulls<ShownScore>(1) }
            if (shown != null) lastShown[0] = shown
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                lastShown[0]?.let { number ->
                    ScoreText(
                        text = number.text,
                        color = when {
                            number.gold -> GoldAccent
                            number.accent != null -> number.accent
                            else -> TileIconColor.copy(alpha = if (number.scored) 1f else 0.55f)
                        },
                        fontWeight = if (number.gold || number.accent != null) FontWeight.Bold else FontWeight.Normal,
                        style = style,
                        modifier = Modifier.alpha(if (shown != null) 1f else 0f),
                    )
                }
                ScoreText(
                    text = "-",
                    color = TileIconColor.copy(alpha = 0.55f),
                    fontWeight = FontWeight.Normal,
                    style = style,
                    modifier = Modifier.alpha(if (shown == null) 1f else 0f),
                )
            }
        }
    }
}

/**
 * What a screen reader hears for a box with [slotCount] slots: what's in each filled one, what the
 * dice would score in the next, and how many are left open - "Scored 12, 9, would score 15", "Scored
 * 12, 2 open" - then any 5x bonus and whether its last score was the player's last turn.
 */
internal fun stackedSpokenState(scores: List<Int>, slotCount: Int, previewScore: Int?, bonusAmount: Int, lastScored: Boolean): String {
    val stillOpen = slotCount - scores.size - if (previewScore != null) 1 else 0
    val parts = listOfNotNull(
        scores.takeIf { it.isNotEmpty() }?.joinToString(", ", prefix = "Scored "),
        previewScore?.let { "would score $it" },
        when {
            stillOpen <= 0 -> null
            previewScore != null -> "$stillOpen more open"
            else -> "$stillOpen open"
        },
        bonusAmount.takeIf { it > 0 }?.let { "plus $it bonus" },
        scores.lastOrNull()?.takeIf { lastScored }?.let { "last turn's score $it" },
    )
    return parts.joinToString(", ").replaceFirstChar { it.uppercase() }
}

/**
 * A box's scores, one line per slot, top to bottom in the order they're filled: each filled slot's
 * score, the dice's preview in the next open one (gold when it's worth picking), and a "-" in the
 * rest - with the 5x tile's bonus under them when it has one. The player's colour marks the last
 * score in the box on another player's scorecard ([lastScoredColor]).
 *
 * Every line is a [SlotScore], laid out once and only shown or hidden after that - a filled line
 * never changes again, and the preview line keeps its number laid out under the "-" while the dice
 * are rolling, just as a one-slot cell does. So a roll that hides every preview is a swap of which
 * text is visible on one line per box, not a fresh layout of three.
 *
 * The lines are sized to fill the tile's height ([maxHeight]) exactly, so a larger system font
 * overflows it. Then the stack scrolls within that height instead of spilling over the next row: it
 * keeps the line that matters now in view - the next slot to score, or once the box is full its last
 * score ([stackedScrollTarget]) - re-scrolling only when that changes, and fades out at whichever
 * edge has more beyond it. A player can drag it to see the rest; a screen reader hears every slot
 * anyway, from the cell's state. At the usual font size it fits, and none of this does anything.
 */
@Composable
private fun StackedScores(
    scores: List<Int>,
    slotCount: Int,
    previewScore: Int?,
    previewGold: Boolean,
    lastScoredColor: Color?,
    bonusAmount: Int,
    prominent: Boolean,
    maxHeight: Dp,
    modifier: Modifier = Modifier,
) {
    // Three lines fill the tile's height: 3 x 16sp beside a 48dp tile, 3 x 20sp (+ a bonus line)
    // beside the 76dp 5x tile.
    val style = if (prominent) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.labelMedium
    val scrollState = rememberScrollState()
    val targetLine = if (scores.size < slotCount) scores.size else scores.lastIndex
    var contentHeight by remember { mutableIntStateOf(0) }
    var viewportHeight by remember { mutableIntStateOf(0) }
    // Only ever does anything while the stack overflows (maxValue > 0), and only when the line to show,
    // or the sizes, change - once a turn, not once a frame.
    LaunchedEffect(targetLine, contentHeight, viewportHeight) {
        if (scrollState.maxValue > 0) {
            scrollState.scrollTo(stackedScrollTarget(contentHeight, viewportHeight, lineCount = slotCount + if (bonusAmount > 0) 1 else 0, targetLine, scrollState.maxValue))
        }
    }
    val fadeHeight = with(LocalDensity.current) { STACK_FADE_HEIGHT.toPx() }
    Column(
        modifier = modifier
            // As wide as its numbers, not just its slot - like a one-slot cell's number, they spill
            // into the gap beside it, which the scroll's clip and the fade's layer would otherwise cut.
            .wrapContentWidth(Alignment.Start, unbounded = true)
            .heightIn(max = maxHeight)
            .onSizeChanged { viewportHeight = it.height }
            .then(if (contentHeight > viewportHeight && viewportHeight > 0) Modifier.overflowFade(scrollState, fadeHeight) else Modifier)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.Center,
    ) {
        Column(modifier = Modifier.onSizeChanged { contentHeight = it.height }) {
            for (slot in 0 until slotCount) {
                val filled = scores.getOrNull(slot)
                val isPreviewSlot = slot == scores.size
                val shown = when {
                    filled != null -> ShownScore(filled.toString(), gold = false, scored = true, accent = lastScoredColor?.takeIf { slot == scores.lastIndex })
                    isPreviewSlot && previewScore != null -> ShownScore(previewScore.toString(), gold = previewGold, scored = false, accent = null)
                    else -> null
                }
                SlotScore(shown = shown, style = style)
            }
            if (bonusAmount > 0) {
                Text(
                    text = "+$bonusAmount",
                    color = GoldAccent,
                    fontWeight = FontWeight.Bold,
                    style = if (prominent) MaterialTheme.typography.bodySmall else MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Visible,
                    softWrap = false,
                )
            }
        }
    }
}

/** How far the fade at a cut-off edge of an overflowing [StackedScores] reaches in. */
private val STACK_FADE_HEIGHT = 8.dp

/**
 * Where to scroll an overflowing [StackedScores] so its [targetLine] is in view - centred where it can
 * be, clamped to the scroll's range ([maxScroll]). The lines are all one height, so each is
 * [contentHeight] over [lineCount] tall.
 */
internal fun stackedScrollTarget(contentHeight: Int, viewportHeight: Int, lineCount: Int, targetLine: Int, maxScroll: Int): Int {
    if (lineCount <= 0) return 0
    val lineHeight = contentHeight / lineCount
    return (lineHeight * targetLine - (viewportHeight - lineHeight) / 2).coerceIn(0, maxScroll)
}

/**
 * Fades out the top and bottom edges of a scrolled stack wherever there's more beyond them - read in
 * the draw phase, so scrolling repaints it without recomposing anything.
 */
private fun Modifier.overflowFade(scrollState: ScrollState, fadeHeight: Float): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        if (scrollState.value > 0) {
            drawRect(
                brush = Brush.verticalGradient(listOf(Color.Black, Color.Transparent), startY = 0f, endY = fadeHeight),
                size = Size(size.width, fadeHeight),
                blendMode = BlendMode.DstOut,
            )
        }
        if (scrollState.value < scrollState.maxValue) {
            drawRect(
                brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black), startY = size.height - fadeHeight, endY = size.height),
                topLeft = Offset(0f, size.height - fadeHeight),
                size = Size(size.width, fadeHeight),
                blendMode = BlendMode.DstOut,
            )
        }
    }

/**
 * One line of [StackedScores]: [shown], or a "-" while there's nothing to show - with the last number
 * shown kept laid out but hidden behind it, so showing it again is only a change of which is visible.
 */
@Composable
private fun SlotScore(shown: ShownScore?, style: TextStyle) {
    val lastShown = remember { arrayOfNulls<ShownScore>(1) }
    if (shown != null) lastShown[0] = shown
    Box(contentAlignment = Alignment.CenterStart) {
        lastShown[0]?.let { number ->
            ScoreText(
                text = number.text,
                color = when {
                    number.gold -> GoldAccent
                    number.accent != null -> number.accent
                    else -> TileIconColor.copy(alpha = if (number.scored) 1f else 0.55f)
                },
                fontWeight = if (number.gold || number.accent != null) FontWeight.Bold else FontWeight.Normal,
                style = style,
                modifier = Modifier.alpha(if (shown != null) 1f else 0f),
            )
        }
        ScoreText(
            text = "-",
            color = TileIconColor.copy(alpha = 0.55f),
            fontWeight = FontWeight.Normal,
            style = style,
            modifier = Modifier.alpha(if (shown == null) 1f else 0f),
        )
    }
}

/**
 * A cell's score or preview as it was last shown: its text, whether it was a gold "worth picking" one,
 * whether it's scored, and the player's colour if it's their last turn's score (see [LastScoredHighlight]).
 * Compared by value, so a [SlotScore] handed the same score again - every filled slot, every time the
 * board recomposes - is skipped rather than recomposed.
 */
private data class ShownScore(val text: String, val gold: Boolean, val scored: Boolean, val accent: Color?)

/** A number (or "-") beside a category tile. */
@Composable
private fun ScoreText(text: String, color: Color, fontWeight: FontWeight, style: TextStyle, modifier: Modifier) {
    Text(
        text = text,
        color = color,
        fontWeight = fontWeight,
        style = style,
        maxLines = 1,
        // The weighted width here is razor-thin by design (see the Row's spacedBy comment in
        // CategoryCell) - just enough for a single digit. Clip was hard-cropping the second digit
        // of any score above 9 (Fives, Chance, ...); Visible lets it spill into that reserved gap
        // instead of being cut off.
        overflow = TextOverflow.Visible,
        softWrap = false,
        modifier = modifier,
    )
}
