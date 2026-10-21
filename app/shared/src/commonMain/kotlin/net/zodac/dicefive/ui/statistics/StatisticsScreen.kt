package net.zodac.dicefive.ui.statistics

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.zodac.dicefive.data.scores.ModeStatistics
import net.zodac.dicefive.data.scores.PlayerStatistics
import net.zodac.dicefive.resources.Res
import net.zodac.dicefive.resources.common_cancel
import net.zodac.dicefive.resources.stats_average
import net.zodac.dicefive.resources.stats_best
import net.zodac.dicefive.resources.stats_by_mode
import net.zodac.dicefive.resources.stats_collapsed_spoken
import net.zodac.dicefive.resources.stats_delete_action
import net.zodac.dicefive.resources.stats_delete_confirm
import net.zodac.dicefive.resources.stats_delete_message
import net.zodac.dicefive.resources.stats_delete_title
import net.zodac.dicefive.resources.stats_empty
import net.zodac.dicefive.resources.stats_expanded_spoken
import net.zodac.dicefive.resources.stats_first_played
import net.zodac.dicefive.resources.stats_fives_scored
import net.zodac.dicefive.resources.stats_games
import net.zodac.dicefive.resources.stats_hide_action
import net.zodac.dicefive.resources.stats_mode
import net.zodac.dicefive.resources.stats_no_win_rate
import net.zodac.dicefive.resources.stats_overview
import net.zodac.dicefive.resources.stats_points
import net.zodac.dicefive.resources.stats_record_lost
import net.zodac.dicefive.resources.stats_record_solo
import net.zodac.dicefive.resources.stats_record_won
import net.zodac.dicefive.resources.stats_show_action
import net.zodac.dicefive.resources.stats_solo
import net.zodac.dicefive.resources.stats_sort_action
import net.zodac.dicefive.resources.stats_sort_ascending
import net.zodac.dicefive.resources.stats_sort_descending
import net.zodac.dicefive.resources.stats_sort_reverse_action
import net.zodac.dicefive.resources.stats_spoken_average
import net.zodac.dicefive.resources.stats_spoken_best
import net.zodac.dicefive.resources.stats_spoken_first_played
import net.zodac.dicefive.resources.stats_spoken_fives
import net.zodac.dicefive.resources.stats_spoken_games
import net.zodac.dicefive.resources.stats_spoken_record
import net.zodac.dicefive.resources.stats_spoken_solo
import net.zodac.dicefive.resources.stats_spoken_streaks
import net.zodac.dicefive.resources.stats_spoken_total_points
import net.zodac.dicefive.resources.stats_spoken_win_rate
import net.zodac.dicefive.resources.stats_title
import net.zodac.dicefive.resources.stats_total_points
import net.zodac.dicefive.resources.stats_win
import net.zodac.dicefive.resources.stats_win_rate
import net.zodac.dicefive.resources.stats_win_rate_value
import net.zodac.dicefive.resources.stats_win_streak
import net.zodac.dicefive.resources.stats_win_streak_value
import net.zodac.dicefive.ui.common.BACK_ARROW_OUTSET
import net.zodac.dicefive.ui.common.DiceFiveDialog
import net.zodac.dicefive.ui.common.LazyListScrollbar
import net.zodac.dicefive.ui.common.LocalLandscapePage
import net.zodac.dicefive.ui.common.LocalReduceMotion
import net.zodac.dicefive.ui.common.ScreenScaffold
import net.zodac.dicefive.ui.common.SegmentedChoiceRow
import net.zodac.dicefive.ui.common.grouped
import net.zodac.dicefive.ui.common.joinSentences
import net.zodac.dicefive.ui.common.pluralStringResource
import net.zodac.dicefive.ui.common.stringResource
import net.zodac.dicefive.ui.common.timestamp
import net.zodac.dicefive.ui.theme.color
import org.jetbrains.compose.resources.StringResource

/** Player cards to a row in landscape. */
private const val LANDSCAPE_COLUMNS = 2

/** More players than this open collapsed without measuring: they can't all fit. */
private const val MEASURE_LIMIT = 12

