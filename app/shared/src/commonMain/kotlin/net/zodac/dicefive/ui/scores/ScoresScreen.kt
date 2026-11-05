package net.zodac.dicefive.ui.scores

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.zodac.dicefive.data.scores.LeaderboardSource
import net.zodac.dicefive.data.scores.ScoreEntry
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.ModePage
import net.zodac.dicefive.online.GameServices
import net.zodac.dicefive.online.GameServicesState
import net.zodac.dicefive.online.LocalGameServices
import net.zodac.dicefive.resources.Res
import net.zodac.dicefive.resources.scores_board_empty
import net.zodac.dicefive.resources.scores_board_label
import net.zodac.dicefive.resources.scores_global
import net.zodac.dicefive.resources.scores_global_cd
import net.zodac.dicefive.resources.scores_title
import net.zodac.dicefive.ui.common.HoldPageCover
import net.zodac.dicefive.ui.common.LocalLandscapePage
import net.zodac.dicefive.ui.common.ModePagePicker
import net.zodac.dicefive.ui.common.OffscreenWarmUp
import net.zodac.dicefive.ui.common.ScreenScaffold
import net.zodac.dicefive.ui.common.ShrinkThenWrapText
import net.zodac.dicefive.ui.common.SoraFontFamily
import net.zodac.dicefive.ui.common.UP_DOWN_BUTTONS_WIDTH
import net.zodac.dicefive.ui.common.modePageName
import net.zodac.dicefive.ui.common.stringResource
import net.zodac.dicefive.ui.icons.Symbols

/** Either side of a board's title. */
private val TITLE_HORIZONTAL_PADDING = 12.dp

private val GLOBAL_ICON_SIZE = 18.dp
private val GLOBAL_ICON_GAP = 6.dp

/** A labelled Global button's width besides its label: the icon, the gap, and a text button's 12dp either side. */
private val GLOBAL_BUTTON_CHROME_WIDTH = GLOBAL_ICON_SIZE + GLOBAL_ICON_GAP + 24.dp

/**
 * The Leaderboard: a drop-down at the top picks a game mode's board (Standard's to start) or Combined, and the board's table is under
 * it. Every board is the same [LeaderboardTable], so the top three are in gold, silver, and bronze on each. The pick is
 * remembered ([ScoresViewModel]).
 */
@Composable
fun ScoresScreen(
    viewModel: ScoresViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    driftingDice: Boolean = true,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ScreenScaffold(title = stringResource(Res.string.scores_title), onBack = onBack, modifier = modifier, driftingDice = driftingDice, wide = true) {
        // Nothing but the title bar until the board is read, so "No scores yet" never flashes up
        // before a leaderboard that has some - and the page cover kept up over it meanwhile.
        val board = state.board
        if (!state.isLoaded || board == null) return@ScreenScaffold HoldPageCover()
        val landscape = LocalLandscapePage.current
        val choice = state.choice
        val picker = @Composable { pickerModifier: Modifier ->
            ModePagePicker(
                selected = choice,
                onSelect = viewModel::selectChoice,
                label = stringResource(Res.string.scores_board_label),
                modifier = pickerModifier,
            )
        }
        if (landscape) {
            // In landscape the drop-down shares a row with the page controls, so the table has the height under them.
            // The table, as one, keeps to a readable width.
            Row(
                modifier = Modifier.widthIn(max = LANDSCAPE_TABLE_WIDTH).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                picker(Modifier.weight(1f))
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    LeaderboardPagination(board, viewModel::previousPage, viewModel::nextPage)
                }
            }
        } else {
            picker(Modifier.fillMaxWidth())
        }
        val services = LocalGameServices.current
        // A mode's board opens that mode's board on the service while signed in to one.
        val online = services.state.collectAsStateWithLifecycle().value is GameServicesState.SignedIn && services.canShowLeaderboardUi
        val boardName = modePageName(choice)
        // Each board its own list, so a new one starts at its top: a list kept across boards holds its place by row, and
        // would scroll a board to wherever the last one's top row is (Standard's best is far down Combined).
        key(choice) {
            LeaderboardTable(
                board = board,
                emptyText = stringResource(Res.string.scores_board_empty, boardName),
                // Rows of every mode share Combined's table: each name in its mode's colour, and its mode in the long press.
                combined = choice == ModePage.Combined,
                onPrevious = viewModel::previousPage,
                onNext = viewModel::nextPage,
                landscape = landscape,
                header = { scrollButtons ->
                    BoardTitle(
                        title = boardName,
                        mode = (choice as? ModePage.Mode)?.mode,
                        services = services.takeIf { online },
                        scrollButtons = scrollButtons,
                    )
                },
            )
        }
    }
}

