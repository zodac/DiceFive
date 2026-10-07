package net.zodac.dicefive.ui.game

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.liveRegion
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
import net.zodac.dicefive.model.HitTarget
import net.zodac.dicefive.model.PlaceMatch
import net.zodac.dicefive.model.PlayerState
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.model.ScoreSection
import net.zodac.dicefive.resources.Res
import net.zodac.dicefive.resources.common_bonus_amount
import net.zodac.dicefive.resources.game_box_disabled_spoken
import net.zodac.dicefive.resources.game_box_first_five_spoken
import net.zodac.dicefive.resources.game_box_last_turn_spoken
import net.zodac.dicefive.resources.game_box_off
import net.zodac.dicefive.resources.game_box_open_spoken
import net.zodac.dicefive.resources.game_box_score_action
import net.zodac.dicefive.resources.game_box_target_name_spoken
import net.zodac.dicefive.resources.game_slots_bonus_spoken
import net.zodac.dicefive.resources.game_slots_last_score_spoken
import net.zodac.dicefive.resources.game_slots_more_open_spoken
import net.zodac.dicefive.resources.game_slots_open_spoken
import net.zodac.dicefive.resources.game_slots_scored_spoken
import net.zodac.dicefive.resources.game_slots_would_score_spoken
import net.zodac.dicefive.resources.game_target_any_place_spoken
import net.zodac.dicefive.resources.game_target_exact_hit_spoken
import net.zodac.dicefive.resources.game_target_hit_spoken
import net.zodac.dicefive.resources.game_target_name_spoken
import net.zodac.dicefive.resources.game_target_partial_hit_spoken
import net.zodac.dicefive.resources.game_target_progress_spoken
import net.zodac.dicefive.ui.common.joinClauses
import net.zodac.dicefive.ui.common.localised
import net.zodac.dicefive.ui.common.pluralStringResource
import net.zodac.dicefive.ui.common.stringResource
import net.zodac.dicefive.ui.game.style.LocalIrishTricolour
import net.zodac.dicefive.ui.theme.GoldAccent
import net.zodac.dicefive.ui.theme.TileIconColor

/** Standard's grid: six rows, upper section beside lower. More than this and the tiles go compact. */
internal const val REGULAR_GRID_ROWS = 6
internal val GRID_ROW_SPACING = 6.dp

/** The board's height for a Standard-sized grid - see [scoreBoardHeight]. */
internal val REGULAR_BOARD_HEIGHT = 380.dp

/**
 * Where every box of a card sits on the board: four equal columns, in rows shared by the left pane (the
 * grid: the upper section in column 1, the lower section in column 2) and the right pane (columns 3 and 4:
 * the [featured] box - 5x, or on a card without one the Alibi - across both in the first row, [sideRows] under it, the
 * dice cup over the next two rows, and the Totals and Undo buttons in the sixth). Both panes have [rowCount] rows of
 * the same height. Hit List's targets have no upper section to sit beside, so they fill the grid two to a row, in
 * card order.
 *
 * [leftRows] are the grid's rows, each one or two categories wide. [sideRows] are the boxes that sit under
 * the 5x tile: the Extended Scores modifier's three - Evens and Odds, then Two Pair - or, without it,
 * Tricolour's four colour boxes, two to a row. A card with both gives the Extended Scores boxes that place,
 * and the colour boxes go on after the lower section in [leftRows], two to a row.
 */
internal class BoardLayout(
    val leftRows: List<List<ScoreCategory>>,
    val sideRows: List<List<ScoreCategory>>,
    val featured: ScoreCategory = ScoreCategory.FIVE_OF_A_KIND,
) {
    val rowCount: Int
        get() = leftRows.size
}

/** The Extended Scores boxes under the 5x tile, by row. */
private val EXTENDED_SIDE_ROWS = listOf(listOf(ScoreCategory.EVENS, ScoreCategory.ODDS), listOf(ScoreCategory.TWO_PAIR))