private val CARD_GAP = 12.dp
private val SCROLLBAR_ROOM = 12.dp

@Composable
fun StatisticsScreen(
    viewModel: StatisticsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingDeleteName by rememberSaveable { mutableStateOf<String?>(null) }
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    val players = remember(state.players, sortOrder) { sortOrder?.order(state.players).orEmpty() }

    ScreenScaffold(
        title = stringResource(Res.string.stats_title),
        onBack = onBack,
        modifier = modifier,
        wide = true,
        // At the bar's end, level with the cards' end edge in landscape.
        actions = {
            val order = sortOrder
            if (state.isLoaded && state.players.isNotEmpty() && order != null) {
                Box(modifier = Modifier.padding(end = if (LocalLandscapePage.current) BACK_ARROW_OUTSET else 0.dp)) {
                    SortMenu(sortOrder = order, onPick = { viewModel.setSortOrder(order.pick(it)) })
                }
            }
        },
    ) {
        // Nothing but the title bar until the stats are read, so "No stats yet" never flashes up
        // before the player cards.
        if (!state.isLoaded || sortOrder == null) return@ScreenScaffold
        if (state.players.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(Res.string.stats_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                )
            }
        } else {
            val listState = rememberLazyListState()
            // Two cards to a row in landscape, each opening on its own (the other keeps its height); one otherwise.
            val columns = if (LocalLandscapePage.current) LANDSCAPE_COLUMNS else 1
            // Whether every card fits on screen opened: null until measured.
            var allFit by remember { mutableStateOf<Boolean?>(if (players.size > MEASURE_LIMIT) false else null) }
            // The names of the open cards. Dealt afresh whenever the order changes (or the columns do, or the fit is known):
            // every card if they all fit, else the top card of each column - the first row's. Toggled from there.
            val openNames = remember(sortOrder?.stored, columns, allFit) {
                val dealt = if (allFit == true) players else players.take(columns)
                mutableStateListOf<String>().apply { addAll(dealt.map { it.playerName }) }
            }

            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                if (allFit == null) {
                    StatsFitMeasurer(players, columns, onMeasured = { allFit = it })
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        // Room on the right for the scrollbar so it doesn't sit on top of a card's edge.
                        contentPadding = PaddingValues(end = SCROLLBAR_ROOM),
                        verticalArrangement = Arrangement.spacedBy(CARD_GAP),
                    ) {
                        items(players.chunked(columns), key = { row -> row.first().playerName }) { row ->
                            StatsRow(row, columns) { player ->
                                PlayerStatsCardContent(
                                    player,
                                    expanded = player.playerName in openNames,
                                    onExpandedChange = { open ->
                                        if (open) openNames.add(player.playerName) else openNames.remove(player.playerName)
                                    },
                                    onDelete = { pendingDeleteName = player.playerName },
                                )
                            }
                        }
                    }
                }
                LazyListScrollbar(listState = listState)
            }
        }

        pendingDeleteName?.let { playerName ->
            DiceFiveDialog(
                icon = Icons.Filled.DeleteForever,
                title = stringResource(Res.string.stats_delete_title),
                message = stringResource(Res.string.stats_delete_message, playerName),
                confirmLabel = stringResource(Res.string.stats_delete_confirm),
                onConfirm = {
                    viewModel.dismissPlayer(playerName)
                    pendingDeleteName = null
                },
                dismissLabel = stringResource(Res.string.common_cancel),
                onDismiss = { pendingDeleteName = null },
                onDismissRequest = { pendingDeleteName = null },
            )
        }
    }
}

/**
 * The sort button and its menu: a row for each [StatisticsSort], the current one marked with an arrow for its direction
 * (up for ascending, down for descending). Picking the current one reverses it.
 */