/**
 * A board's title, a heading, at the row's start, with [scrollButtons] at its end while the page scrolls, in the same place signed in
 * or out, and - for a [mode]'s board while signed in to a service ([services]) - its Global button in its middle. Where a
 * long title reaches the middle, the button moves along to clear it. Its label goes before the title's size does: where they
 * won't all fit on the line, it's the icon alone. Neither takes any height: the title sets the row's.
 */
@Composable
private fun BoardTitle(title: String, mode: GameMode?, services: GameServices?, scrollButtons: (@Composable () -> Unit)?) {
    val titleStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = SoraFontFamily, fontWeight = FontWeight.Bold)
    val titleText = @Composable { titleModifier: Modifier ->
        val placed = titleModifier.padding(horizontal = TITLE_HORIZONTAL_PADDING, vertical = 4.dp).semantics { heading() }
        // Beside the arrows, a long title steps down to stay on its one line.
        if (scrollButtons != null) {
            ShrinkThenWrapText(text = title, style = titleStyle, color = MaterialTheme.colorScheme.primary, modifier = placed)
        } else {
            Text(text = title, style = titleStyle, color = MaterialTheme.colorScheme.primary, modifier = placed)
        }
    }
    val hasGlobal = mode != null && services != null
    if (!hasGlobal && scrollButtons == null) return titleText(Modifier.fillMaxWidth())
    BoxWithConstraints {
        val measurer = rememberTextMeasurer()
        val label = stringResource(Res.string.scores_global)
        val labelStyle = MaterialTheme.typography.labelLarge
        val arrowsWidth = if (scrollButtons != null) UP_DOWN_BUTTONS_WIDTH else 0.dp
        val density = LocalDensity.current
        val titleWidth = with(density) { measurer.measure(title, titleStyle).size.width.toDp() } + TITLE_HORIZONTAL_PADDING * 2
        val fitsLabel = with(density) {
            val buttonWidth = measurer.measure(label, labelStyle).size.width.toDp() + GLOBAL_BUTTON_CHROME_WIDTH
            titleWidth + buttonWidth + arrowsWidth <= maxWidth
        }
        Layout(
            content = {
                titleText(Modifier)
                if (mode != null && services != null) GlobalLeaderboardButton(mode, services, label.takeIf { fitsLabel })
                if (scrollButtons != null) Box(Modifier.takingNoHeight()) { scrollButtons() }
            },
        ) { measurables, constraints ->
            val width = constraints.maxWidth
            val loose = constraints.copy(minWidth = 0, minHeight = 0)
            val global = if (hasGlobal) measurables[1].measure(loose) else null
            val arrows = if (scrollButtons != null) measurables.last().measure(loose) else null
            val arrowsStart = width - (arrows?.width ?: 0)
            val titlePlaceable = measurables[0].measure(loose.copy(maxWidth = arrowsStart - (global?.width ?: 0)))
            val rowHeight = titlePlaceable.height
            layout(width, rowHeight) {
                titlePlaceable.placeRelative(0, 0)
                global?.placeRelative(
                    x = ((width - global.width) / 2).coerceIn(minOf(titlePlaceable.width, titleWidth.roundToPx()), arrowsStart - global.width),
                    y = rowHeight / 2,
                )
                arrows?.placeRelative(arrowsStart, rowHeight / 2)
            }
        }
    }
}

/**
 * Opens [mode]'s leaderboard on the service: the globe and [label], or the globe alone where [label] is null. It takes
 * no height of its own: the title sets the row's, and the button is centred on it, spilling into the card's padding,
 * so a card is the same size signed in or out.
 */