/** How a card of [categories] is laid out - see [BoardLayout]. The featured box is left out of both: it has its own tile. */
internal fun boardLayout(categories: List<ScoreCategory>): BoardLayout {
    val featured = categories.firstOrNull { it.featured } ?: ScoreCategory.FIVE_OF_A_KIND
    val gridCategories = categories.filter { it != featured }
    val extended = EXTENDED_SIDE_ROWS.map { row -> row.filter { it in gridCategories } }.filter { it.isNotEmpty() }
    val sideRows = extended.ifEmpty { gridCategories.filter { it.section == ScoreSection.COLOUR }.chunked(2) }
    val upper = gridCategories.filter { it.section == ScoreSection.UPPER }
    val others = gridCategories - upper.toSet() - sideRows.flatten().toSet()
    val sideBySide = upper.zip(others) { left, right -> listOf(left, right) }
    val leftOver = upper.drop(others.size) + others.drop(upper.size)
    return BoardLayout(sideBySide + leftOver.chunked(2), sideRows, featured)
}

/**
 * How tall the scoring area is for a card of [categories]. Standard's six rows keep the board's original
 * height; more rows than that switch to compact tiles (see [CategoryTile]) and grow the board just
 * enough to fit one per row inside [padding] top and bottom. The dice cup is no shorter for it: the right
 * pane has free rows for it to take (see [DiceCupPanel]).
 */
internal fun scoreBoardHeight(categories: List<ScoreCategory>, padding: Dp): Dp {
    val rows = boardLayout(categories).rowCount
    if (rows <= REGULAR_GRID_ROWS) return REGULAR_BOARD_HEIGHT
    return maxOf(REGULAR_BOARD_HEIGHT, (COMPACT_TILE_SIZE + GRID_ROW_SPACING) * rows - GRID_ROW_SPACING + padding * 2)
}

/** The grid's tile size for [rowCount] rows - regular for Standard's six, compact beyond that. */
internal fun gridTileSize(rowCount: Int): Dp = if (rowCount > REGULAR_GRID_ROWS) COMPACT_TILE_SIZE else REGULAR_TILE_SIZE

/**
 * The two-column scorecard grid for the active player only - other players' progress is
 * summarized in the header tabs instead, matching the reference layout. [canScore] and
 * [available] are precomputed once by the caller and shared with the 5x tile beside it.
 * Which boxes it shows, and how they're laid out, come from [categories] (see [boardLayout]).
 */