@Composable
private fun SortMenu(sortOrder: StatisticsSortOrder, onPick: (StatisticsSort) -> Unit) {
    val (sort, ascending) = sortOrder
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Sort,
                contentDescription = stringResource(Res.string.stats_sort_action),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (option in StatisticsSort.entries) {
                val selected = option == sort
                val label = stringResource(option.label)
                val state = stringResource(if (ascending) Res.string.stats_sort_ascending else Res.string.stats_sort_descending)
                val reverseLabel = stringResource(Res.string.stats_sort_reverse_action)
                DropdownMenuItem(
                    text = {
                        Text(
                            text = label,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    trailingIcon = {
                        // Decorative: the direction is the row's spoken state.
                        Icon(
                            imageVector = if (ascending) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                            contentDescription = null,
                            tint = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        )
                    },
                    onClick = {
                        onPick(option)
                        open = false
                    },
                    modifier = Modifier.clearAndSetSemantics {
                        contentDescription = label
                        role = Role.Button
                        if (selected) {
                            stateDescription = state
                            onClick(label = reverseLabel) {
                                onPick(option)
                                open = false
                                true
                            }
                        } else {
                            onClick(label = null) {
                                onPick(option)
                                open = false
                                true
                            }
                        }
                    },
                )
            }
        }
    }
}

/** A row of up to [columns] cards; a last place left empty keeps its card the others' width. */
@Composable
private fun StatsRow(row: List<PlayerStatistics>, columns: Int, card: @Composable (PlayerStatistics) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(CARD_GAP)) {
        for (player in row) {
            Box(modifier = Modifier.weight(1f)) { card(player) }
        }
        repeat(columns - row.size) { Spacer(modifier = Modifier.weight(1f)) }
    }
}

/**
 * Lays out every card opened, unseen and taking no room, and reports through [onMeasured] whether they all fit in the
 * height it is given. The screen shows the real list once it has.
 */
@Composable
private fun StatsFitMeasurer(players: List<PlayerStatistics>, columns: Int, onMeasured: (Boolean) -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(CARD_GAP),
        modifier = Modifier
            .clearAndSetSemantics {}
            .layout { measurable, constraints ->
                val room = SCROLLBAR_ROOM.roundToPx()
                val placeable = measurable.measure(
                    Constraints(maxWidth = (constraints.maxWidth - room).coerceAtLeast(0), maxHeight = Constraints.Infinity),
                )
                onMeasured(placeable.height <= constraints.maxHeight)
                layout(0, 0) {}
            },
    ) {
        for (row in players.chunked(columns)) {
            StatsRow(row, columns) { player -> PlayerStatsCardContent(player, expanded = true, onExpandedChange = {}, onDelete = {}) }
        }
    }
}

/** [PlayerStatsCardContent] that keeps its own open state, starting as [initiallyExpanded] says. */
@Composable
fun PlayerStatsCard(player: PlayerStatistics, onDelete: () -> Unit, initiallyExpanded: Boolean = false) {
    var expanded by rememberSaveable(player.playerName) { mutableStateOf(initiallyExpanded) }
    PlayerStatsCardContent(player, expanded = expanded, onExpandedChange = { expanded = it }, onDelete = onDelete)
}

/**
 * One player's card, open or closed as [expanded] says. Closed it is just their name and best score (gold, in points), with a dropdown arrow
 * after the score; tapping that header opens the rest, under an Overview / By mode switch. The delete button at the
 * header's end, open or closed, is a control of its own.
 */