@Composable
private fun GlobalLeaderboardButton(mode: GameMode, services: GameServices, label: String?) {
    val description = stringResource(Res.string.scores_global_cd, stringResource(mode.displayName), services.serviceName)
    val onClick = { services.showLeaderboardUi(mode.onlineLeaderboardKey) }
    val modifier = Modifier.takingNoHeight().semantics { contentDescription = description }
    if (label == null) {
        IconButton(onClick = onClick, modifier = modifier) {
            Icon(imageVector = Symbols.Public, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        return
    }
    TextButton(onClick = onClick, modifier = modifier) {
        Icon(imageVector = Symbols.Public, contentDescription = null, modifier = Modifier.size(GLOBAL_ICON_SIZE))
        Spacer(modifier = Modifier.size(GLOBAL_ICON_GAP))
        Text(label, maxLines = 1)
    }
}

/** Centred on the row it's in, spilling into the card's padding, so the row is as tall as it would be without this. */
private fun Modifier.takingNoHeight(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minHeight = 0))
    layout(placeable.width, 0) { placeable.place(0, -placeable.height / 2) }
}

/** Set once [ScoresWarmUp] has drawn every board it draws, for the life of the process. */
private var scoresWarmedUp = false

/**
 * For tests only: forgets that [ScoresWarmUp] has run, so a test can watch it run. The flag lives for the
 * whole process, and the unit tests share one: any earlier test that left the menu up long enough has
 * already used it up.
 */
fun resetScoresWarmUpForTest() {
    scoresWarmedUp = false
}

// After the menu's own entrance, and clear of the Styles warm-up, which starts at 600ms and runs for a few hundred.
private const val SCORES_WARM_UP_DELAY_MILLIS = 2000L

// Frames each board gets: enough for the page's own fade-in (100ms) to finish, so it's really drawn.
private const val SCORES_WARM_UP_FRAMES = 8

/** The boards the warm-up draws: Standard's, which the page opens on, then Combined, whose rows are each coloured by mode. */
private val WARM_UP_CHOICES = listOf(ModePage.STANDARD, ModePage.Combined)

/**
 * Draws the Leaderboard - Standard's board, then Combined - once, out of sight, so opening it isn't
 * the first time its code runs. That's most of the lag on a first open: a few rows take no time, but loading
 * and running the page's first-time code (the drop-down, the table, the lists, their scrollbars) lands on the
 * frame the page opens on. Placed on the menu under its opaque backdrop, in a 1dp clipped box, once the menu
 * has settled - the same idea as the Styles page's warm-up. It reads nothing from the database: the page is
 * drawn from a made-up board, through a view model that reads a fixed board instead.
 */
@Composable
fun ScoresWarmUp(width: Dp, modifier: Modifier = Modifier) {
    if (scoresWarmedUp) return
    val viewModel = remember { ScoresViewModel(WarmUpBoard) }
    // As big as the page would be, so it lays out as it would there.
    val height = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    OffscreenWarmUp<ModePage>(
        key = Unit,
        delayMillis = SCORES_WARM_UP_DELAY_MILLIS,
        pageSize = DpSize(width, height),
        modifier = modifier,
        run = {
            for (choice in WARM_UP_CHOICES) {
                viewModel.selectChoice(choice)
                draw(choice, frames = SCORES_WARM_UP_FRAMES)
            }
            scoresWarmedUp = true
        },
    ) {
        ScoresScreen(viewModel = viewModel, onBack = {}, driftingDice = false)
    }
}

/** A board of a few made-up scores, on two modes' boards. */
private object WarmUpBoard : LeaderboardSource {
    private fun entry(id: Long, score: Int, mode: GameMode) = ScoreEntry(
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

    private val standard = listOf(entry(1, 250, GameMode.STANDARD), entry(2, 200, GameMode.STANDARD))
    private val tricolour = listOf(entry(3, 180, GameMode.TRICOLOUR))

    private fun forMode(gameMode: GameMode): List<ScoreEntry> = when (gameMode) {
        GameMode.STANDARD -> standard
        GameMode.TRICOLOUR -> tricolour
        else -> emptyList()
    }

    override suspend fun page(pageIndex: Int, pageSize: Int): List<ScoreEntry> = if (pageIndex == 0) standard + tricolour else emptyList()

    override suspend fun totalCount(): Int = standard.size + tricolour.size

    override suspend fun pageForMode(gameMode: GameMode, pageIndex: Int, pageSize: Int): List<ScoreEntry> =
        if (pageIndex == 0) forMode(gameMode) else emptyList()

    override suspend fun totalCountForMode(gameMode: GameMode): Int = forMode(gameMode).size
}
