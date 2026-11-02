package net.zodac.dicefive.ui.scores

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.zodac.dicefive.data.scores.SCORES_PAGE_SIZE
import net.zodac.dicefive.data.scores.ScoreEntry
import net.zodac.dicefive.game.TieBreak
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.resources.Res
import net.zodac.dicefive.resources.common_page_of
import net.zodac.dicefive.resources.common_tied_rank
import net.zodac.dicefive.resources.scores_header_player
import net.zodac.dicefive.resources.scores_header_score
import net.zodac.dicefive.resources.scores_next
import net.zodac.dicefive.resources.scores_previous
import net.zodac.dicefive.ui.common.LazyListScrollbar
import net.zodac.dicefive.ui.common.OnDemandTooltip
import net.zodac.dicefive.ui.common.localised
import net.zodac.dicefive.ui.common.stringResource
import net.zodac.dicefive.ui.common.textScale
import net.zodac.dicefive.ui.common.timestampFormatter
import net.zodac.dicefive.ui.icons.Symbols
import net.zodac.dicefive.ui.theme.Bronze
import net.zodac.dicefive.ui.theme.Silver

/** [GameMode.HIGHEST_POSSIBLE_SCORE] (a perfect game in whichever mode allows the most) is the longest a score can ever be. */
private val SCORE_DISPLAY_WIDTH = GameMode.HIGHEST_POSSIBLE_SCORE.toString().length

/** Ranks worth calling out on the leaderboard, whichever page they happen to fall on. */
private const val PODIUM_RANKS = 3

/** How strong the podium row's own gold/silver/bronze background tint is - faint enough that the
 * row's text (itself already coloured with the same accent) stays comfortably readable over it. */
private const val PODIUM_BACKGROUND_ALPHA = 0.16f

/** Extra room under the Combined card when its last row is a podium one - see [LeaderboardTable]. */
private val PODIUM_END_EXTRA_PADDING = 4.dp

/** A small gap above the 2nd and 3rd podium rows, outside their own coloured fill, so gold doesn't
 * visibly bleed straight into silver into bronze - three adjacent, touching tinted rows read as
 * one shape otherwise. */
private val PODIUM_ROW_GAP = 4.dp


/** The padding above and below an ordinary (non-podium) row's text. */
private val PLAIN_ROW_VERTICAL_PADDING = 3.dp

/** A text button's own height - the space the pagination row occupies, filled or not. */
private val PAGINATION_ROW_HEIGHT = 40.dp

/** From this system font scale up, the table's narrow columns (rank, 5x, score) get a bigger share of the
 * row: at the normal split, a three-digit rank or a four-digit score wider than its cell overlaps its
 * neighbour. Below it the split is the one the table has always had. */
private const val LARGE_FONT_SCALE = 1.15f

/** The share of a row each column gets - [HeaderRow] and every [ScoreRow] use the same, so they line up. */
private class ScoreColumns(val rank: Float, val player: Float, val fiveOfAKind: Float, val score: Float)

private val NORMAL_COLUMNS = ScoreColumns(rank = 1f, player = 4f, fiveOfAKind = 1f, score = 1.5f)
private val LARGE_FONT_COLUMNS = ScoreColumns(rank = 1.5f, player = 3.5f, fiveOfAKind = 1.5f, score = 2.5f)

@Composable
private fun scoreColumns(): ScoreColumns = if (LocalDensity.current.textScale > LARGE_FONT_SCALE) LARGE_FONT_COLUMNS else NORMAL_COLUMNS


/** Widest a board's table, and the row over it, are in landscape. */
private val LANDSCAPE_TABLE_WIDTH = 640.dp

/**
 * One leaderboard as a card: [header] (the board's title, a heading, and whatever sits on its row), the column
 * headings, then the page's rows ranked by the tie-break house rule, with 1st-3rd called out in gold, silver, and
 * bronze. A long press on a row shows its date, and its mode when [showMode] (a board of one mode needn't say it).
 * A board with no scores shows [emptyText] instead of the card.
 *
 * Every board uses this, so every board looks and behaves alike: the card is as tall as its rows, growing with them until
 * it has the page and then scrolling inside, and its page controls are under it, in a row whose height is reserved
 * whether or not there is anywhere to page to (a board with one page ends where one with two does). In [landscape] the
 * caller draws the controls itself, with [LeaderboardPagination], beside the drop-down over the card.
 */