@Composable
private fun PlayerStatsCardContent(player: PlayerStatistics, expanded: Boolean, onExpandedChange: (Boolean) -> Unit, onDelete: () -> Unit) {
    var view by rememberSaveable(player.playerName) { mutableStateOf(StatsView.OVERVIEW) }
    val deleteLabel = stringResource(Res.string.stats_delete_action, player.playerName)
    val spoken = stringResource(Res.string.stats_spoken_best, player.playerName, player.maxScore.grouped())
    val toggleLabel = stringResource(if (expanded) Res.string.stats_hide_action else Res.string.stats_show_action)
    val stateText = stringResource(if (expanded) Res.string.stats_expanded_spoken else Res.string.stats_collapsed_spoken)
    val reduceMotion = LocalReduceMotion.current
    // The header's height, which a name on two lines makes taller: the delete button centres on it.
    var headerHeightPx by remember { mutableIntStateOf(0) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Box {
            Column(modifier = if (reduceMotion) Modifier else Modifier.animateContentSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { headerHeightPx = it.height }
                        // The name and best score in words; its state is spoken and its action labelled. The delete
                        // button is a sibling, so this doesn't swallow it.
                        .clearAndSetSemantics {
                            contentDescription = spoken
                            stateDescription = stateText
                            onClick(label = toggleLabel) {
                                onExpandedChange(!expanded)
                                true
                            }
                        }
                        .clickable(onClickLabel = toggleLabel) { onExpandedChange(!expanded) }
                        .padding(CARD_PADDING)
                        // Leaves the delete button's room at the end.
                        .padding(end = DELETE_BUTTON_ROOM),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val name = @Composable { modifier: Modifier ->
                        Text(
                            text = player.playerName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            // A second line before an ellipsis, so a name at a large font wraps rather than losing its end.
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = modifier,
                        )
                    }
                    val best = @Composable {
                        Text(
                            text = pointsText(player.maxScore),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.End,
                        )
                    }
                    // Above STACKED_FONT_SCALE the score goes under the name, which keeps the row's width to itself.
                    if (LocalDensity.current.fontScale > STACKED_FONT_SCALE) {
                        Column(modifier = Modifier.weight(1f)) {
                            name(Modifier)
                            best()
                        }
                    } else {
                        name(Modifier.weight(1f))
                        best()
                    }
                    // Decorative: the header's own state and action are spoken (above).
                    Icon(
                        imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (expanded) {
                    Column(
                        modifier = Modifier.padding(start = CARD_PADDING, end = CARD_PADDING, bottom = CARD_PADDING),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SegmentedChoiceRow(
                            options = StatsView.entries,
                            selected = view,
                            onSelect = { view = it },
                            label = { stringResource(it.label) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        when (view) {
                            StatsView.OVERVIEW -> Overview(player)
                            StatsView.BY_MODE -> ByMode(player)
                        }
                    }
                }
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 4.dp)
                    .offset { IntOffset(0, (headerHeightPx - DELETE_BUTTON_SIZE.roundToPx()) / 2) },
            ) {
                Icon(
                    imageVector = Icons.Filled.DeleteForever,
                    contentDescription = deleteLabel,
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** The two halves of an opened card, picked by its switch. */
private enum class StatsView(val label: StringResource) {
    OVERVIEW(Res.string.stats_overview),
    BY_MODE(Res.string.stats_by_mode),
}

/**
 * The player's overall stats: games, win rate and average large; the won/lost/solo split as a bar with its key; then
 * the win streak, 5x scored, total points and when they first played. Two stops for TalkBack: the numbers with the split, then the
 * rest.
 */
@Composable
private fun Overview(player: PlayerStatistics) {
    val numbers = buildList {
        add(stringResource(Res.string.stats_spoken_record, player.gamesPlayed, player.gamesWon, player.gamesLost))
        add(pluralStringResource(Res.plurals.stats_spoken_solo, player.soloGames, player.soloGames))
        player.winRatePercent?.let { add(stringResource(Res.string.stats_spoken_win_rate, it)) }
        add(stringResource(Res.string.stats_spoken_average, player.averageScore.grouped()))
    }
    val firstPlayed = timestamp(player.firstPlayedEpochMillis)
    val details = buildList {
        if (player.bestWinStreak > 0) add(stringResource(Res.string.stats_spoken_streaks, player.currentWinStreak, player.bestWinStreak))
        add(pluralStringResource(Res.plurals.stats_spoken_fives, player.fiveOfAKindCount, player.fiveOfAKindCount))
        add(stringResource(Res.string.stats_spoken_total_points, player.totalScore.grouped()))
        add(stringResource(Res.string.stats_spoken_first_played, firstPlayed))
    }
    val numbersSpoken = joinSentences(numbers)
    val detailsSpoken = joinSentences(details)

    Column(
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = numbersSpoken },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HeadlineRow {
            Headline(value = AnnotatedString(player.gamesPlayed.grouped()), label = stringResource(Res.string.stats_games))
            Headline(value = AnnotatedString(winRateText(player.winRatePercent)), label = stringResource(Res.string.stats_win_rate))
            Headline(value = pointsText(player.averageScore), label = stringResource(Res.string.stats_average))
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ShareBar(
                listOf(
                    player.gamesWon to MaterialTheme.colorScheme.tertiary,
                    player.gamesLost to MaterialTheme.colorScheme.error,
                    player.soloGames to MaterialTheme.colorScheme.outline,
                ),
            )
            KeyRow {
                KeyItem(MaterialTheme.colorScheme.tertiary, pluralStringResource(Res.plurals.stats_record_won, player.gamesWon, player.gamesWon))
                KeyItem(MaterialTheme.colorScheme.error, pluralStringResource(Res.plurals.stats_record_lost, player.gamesLost, player.gamesLost))
                KeyItem(MaterialTheme.colorScheme.outline, pluralStringResource(Res.plurals.stats_record_solo, player.soloGames, player.soloGames))
            }
        }
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

    Column(
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = detailsSpoken },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DetailLine(
            label = stringResource(Res.string.stats_win_streak),
            value = if (player.bestWinStreak > 0) {
                stringResource(Res.string.stats_win_streak_value, player.currentWinStreak, player.bestWinStreak)
            } else {
                stringResource(Res.string.stats_no_win_rate)
            },
        )
        DetailLine(label = stringResource(Res.string.stats_fives_scored), value = player.fiveOfAKindCount.grouped())
        DetailLine(label = stringResource(Res.string.stats_total_points), value = player.totalScore.grouped())
        DetailLine(label = stringResource(Res.string.stats_first_played), value = firstPlayed)
    }
}

/**
 * A table of the player's games in each mode they've played: games, win rate, solo games and best score, each name in
 * its mode's colour. Under it, a bar of every mode's share of the games in the same colours. Above
 * [STACKED_FONT_SCALE] a name takes its own line, its numbers under the headings on the next. TalkBack reads each row
 * as one stop, in full, so the headings and the bar are silent.
 */
@Composable
private fun ByMode(player: PlayerStatistics) {
    val stacked = LocalDensity.current.fontScale > STACKED_FONT_SCALE
    val headings = listOf(
        stringResource(Res.string.stats_games),
        stringResource(Res.string.stats_win),
        stringResource(Res.string.stats_solo),
        stringResource(Res.string.stats_best),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth().clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!stacked) TableCell(stringResource(Res.string.stats_mode), heading = true, modifier = Modifier.weight(1f))
            headings.forEach { NumberCell(it, stacked, heading = true) }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        player.modes.forEach { mode -> ModeRow(mode, stacked) }
    }
    ShareBar(player.modes.map { it.gamesPlayed to it.gameMode.color })
}

@Composable
private fun ModeRow(mode: ModeStatistics, stacked: Boolean) {
    val name = stringResource(mode.gameMode.displayName)
    val spoken = joinSentences(
        buildList {
            add(stringResource(Res.string.stats_spoken_best, name, mode.maxScore.grouped()))
            add(pluralStringResource(Res.plurals.stats_spoken_games, mode.gamesPlayed, mode.gamesPlayed))
            mode.winRatePercent?.let { add(stringResource(Res.string.stats_spoken_win_rate, it)) }
            add(pluralStringResource(Res.plurals.stats_spoken_solo, mode.soloGames, mode.soloGames))
        },
    )
    val numbers = listOf(mode.gamesPlayed.grouped(), winRateText(mode.winRatePercent), mode.soloGames.grouped(), mode.maxScore.grouped())
    val nameText = @Composable { modifier: Modifier ->
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = mode.gameMode.color,
            modifier = modifier,
        )
    }
    val semantics = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = spoken }
    if (stacked) {
        Column(modifier = semantics) {
            nameText(Modifier)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                numbers.forEach { NumberCell(it, stacked = true) }
            }
        }
    } else {
        Row(modifier = semantics, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            nameText(Modifier.weight(1f))
            numbers.forEach { NumberCell(it, stacked = false) }
        }
    }
}

/** A number column's cell: an even share of the row when stacked, else at least [NUMBER_COLUMN_MIN_WIDTH] after the name. */
@Composable
private fun RowScope.NumberCell(text: String, stacked: Boolean, heading: Boolean = false) {
    TableCell(
        text = text,
        heading = heading,
        numeric = true,
        modifier = if (stacked) Modifier.weight(1f) else Modifier.widthIn(min = NUMBER_COLUMN_MIN_WIDTH),
    )
}

@Composable
private fun TableCell(text: String, modifier: Modifier, heading: Boolean = false, numeric: Boolean = false) {
    Text(
        text = text,
        style = if (heading) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium,
        fontWeight = if (heading) FontWeight.Normal else FontWeight.Bold,
        color = if (heading) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        textAlign = if (numeric) TextAlign.End else TextAlign.Start,
        modifier = modifier,
    )
}

/** A win rate as shown: a percentage, or a dash when every game was solo. */
@Composable
private fun winRateText(percent: Int?): String =
    if (percent == null) stringResource(Res.string.stats_no_win_rate) else stringResource(Res.string.stats_win_rate_value, percent)

/**
 * [points], thousands-separated, with the language's short unit ([stats_points]: "1,287pts"). Whatever sits around the
 * number is drawn at [UNIT_SIZE] of it, so the number reads first.
 */
@Composable
private fun pointsText(points: Int): AnnotatedString {
    val number = points.grouped()
    val text = stringResource(Res.string.stats_points, number)
    val start = text.indexOf(number)
    return buildAnnotatedString {
        append(text)
        if (start >= 0) {
            val end = start + number.length
            if (start > 0) addStyle(SpanStyle(fontSize = UNIT_SIZE), 0, start)
            if (end < text.length) addStyle(SpanStyle(fontSize = UNIT_SIZE), end, text.length)
        }
    }
}

/** One rounded bar split into each count's share, in its colour; a count of 0 takes no room. */
@Composable
private fun ShareBar(shares: List<Pair<Int, Color>>) {
    Row(modifier = Modifier.fillMaxWidth().height(SHARE_BAR_HEIGHT).clip(RoundedCornerShape(SHARE_BAR_HEIGHT / 2))) {
        shares.filter { it.first > 0 }.forEach { (count, colour) ->
            Box(modifier = Modifier.weight(count.toFloat()).fillMaxHeight().background(colour))
        }
    }
}

// FlowRows: at a large font the items no longer fit across, and the last ones drop to a second line rather than
// overlapping.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeadlineRow(content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun Headline(value: AnnotatedString, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 4.dp)) {
        Text(text = value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeyRow(content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}

@Composable
private fun KeyItem(colour: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(modifier = Modifier.size(KEY_SWATCH_SIZE).clip(RoundedCornerShape(2.dp)).background(colour))
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** A label and its value on one line; above [STACKED_FONT_SCALE], the label over the value. */
@Composable
private fun DetailLine(label: String, value: String) {
    val labelText = @Composable { modifier: Modifier ->
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
    }
    val valueText = @Composable {
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
    }
    if (LocalDensity.current.fontScale > STACKED_FONT_SCALE) {
        Column(modifier = Modifier.fillMaxWidth()) {
            labelText(Modifier)
            valueText()
        }
    } else {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            labelText(Modifier.weight(1f))
            valueText()
        }
    }
}

/** The card's padding, and the delete button's own size (the 48dp minimum touch target). */
private val CARD_PADDING = 16.dp
private val DELETE_BUTTON_SIZE = 48.dp

/** Space left at the end of a card's header row for its delete button (48dp, less the card's own padding). */
private val DELETE_BUTTON_ROOM = 40.dp

private val SHARE_BAR_HEIGHT = 10.dp
private val KEY_SWATCH_SIZE = 10.dp
private val NUMBER_COLUMN_MIN_WIDTH = 44.dp

/** The font scale above which the header puts the best score under the name, Overview's details their label over
 * their value, and By mode each name over its numbers. */
private const val STACKED_FONT_SCALE = 1.3f

/** The size of a score's unit ([pointsText]) against its number. */
private val UNIT_SIZE = 0.75.em