@Composable
fun ScoreGrid(
    categories: List<ScoreCategory>,
    player: PlayerState?,
    dice: List<Die>,
    canScore: Boolean,
    showPreview: Boolean,
    available: Set<ScoreCategory>,
    onScoreCategory: (ScoreCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = boardLayout(categories).leftRows
    val compact = gridTileSize(rows.size) == COMPACT_TILE_SIZE
    Column(modifier = modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(GRID_ROW_SPACING)) {
        for (row in rows) {
            CellRow(row, player, canScore, showPreview, available, dice, onScoreCategory, compact, Modifier.weight(1f))
        }
    }
}

/** One row of the board: one or two [categories], each in half of it. A lone one keeps to the first half's width. */
@Composable
internal fun CellRow(
    categories: List<ScoreCategory>,
    player: PlayerState?,
    canScore: Boolean,
    showPreview: Boolean,
    available: Set<ScoreCategory>,
    dice: List<Die>,
    onScoreCategory: (ScoreCategory) -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        // Gives the first column's score text (up to 2 digits) clearance before the
        // second column's tile starts - otherwise they visually touch/overlap.
        horizontalArrangement = Arrangement.spacedBy(COLUMN_GAP),
    ) {
        for (category in categories) {
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
        if (categories.size == 1) Spacer(modifier = Modifier.weight(1f))
    }
}

/** The gap between the board's columns. */
internal val COLUMN_GAP = 20.dp

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
    // The tile stretched across the width it's given, for 5x: two columns wide, one row tall.
    wide: Boolean = false,
    // A square tile of this side, with its score beside it, for 5x over two rows.
    squareSize: Dp? = null,
    compact: Boolean = false,
) {
    val scores = player?.scoresIn(category).orEmpty()
    // Switched off for this game: never open, never scored, drawn apart from every other state.
    val switchedOff = player?.isDisabled(category) == true
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
    // A Hit List target: what it calls, and - while its dice are being previewed - how each place stands against them.
    val target = player?.hitList?.get(category)
    val matches = if (target != null && canPreview) target.matches(dice) else null
    // A target's tile lights up for a hit, not a partial one - nearly every roll scores a little on most targets, and a
    // board of lit tiles would hide the hits. A partial score is still gold beside it.
    val tileLit = isGoodChoice && (target == null || target.isHit(dice))
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
    val categoryName = category.spokenName(irish)
    val spokenState = when {
        switchedOff -> stringResource(Res.string.game_box_disabled_spoken)
        slotCount > 1 -> stackedSpokenState(scores, slotCount, previewScore, if (fiveOfAKindTileBonusPreview || fiveOfAKindBonusCount > 0) pendingBonusAmount else 0, lastScored != null)
        filled != null -> joinClauses(
            listOfNotNull(
                stringResource(Res.string.game_slots_scored_spoken, filled.localised()),
                stringResource(Res.string.game_slots_bonus_spoken, pendingBonusAmount).takeIf { pendingBonusAmount > 0 },
                stringResource(Res.string.game_box_last_turn_spoken).takeIf { lastScored != null },
            ),
        )
        previewScore != null -> joinClauses(
            listOfNotNull(
                stringResource(Res.string.game_slots_would_score_spoken, previewScore).replaceFirstChar { it.uppercase() },
                stringResource(Res.string.game_slots_bonus_spoken, pendingBonusAmount).takeIf { fiveOfAKindTileBonusPreview },
                if (target != null && matches != null) targetProgress(target, matches, previewScore) else null,
            ),
        )
        else -> stringResource(Res.string.game_box_open_spoken)
    }
    // A player's first 5x of the game, flashed gold: spoken as well, as a polite live region that announces the change once.
    val flashing = category == ScoreCategory.FIVE_OF_A_KIND && LocalFiveOfAKindFlash.current
    val targetName = target?.spokenName()
    val boxName = if (targetName != null) stringResource(Res.string.game_box_target_name_spoken, categoryName, targetName) else categoryName
    val firstFiveState = stringResource(Res.string.game_box_first_five_spoken, spokenState)
    val scoreActionLabel = stringResource(Res.string.game_box_score_action)
    val cellSemantics: SemanticsPropertyReceiver.() -> Unit = {
        contentDescription = boxName
        stateDescription = if (flashing) firstFiveState else spokenState
        if (flashing) liveRegion = LiveRegionMode.Polite
        if (switchedOff) disabled()
        if (isLegalChoice) {
            role = Role.Button
            onClick(label = scoreActionLabel) {
                onScoreCategory(category)
                true
            }
        }
    }
    val tile = @Composable { tileModifier: Modifier ->
        CategoryTile(
            category = category,
            modifier = tileModifier,
            // Not `|| fiveOfAKindTileBonusPreview`: the 5x tile is never actually pickable again
            // once scored (it isn't a legal choice), so glowing it like an open, scorable box
            // would be misleading - the +score line below is the preview, the tile's look doesn't
            // change.
            highlighted = tileLit,
            flashing = flashing,
            wide = wide,
            squareSize = squareSize,
            compact = compact,
            scored = boxFull,
            disabled = switchedOff,
            fiveOfAKindBonusCount = fiveOfAKindBonusCount,
            // On the wide tile, which has the room, rather than under the score beside it, which hasn't.
            fiveOfAKindBonusAmount = if (wide) pendingBonusAmount else 0,
            outlineColor = lastScored?.color,
            target = target,
            matches = matches,
            onClick = if (isLegalChoice) { { onScoreCategory(category) } } else null,
        )
    }
    val scoreContent = @Composable { scoreModifier: Modifier ->
        if (switchedOff) {
            // Words, not a number or the "-" of an open box: what's here is that there's nothing to score.
            ScoreText(
                text = stringResource(Res.string.game_box_off),
                color = TileIconColor.copy(alpha = 0.55f),
                fontWeight = FontWeight.Normal,
                style = MaterialTheme.typography.bodyMedium,
                modifier = scoreModifier,
            )
        } else if (slotCount > 1) {
            StackedScores(
                scores = scores,
                slotCount = slotCount,
                previewScore = previewScore,
                previewGold = isGoodChoice,
                lastScoredColor = lastScored?.color,
                bonusAmount = if (!wide && (fiveOfAKindBonusCount > 0 || fiveOfAKindTileBonusPreview)) pendingBonusAmount else 0,
                // No taller than the tile beside it - see StackedScores.
                maxHeight = when {
                    squareSize != null -> squareSize
                    compact -> COMPACT_TILE_SIZE
                    else -> REGULAR_TILE_SIZE
                },
                modifier = scoreModifier,
            )
        } else if (!wide && (fiveOfAKindBonusCount > 0 || fiveOfAKindTileBonusPreview)) {
            Column(modifier = scoreModifier) {
                Text(
                    text = (filled ?: previewScore ?: 0).localised(),
                    color = when {
                        isGoodChoice -> GoldAccent
                        lastScored != null -> lastScored.color
                        else -> TileIconColor
                    },
                    fontWeight = if (isGoodChoice || lastScored != null) FontWeight.Bold else FontWeight.Normal,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Visible,
                    softWrap = false,
                )
                Text(
                    // The total bonus on the 5x tile, not one line per extra 5x - ten of
                    // them is still just one "+900" line, not ten "+100"s.
                    text = stringResource(Res.string.common_bonus_amount, pendingBonusAmount),
                    color = GoldAccent,
                    fontWeight = FontWeight.Bold,
                    // Small: a row has little vertical room to spare for a second line.
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Visible,
                    softWrap = false,
                )
            }
        } else {
            val style = MaterialTheme.typography.bodyMedium
            // The score or preview on show - or, while there isn't one, the last one that was, kept
            // laid out but hidden behind the "-". Every cell's preview hides the moment the cup is
            // tapped for a reroll, and laying all their text out again (a number to "-", bold to
            // plain) on that one frame made it the heaviest of the roll; now it's just a swap of
            // which is visible. The number is laid out again only when it changes, as the dice
            // settle. Screen readers hear the cell's stateDescription, never these.
            val shown = (filled ?: previewScore)?.let { ShownScore(it.localised(), gold = isGoodChoice, scored = filled != null, accent = lastScored?.color) }
            val lastShown = remember { arrayOfNulls<ShownScore>(1) }
            if (shown != null) lastShown[0] = shown
            Box(modifier = scoreModifier, contentAlignment = Alignment.CenterStart) {
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
    if (wide) {
        // Two columns wide, its right edge level with the right edge of the tile in the second of them: the
        // score then sits where that column's scores do.
        BoxWithConstraints(modifier = modifier.clearAndSetSemantics(cellSemantics)) {
            val tileSize = if (compact) COMPACT_TILE_SIZE else REGULAR_TILE_SIZE
            val scoreWidth = (maxWidth - COLUMN_GAP) / 2 - tileSize - TILE_SCORE_GAP
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TILE_SCORE_GAP),
            ) {
                tile(Modifier.weight(1f))
                scoreContent(Modifier.width(scoreWidth))
            }
        }
    } else {
        Row(
            modifier = modifier.clearAndSetSemantics(cellSemantics),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TILE_SCORE_GAP),
        ) {
            tile(Modifier)
            scoreContent(Modifier.weight(1f))
        }
    }
}

