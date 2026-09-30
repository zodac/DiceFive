package net.zodac.dicefive.ui.scores

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.zodac.dicefive.data.scores.SCORES_PAGE_SIZE
import net.zodac.dicefive.data.scores.ScoreEntry
import net.zodac.dicefive.game.TieBreak
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.ui.common.LazyListScrollbar
import net.zodac.dicefive.ui.common.ScreenScaffold
import net.zodac.dicefive.ui.common.formatTimestamp
import net.zodac.dicefive.ui.theme.Bronze
import net.zodac.dicefive.ui.theme.Silver

/** [GameMode.HIGHEST_POSSIBLE_SCORE] (a perfect game in whichever mode allows the most) is the longest a score can ever be. */
private val SCORE_DISPLAY_WIDTH = GameMode.HIGHEST_POSSIBLE_SCORE.toString().length

/** Ranks worth calling out on the leaderboard, whichever page they happen to fall on. */
private const val PODIUM_RANKS = 3

/** How strong the podium row's own gold/silver/bronze background tint is - faint enough that the
 * row's text (itself already coloured with the same accent) stays comfortably readable over it. */
private const val PODIUM_BACKGROUND_ALPHA = 0.16f

/** A small gap above the 2nd and 3rd podium rows, outside their own coloured fill, so gold doesn't
 * visibly bleed straight into silver into bronze - three adjacent, touching tinted rows read as
 * one shape otherwise. */
private val PODIUM_ROW_GAP = 4.dp

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
private fun scoreColumns(): ScoreColumns = if (LocalDensity.current.fontScale > LARGE_FONT_SCALE) LARGE_FONT_COLUMNS else NORMAL_COLUMNS

@Composable
fun ScoresScreen(
    viewModel: ScoresViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ScreenScaffold(title = "Leaderboard", onBack = onBack, modifier = modifier) {
        if (state.entries.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "No scores yet - play a game!",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                )
            }
        } else {
            Card(modifier = Modifier.fillMaxWidth().weight(1f)) {
                Column(modifier = Modifier.padding(8.dp)) {
                    HeaderRow()
                    // No divider here any more - it sat flush against the first row with no gap,
                    // so its line cut straight across the top of a gold/silver/bronze podium row's
                    // own rounded background the moment one was in play. The header cells' own
                    // colour/weight already separate them from the data below without it.
                    Spacer(modifier = Modifier.height(4.dp))
                    val listState = rememberLazyListState()
                    // Ranks/`=` ties reflect the same house-rule ordering ScoreDao.pagedScores
                    // already sorted this page by - see rankEntries.
                    val ranked = remember(state.entries, state.pageIndex) { rankEntries(state.entries, state.pageIndex) }
                    Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            // Matching padding on both sides, not just room on the right for the
                            // scrollbar - reserving space on the right alone (however deliberate)
                            // left every row's content visibly closer to the left edge than the
                            // right, scrollbar or not.
                            contentPadding = PaddingValues(horizontal = 12.dp),
                        ) {
                            itemsIndexed(ranked, key = { _, ranked -> ranked.entry.id }) { index, rankedEntry ->
                                ScoreRow(
                                    rank = rankedEntry.rank,
                                    isTrueTie = rankedEntry.isTrueTie,
                                    entry = rankedEntry.entry,
                                    striped = index % 2 == 1,
                                )
                            }
                        }
                        LazyListScrollbar(listState = listState)
                    }
                }
            }
            // The row's height is reserved whether or not anything fills it: the controls are only
            // worth showing when there's somewhere to page to ("Page 1 of 1" between two dead
            // buttons is furniture), but letting the table grow into the gap would mean the card
            // ended in a different place on a one-page leaderboard than on a two-page one.
            Box(
                // At least the normal height at any font size, and taller with it: the buttons' text grows.
                modifier = Modifier.fillMaxWidth().heightIn(min = PAGINATION_ROW_HEIGHT * LocalDensity.current.fontScale.coerceAtLeast(1f)),
                contentAlignment = Alignment.Center,
            ) {
                if (state.totalPages > 1) {
                    PaginationControls(
                        pageIndex = state.pageIndex,
                        totalPages = state.totalPages,
                        hasPrevious = state.hasPreviousPage,
                        hasNext = state.hasNextPage,
                        onPrevious = viewModel::previousPage,
                        onNext = viewModel::nextPage,
                    )
                }
            }
        }
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
        HeaderCell(text = "Player", weight = columns.player)
        HeaderCell(text = "5x", weight = columns.fiveOfAKind, align = TextAlign.Center)
        HeaderCell(text = "Score", weight = columns.score, align = TextAlign.End)
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
 * house-rule ordering `ScoreDao.pagedScores`'s `ORDER BY` already sorted them by (keep the two in
 * step; see [TieBreak.leaderboardComparator]'s doc comment). A tie split across a page boundary
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

// rememberPlainTooltipPositionProvider is deprecated in favour of rememberTooltipPositionProvider,
// which doesn't exist yet in material3 1.4.0 - it arrives with the 1.5.0 line. Swap it over then.
@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScoreRow(rank: Int, isTrueTie: Boolean, entry: ScoreEntry, striped: Boolean) {
    val onPodium = rank <= PODIUM_RANKS
    val accent = podiumAccent(rank)
    val columns = scoreColumns()

    val tooltipState = rememberTooltipState()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(text = formatTimestamp(entry.timestampEpochMillis)) } },
        state = tooltipState,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Only between podium rows (2nd/3rd get it above them) - a plain divider-free row
                // still wants to sit flush against its neighbours the way it always has.
                .padding(top = if (rank in 2..PODIUM_RANKS) PODIUM_ROW_GAP else 0.dp)
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
                .padding(horizontal = 8.dp, vertical = if (onPodium) 6.dp else 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Same style/weight on every row, podium or not - the accent colour above is already
            // the whole distinction, a second (font) one on top of it was redundant.
            Text(
                // "=" only for a true tie (every tie-break criterion also matches) - see rankEntries.
                text = if (isTrueTie) "=$rank" else rank.toString(),
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
                text = entry.fiveOfAKindCount.toString(),
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
                text = entry.score.toString().padStart(SCORE_DISPLAY_WIDTH),
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

@Composable
private fun PaginationControls(
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
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                modifier = Modifier.padding(end = 4.dp),
            )
            Text("Previous")
        }
        Text(
            text = "Page ${pageIndex + 1} of $totalPages",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onNext, enabled = hasNext) {
            Text("Next")
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}