@Composable
fun ColumnScope.LeaderboardTable(
    board: LeaderboardPage,
    emptyText: String,
    showMode: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    landscape: Boolean,
    header: @Composable ColumnScope.() -> Unit,
) {
    if (board.entries.isEmpty()) {
        EmptyBoardCard(emptyText)
        return
    }
    // Ranks/`=` ties reflect the same house-rule ordering ScoreDao.pagedScores
    // already sorted this page by - see rankEntries.
    val ranked = remember(board.entries, board.pageIndex) { rankEntries(board.entries, board.pageIndex) }
    // A podium row's tint runs to its own edge, so a card that ends on one looks to have less room below it than
    // one that ends on a plain row, whose text has nothing painted around it. Give it a little more.
    val endsOnPodium = ranked.last().rank <= PODIUM_RANKS
    Card(modifier = Modifier.widthIn(max = if (landscape) LANDSCAPE_TABLE_WIDTH else Dp.Infinity).fillMaxWidth().weight(1f, fill = false)) {
        Column(modifier = Modifier.padding(start = 8.dp, top = 8.dp, end = 8.dp, bottom = if (endsOnPodium) 8.dp + PODIUM_END_EXTRA_PADDING else 8.dp)) {
            header()
            HeaderRow()
            // No divider under the headings - it would cut across the top of a podium row's rounded background. The
            // header cells' own colour and weight already separate them from the rows.
            Spacer(modifier = Modifier.height(4.dp))
            val listState = rememberLazyListState()
            // A new page starts at its top, not wherever the last one was scrolled to.
            LaunchedEffect(board.pageIndex) { listState.scrollToItem(0) }
            Box(modifier = Modifier.fillMaxWidth()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth(),
                    // The same padding on both sides, so a row's content sits as far from the left edge as from the right.
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) {
                    itemsIndexed(ranked, key = { _, ranked -> ranked.entry.uuid.toHexString() }) { index, rankedEntry ->
                        ScoreRow(
                            rank = rankedEntry.rank,
                            isTrueTie = rankedEntry.isTrueTie,
                            entry = rankedEntry.entry,
                            striped = index % 2 == 1,
                            showMode = showMode,
                        )
                    }
                }
                LazyListScrollbar(listState = listState)
            }
        }
    }
    if (landscape) return
    Box(
        // At least the normal height at any font size, and taller with it: the buttons' text grows.
        modifier = Modifier.fillMaxWidth().heightIn(min = PAGINATION_ROW_HEIGHT * LocalDensity.current.textScale.coerceAtLeast(1f)),
        contentAlignment = Alignment.Center,
    ) {
        LeaderboardPagination(board, onPrevious, onNext)
    }
}

/** A board's page controls, where it has more than one page. */
@Composable
fun LeaderboardPagination(board: LeaderboardPage, onPrevious: () -> Unit, onNext: () -> Unit) {
    if (board.totalPages > 1) {
        PaginationControls(
            pageIndex = board.pageIndex,
            totalPages = board.totalPages,
            hasPrevious = board.hasPreviousPage,
            hasNext = board.hasNextPage,
            onPrevious = onPrevious,
            onNext = onNext,
        )
    }
}

@Composable
private fun EmptyBoardCard(text: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(24.dp),
        )
    }
}

@Composable
private fun HeaderRow() {
    val columns = scoreColumns()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Matches the LazyColumn's own contentPadding(horizontal = 12.dp) below on both sides -
            // without it here too, the header's columns don't line up with where the rows' own
            // text actually lands.
            .padding(horizontal = 8.dp + 12.dp, vertical = 2.dp),
    ) {
        HeaderCell(text = "#", weight = columns.rank)
        HeaderCell(text = stringResource(Res.string.scores_header_player), weight = columns.player)
        HeaderCell(text = requireNotNull(ScoreCategory.FIVE_OF_A_KIND.mark), weight = columns.fiveOfAKind, align = TextAlign.Center)
        HeaderCell(text = stringResource(Res.string.scores_header_score), weight = columns.score, align = TextAlign.End)
    }
}

@Composable
private fun RowScope.HeaderCell(text: String, weight: Float, align: TextAlign = TextAlign.Start) {
    Text(
        text = text,
        modifier = Modifier.weight(weight),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = align,
    )
}

/**
 * The podium accent for a rank, or null off the podium. 1st place reuses `colorScheme.primary` -
 * already the brand's gold - while 2nd/3rd reach for the fixed silver/bronze pair in `Color.kt`,
 * since M3 has no role for either.
 */
@Composable
private fun podiumAccent(rank: Int): Color? {
    return when (rank) {
        1 -> MaterialTheme.colorScheme.primary
        2 -> Silver
        3 -> Bronze
        else -> null
    }
}

/** One page's row, ranked by the tie-break house rule (`game/TieBreak.kt`) rather than plain
 * position - [isTrueTie] means this row shares [rank] with at least one neighbour because every
 * criterion in that list also matched between them, not just their raw score. */
private data class RankedEntry(val entry: ScoreEntry, val rank: Int, val isTrueTie: Boolean)

/**
 * Assigns each of this page's [entries] its rank, sharing one only for a true tie - the same
 * house-rule ordering `ScoreDao.pagedScores`'s `ORDER BY` already sorted them by (built from
 * [TieBreak.leaderboardOrder]). A tie split across a page boundary
 * isn't caught here - each page only ever compares against its own rows.
 */
