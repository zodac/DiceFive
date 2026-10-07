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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.zodac.dicefive.data.scores.SCORES_PAGE_SIZE
import net.zodac.dicefive.data.scores.ScoreEntry
import net.zodac.dicefive.game.TieBreak
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.resources.Res
import net.zodac.dicefive.resources.common_page_of
import net.zodac.dicefive.resources.common_tied_rank
import net.zodac.dicefive.resources.scores_empty
import net.zodac.dicefive.resources.scores_header_player
import net.zodac.dicefive.resources.scores_header_score
import net.zodac.dicefive.resources.scores_next
import net.zodac.dicefive.resources.scores_previous
import net.zodac.dicefive.resources.scores_title
import net.zodac.dicefive.ui.common.LazyListScrollbar
import net.zodac.dicefive.ui.common.OnDemandTooltip
import net.zodac.dicefive.ui.common.ScreenScaffold
import net.zodac.dicefive.ui.common.SegmentedChoiceRow
import net.zodac.dicefive.ui.common.SoraFontFamily
import net.zodac.dicefive.ui.common.VerticalScrollbar
import net.zodac.dicefive.ui.common.delayWhileResumed
import net.zodac.dicefive.ui.common.timestampFormatter
import net.zodac.dicefive.ui.common.localised
import net.zodac.dicefive.ui.common.stringResource
import net.zodac.dicefive.ui.theme.Bronze
import net.zodac.dicefive.ui.theme.Silver

/** [GameMode.HIGHEST_POSSIBLE_SCORE] (a perfect game in whichever mode allows the most) is the longest a score can ever be. */
private val SCORE_DISPLAY_WIDTH = GameMode.HIGHEST_POSSIBLE_SCORE.toString().length

/** Ranks worth calling out on the leaderboard, whichever page they happen to fall on. */
private const val PODIUM_RANKS = 3

/** How strong the podium row's own gold/silver/bronze background tint is - faint enough that the
 * row's text (itself already coloured with the same accent) stays comfortably readable over it. */
private const val PODIUM_BACKGROUND_ALPHA = 0.16f

/** Extra room under the Combined card when its last row is a podium one - see [CombinedLeaderboard]. */
private val PODIUM_END_EXTRA_PADDING = 4.dp

/** A small gap above the 2nd and 3rd podium rows, outside their own coloured fill, so gold doesn't
 * visibly bleed straight into silver into bronze - three adjacent, touching tinted rows read as
 * one shape otherwise. */
private val PODIUM_ROW_GAP = 4.dp

/** How many scores a game mode's card shows before the rest scroll. */
private const val MODE_CARD_VISIBLE_ROWS = 10

/** The padding above and below an ordinary (non-podium) row's text. */
private val PLAIN_ROW_VERTICAL_PADDING = 3.dp

/** The page's side margin that [ScreenScaffold] leaves free, where the Game Mode page's scrollbar sits. */
private val PAGE_MARGIN = 20.dp

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
    driftingDice: Boolean = true,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ScreenScaffold(title = stringResource(Res.string.scores_title), onBack = onBack, modifier = modifier, driftingDice = driftingDice) {
        // Nothing but the title bar until the scores are read, so "No scores yet" never flashes up
        // before a leaderboard that has some.
        if (!state.isLoaded) return@ScreenScaffold
        // The switch is always there: a board with nothing on the Combined table can still have scores on a
        // card (a mode that never counts towards it).
        SegmentedChoiceRow(
            options = LeaderboardView.entries,
            selected = state.view,
            onSelect = viewModel::selectView,
            label = { stringResource(it.label) },
            modifier = Modifier.fillMaxWidth(),
        )
        when (state.view) {
            LeaderboardView.COMBINED -> CombinedLeaderboard(state = state, viewModel = viewModel)
            LeaderboardView.GAME_MODE -> GameModeLeaderboard(state = state, viewModel = viewModel)
        }
    }
}

@Composable
private fun NoScoresCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(Res.string.scores_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(24.dp),
        )
    }
}