/** The gap between a tile and its score. */
private val TILE_SCORE_GAP = 8.dp

/** What a target calls, said aloud - "4, 1, 3, 2, any. 20 points, 40 exact" - since its tile shows it only as art. */
@Composable
internal fun HitTarget.spokenName(): String {
    val anyPlace = stringResource(Res.string.game_target_any_place_spoken)
    return pluralStringResource(Res.plurals.game_target_name_spoken, points, joinClauses(places.map { it?.localised() ?: anyPlace }), points, exactPoints)
}

/**
 * The spoken twin of a target tile's underlines: how many of its numbers the dice show, and how many are in their
 * places - or, once it's hit, which kind of hit it is (a [previewScore] of its exact points is an exact hit). A
 * partial hit's points are said as such.
 */
@Composable
internal fun targetProgress(target: HitTarget, matches: List<PlaceMatch>, previewScore: Int): String {
    val rolled = matches.count { it == PlaceMatch.ROLLED || it == PlaceMatch.IN_PLACE }
    val inPlace = matches.count { it == PlaceMatch.IN_PLACE }
    return when {
        previewScore == target.exactPoints -> stringResource(Res.string.game_target_exact_hit_spoken)
        rolled == target.called.size -> stringResource(Res.string.game_target_hit_spoken, inPlace, target.called.size)
        previewScore > 0 -> stringResource(Res.string.game_target_partial_hit_spoken, rolled, target.called.size, inPlace)
        else -> stringResource(Res.string.game_target_progress_spoken, rolled, target.called.size, inPlace)
    }
}