private fun rankEntries(entries: List<ScoreEntry>, pageIndex: Int): List<RankedEntry> {
    val stats = entries.map { it.toTieBreakStats() }
    val ranks = IntArray(entries.size)
    var rank = pageIndex * SCORES_PAGE_SIZE + 1
    for (index in entries.indices) {
        if (index > 0 && TieBreak.leaderboardComparator.compare(stats[index - 1], stats[index]) != 0) {
            rank = pageIndex * SCORES_PAGE_SIZE + index + 1
        }
        ranks[index] = rank
    }
    return entries.indices.map { index ->
        val tiedWithPrevious = index > 0 && ranks[index - 1] == ranks[index]
        val tiedWithNext = index < entries.lastIndex && ranks[index + 1] == ranks[index]
        RankedEntry(entries[index], ranks[index], isTrueTie = tiedWithPrevious || tiedWithNext)
    }
}

@Composable
private fun ScoreRow(
    rank: Int,
    isTrueTie: Boolean,
    entry: ScoreEntry,
    striped: Boolean,
    showMode: Boolean,
) {
    val onPodium = rank <= PODIUM_RANKS
    val accent = podiumAccent(rank)
    val columns = scoreColumns()
    val modeName = if (showMode) entry.gameMode?.let { stringResource(it.displayName) } ?: entry.gameModeId else null
    val formatTimestamp = timestampFormatter()

    // The mode only where rows of every mode share a table - on its own card it's the card's title.
    // Built on a long press only: see OnDemandTooltip.
    OnDemandTooltip(message = { scoreRowDetail(formatTimestamp(entry.timestampEpochMillis), modeName) }) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Only between podium rows (2nd/3rd get it above them) - a plain divider-free row
                // still wants to sit flush against its neighbours the way it always has.
                .padding(top = if (onPodium && rank > 1) PODIUM_ROW_GAP else 0.dp)
                .clip(MaterialTheme.shapes.small)
                // A podium row gets a rounded tint in its own gold/silver/bronze instead of the
                // ordinary zebra striping - that stripe would otherwise fight with the accent
                // colour for attention. Off the podium, striping instead of a divider per row: at
                // 100 rows a page, lines turn the table into a grid, while alternating fills stay
                // quiet.
                .background(
                    when {
                        accent != null -> accent.copy(alpha = PODIUM_BACKGROUND_ALPHA)
                        striped -> MaterialTheme.colorScheme.surfaceContainerHighest
                        else -> Color.Transparent
                    },
                )
                .padding(horizontal = 8.dp, vertical = if (onPodium) 6.dp else PLAIN_ROW_VERTICAL_PADDING),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Same style/weight on every row, podium or not - the accent colour above is already
            // the whole distinction, a second (font) one on top of it was redundant.
            Text(
                // "=" only for a true tie (every tie-break criterion also matches) - see rankEntries.
                text = if (isTrueTie) stringResource(Res.string.common_tied_rank, rank) else rank.localised(),
                modifier = Modifier.weight(columns.rank),
                style = MaterialTheme.typography.bodySmall,
                color = accent ?: MaterialTheme.colorScheme.onSurfaceVariant,
                softWrap = false,
            )
            Text(
                text = entry.playerName,
                modifier = Modifier.weight(columns.player),
                style = MaterialTheme.typography.bodyMedium,
                // A second line before an ellipsis: at a large font a name that no longer fits one line
                // wraps rather than losing its end. Names that fit one line look as they always did.
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            // How many 5x that game scored - a quiet secondary column, so it takes the rank's muted
            // colour rather than competing with the score.
            Text(
                text = entry.fiveOfAKindCount.localised(),
                modifier = Modifier.weight(columns.fiveOfAKind),
                style = MaterialTheme.typography.bodySmall,
                color = accent ?: MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                softWrap = false,
            )
            Text(
                // Space-padded to a fixed width, same reasoning as Statistics' max score: keeps
                // every row's score the same width regardless of digit count. Always bodyMedium,
                // podium or not - the row's own background tint is what calls out a podium finish
                // now, not a second size bump on top of it.
                text = entry.score.localised().padStart(SCORE_DISPLAY_WIDTH),
                modifier = Modifier.weight(columns.score),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = accent ?: MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End,
                softWrap = false,
            )
        }
    }
}

/** When the entry was played ([timestamp]), and - with [modeName] - in which mode, on a line of its own. */
private fun scoreRowDetail(timestamp: String, modeName: String?): String =
    if (modeName != null) "$timestamp\n$modeName" else timestamp

@Composable
internal fun PaginationControls(
    pageIndex: Int,
    totalPages: Int,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    // A FlowRow, not a Row: at a large font the three no longer fit across, and the page count drops to a
    // second line instead of running off the edge. At the normal size it's one row, as before.
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.Center,
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onPrevious, enabled = hasPrevious) {
            Icon(
                imageVector = Symbols.ArrowBack,
                contentDescription = null,
                modifier = Modifier.padding(end = 4.dp),
            )
            Text(stringResource(Res.string.scores_previous))
        }
        Text(
            text = stringResource(Res.string.common_page_of, pageIndex + 1, totalPages),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onNext, enabled = hasNext) {
            Text(stringResource(Res.string.scores_next))
            Icon(
                imageVector = Symbols.ArrowForward,
                contentDescription = null,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}