/** Every mode's scores in one table, 1st-3rd called out in gold, silver and bronze; a long press names a row's mode and date. */
@Composable
private fun ColumnScope.CombinedLeaderboard(state: ScoresUiState, viewModel: ScoresViewModel) {
    if (state.entries.isEmpty()) {
        NoScoresCard()
        return
    }
    // Ranks/`=` ties reflect the same house-rule ordering ScoreDao.pagedScores
    // already sorted this page by - see rankEntries.
    val ranked = remember(state.entries, state.pageIndex) { rankEntries(state.entries, state.pageIndex) }
    // A podium row's tint runs to its own edge, so a card that ends on one looks to have less room below it than
    // one that ends on a plain row, whose text has nothing painted around it. Give it a little more.
    val endsOnPodium = ranked.last().rank <= PODIUM_RANKS
    // As tall as its rows, growing with them until it has the page - then the list scrolls inside it.
    Card(modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
        Column(modifier = Modifier.padding(start = 8.dp, top = 8.dp, end = 8.dp, bottom = if (endsOnPodium) 8.dp + PODIUM_END_EXTRA_PADDING else 8.dp)) {
            HeaderRow()
            // No divider here any more - it sat flush against the first row with no gap,
            // so its line cut straight across the top of a gold/silver/bronze podium row's
            // own rounded background the moment one was in play. The header cells' own
            // colour/weight already separate them from the data below without it.
            Spacer(modifier = Modifier.height(4.dp))
            val listState = rememberLazyListState()
            Box(modifier = Modifier.fillMaxWidth()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth(),
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
                            highlightPodium = true,
                            showMode = true,
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

/**
 * One card per game mode, stacked in a page that scrolls (its scrollbar in the side margin, where the
 * whole margin is the handle - a card's own list takes the finger inside it). Each card is no taller than
 * its top [MODE_CARD_VISIBLE_ROWS] scores; the rest scroll inside it, and its page controls wait at the
 * end of that scroll.
 */
@Composable
private fun ColumnScope.GameModeLeaderboard(state: ScoresUiState, viewModel: ScoresViewModel) {
    // Only modes with a score get a card; none at all, once every mode has been read, is an empty board.
    val boards = LEADERBOARD_MODES.mapNotNull { mode -> state.modeBoards[mode]?.takeIf { it.totalCount > 0 }?.let { mode to it } }
    if (boards.isEmpty()) {
        if (state.modeBoardsLoaded) NoScoresCard()
        return
    }
    val pageScroll = rememberScrollState()
    Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
        Column(modifier = Modifier.verticalScroll(pageScroll), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            for ((mode, board) in boards) {
                ModeCard(
                    mode = mode,
                    board = board,
                    onPrevious = { viewModel.previousModePage(mode) },
                    onNext = { viewModel.nextModePage(mode) },
                )
            }
        }
        VerticalScrollbar(
            scrollState = pageScroll,
            width = PAGE_MARGIN,
            modifier = Modifier.align(Alignment.TopEnd).offset(x = PAGE_MARGIN),
        )
    }
}

@Composable
private fun ModeCard(mode: GameMode, board: ModeBoard, onPrevious: () -> Unit, onNext: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = stringResource(mode.displayName),
                style = MaterialTheme.typography.titleMedium,
                fontFamily = SoraFontFamily,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp).semantics { heading() },
            )
            HeaderRow()
            Spacer(modifier = Modifier.height(4.dp))
            val listState = rememberLazyListState()
            // A new page starts at its top, not wherever the last one was scrolled to.
            LaunchedEffect(board.pageIndex) { listState.scrollToItem(0) }
            val ranked = remember(board.entries, board.pageIndex) { rankEntries(board.entries, board.pageIndex) }
            Box(modifier = Modifier.fillMaxWidth().heightIn(max = modeCardListHeight())) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) {
                    itemsIndexed(ranked, key = { _, ranked -> ranked.entry.id }) { index, rankedEntry ->
                        ScoreRow(
                            rank = rankedEntry.rank,
                            isTrueTie = rankedEntry.isTrueTie,
                            entry = rankedEntry.entry,
                            striped = index % 2 == 1,
                            highlightPodium = false,
                            showMode = false,
                        )
                    }
                    // Only at the end of the scroll, so a card costs no height for controls it rarely needs.
                    if (board.totalPages > 1) {
                        item(key = "pagination") {
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
                }
                LazyListScrollbar(listState = listState)
            }
        }
    }
}