/**
 * What a screen reader hears for a box with [slotCount] slots: what's in each filled one, what the
 * dice would score in the next, and how many are left open - "Scored 12, 9, would score 15", "Scored
 * 12, 2 open" - then any 5x bonus and whether its last score was the player's last turn.
 */
@Composable
internal fun stackedSpokenState(scores: List<Int>, slotCount: Int, previewScore: Int?, bonusAmount: Int, lastScored: Boolean): String {
    val stillOpen = slotCount - scores.size - if (previewScore != null) 1 else 0
    val parts = listOfNotNull(
        scores.takeIf { it.isNotEmpty() }?.let { stringResource(Res.string.game_slots_scored_spoken, joinClauses(it.map(Int::toString))) },
        previewScore?.let { stringResource(Res.string.game_slots_would_score_spoken, it) },
        when {
            stillOpen <= 0 -> null
            previewScore != null -> pluralStringResource(Res.plurals.game_slots_more_open_spoken, stillOpen, stillOpen)
            else -> pluralStringResource(Res.plurals.game_slots_open_spoken, stillOpen, stillOpen)
        },
        bonusAmount.takeIf { it > 0 }?.let { stringResource(Res.string.game_slots_bonus_spoken, it) },
        scores.lastOrNull()?.takeIf { lastScored }?.let { stringResource(Res.string.game_slots_last_score_spoken, it) },
    )
    return joinClauses(parts).replaceFirstChar { it.uppercase() }
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
    maxHeight: Dp,
    modifier: Modifier = Modifier,
) {
    // Three lines fill the tile's height: 3 x 16sp beside a 48dp tile.
    val style = MaterialTheme.typography.labelMedium
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
                    filled != null -> ShownScore(filled.localised(), gold = false, scored = true, accent = lastScoredColor?.takeIf { slot == scores.lastIndex })
                    isPreviewSlot && previewScore != null -> ShownScore(previewScore.localised(), gold = previewGold, scored = false, accent = null)
                    else -> null
                }
                SlotScore(shown = shown, style = style)
            }
            if (bonusAmount > 0) {
                Text(
                    text = stringResource(Res.string.common_bonus_amount, bonusAmount),
                    color = GoldAccent,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelSmall,
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