/** What [MODE_CARD_VISIBLE_ROWS] ordinary rows add up to: a row is a line of `bodyMedium` and its padding, so it grows with the font. */
@Composable
private fun modeCardListHeight(): Dp {
    val lineHeight = with(LocalDensity.current) { MaterialTheme.typography.bodyMedium.lineHeight.toDp() }
    return (lineHeight + PLAIN_ROW_VERTICAL_PADDING * 2) * MODE_CARD_VISIBLE_ROWS
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
        HeaderCell(text = "5x", weight = columns.fiveOfAKind, align = TextAlign.Center) // i18n: not translated - the game's mark
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

@Composable
private fun ScoreRow(
    rank: Int,
    isTrueTie: Boolean,
    entry: ScoreEntry,
    striped: Boolean,
    highlightPodium: Boolean,
    showMode: Boolean,
) {
    val onPodium = highlightPodium && rank <= PODIUM_RANKS
    val accent = if (highlightPodium) podiumAccent(rank) else null
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
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

/** Set once [ScoresWarmUp] has drawn both views, for the life of the process. */
private var scoresWarmedUp = false

// After the menu's own entrance, and clear of the Styles warm-up, which starts at 600ms and runs for a few hundred.
private const val SCORES_WARM_UP_DELAY_MILLIS = 2000L

// Frames each view gets: enough for the page's own fade-in (100ms) to finish, so it's really drawn.
private const val SCORES_WARM_UP_FRAMES = 8

/**
 * Draws the Leaderboard - the Combined table, then the Game Mode cards - once, out of sight, so opening it isn't
 * the first time its code runs. That's most of the lag on a first open: a few rows take no time, but loading
 * and running the page's first-time code (the switch, the table, the lists, their scrollbars) lands on the
 * frame the page opens on. Placed on the menu under its opaque backdrop, in a 1dp clipped box, once the menu
 * has settled - the same idea as the Styles page's warm-up. It reads nothing from the database: the page is
 * drawn from a made-up board, through a view model that has no repository.
 */
@Composable
fun ScoresWarmUp(width: Dp, modifier: Modifier = Modifier) {
    if (scoresWarmedUp) return
    var warming by remember { mutableStateOf(false) }
    val viewModel = remember { ScoresViewModel(initialState = warmUpState()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(Unit) {
        lifecycle.delayWhileResumed(SCORES_WARM_UP_DELAY_MILLIS)
        warming = true
        for (view in LeaderboardView.entries) {
            viewModel.selectView(view)
            repeat(SCORES_WARM_UP_FRAMES) { withFrameNanos { } }
        }
        scoresWarmedUp = true
        warming = false
    }
    if (!warming) return
    Box(modifier = modifier.size(1.dp).clipToBounds()) {
        // As big as the page would be, so it lays out as it would there.
        val height = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
        Box(modifier = Modifier.requiredWidth(width).requiredHeight(height)) {
            ScoresScreen(viewModel = viewModel, onBack = {}, driftingDice = false)
        }
    }
}

/** A board of a few made-up scores, on the table and on two cards. */
private fun warmUpState(): ScoresUiState {
    fun entry(id: Long, score: Int, mode: GameMode) = ScoreEntry(
        id = id,
        playerName = "Player $id", // i18n: not translated - made-up warm-up data, never shown
        score = score,
        timestampEpochMillis = 0L,
        won = null,
        isPrimaryPlayer = false,
        fiveOfAKindCount = 1,
        zeroedCategoryCount = 0,
        upperSectionTotal = 0,
        chanceScore = 0,
        threeOfAKindScore = 0,
        fourOfAKindScore = 0,
        gameModeId = mode.id,
    )
    val standard = listOf(entry(1, 250, GameMode.STANDARD), entry(2, 200, GameMode.STANDARD))
    val boards = LEADERBOARD_MODES.associateWith { ModeBoard() } +
        (GameMode.STANDARD to ModeBoard(entries = standard, totalCount = standard.size)) +
        (GameMode.TRICOLOUR to ModeBoard(entries = listOf(entry(3, 180, GameMode.TRICOLOUR)), totalCount = 1))
    return ScoresUiState(entries = standard, totalCount = standard.size, isLoaded = true, modeBoards = boards)
}
