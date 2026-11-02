package net.zodac.dicefive.ui.scores

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.zodac.dicefive.app.AppContainer
import net.zodac.dicefive.data.scores.LeaderboardSource
import net.zodac.dicefive.data.scores.SCORES_PAGE_SIZE
import net.zodac.dicefive.data.scores.ScoreEntry
import net.zodac.dicefive.model.GameMode

/** The game modes that can have a board on the Leaderboard - every one, including those that stay off the Combined table. */
val LEADERBOARD_MODES: List<GameMode> = GameMode.entries.toList()

/**
 * A board the Leaderboard can show, picked from the drop-down at the top of its screen: [Combined], every mode's scores in
 * one ranked table, or one [Mode]'s own. Every board is shown the same way (`LeaderboardTable`), so a board added here
 * needs only a source for its pages.
 */
sealed interface LeaderboardChoice {
    /** What the choice is stored as (the `leaderboard_choice` preference): [Combined]'s own word, or the mode's id. */
    val stored: String

    data object Combined : LeaderboardChoice {
        override val stored = "combined"
    }

    data class Mode(val mode: GameMode) : LeaderboardChoice {
        override val stored: String get() = mode.id
    }

    companion object {
        /** Every board, in the drop-down's order: Combined first, then a board for each mode. */
        val all: List<LeaderboardChoice> = listOf(Combined) + LEADERBOARD_MODES.map(::Mode)

        /** The board [stored] names; Combined where nothing was picked yet or the mode is no longer known. */
        fun parse(stored: String?): LeaderboardChoice = GameMode.fromId(stored ?: return Combined)?.let(::Mode) ?: Combined
    }
}

/** How many pages [totalCount] rows make up - at least one, so an empty board still has a "page 1". */
private fun pageCount(totalCount: Int): Int = ((totalCount - 1) / SCORES_PAGE_SIZE + 1).coerceAtLeast(1)

/** One board: the page of its scores on view, which page that is and how many rows it has. */
data class LeaderboardPage(
    val entries: List<ScoreEntry> = emptyList(),
    val pageIndex: Int = 0,
    val totalCount: Int = 0,
) {
    val totalPages: Int get() = pageCount(totalCount)
    val hasNextPage: Boolean get() = pageIndex < totalPages - 1
    val hasPreviousPage: Boolean get() = pageIndex > 0
}

data class ScoresUiState(
    /** The board on view - the one picked, once its first page has been read. */
    val choice: LeaderboardChoice = LeaderboardChoice.Combined,
    /** Every board read so far, by choice. A board is read the first time it is picked, and kept. */
    val boards: Map<LeaderboardChoice, LeaderboardPage> = emptyMap(),
    /** Whether the board on view has been read yet - until then an empty board means "not known
     * yet", not "no scores", and the screen shows neither the table nor its empty message. */
    val isLoaded: Boolean = false,
) {
    /** The page of the board on view, or null until it is read. */
    val board: LeaderboardPage? get() = boards[choice]
}

/**
 * Reads its boards from [source]: the score repository, or [ScoresWarmUp]'s made-up board. It opens on [savedChoice]'s
 * board once that has loaded, and hands each pick to [saveChoice] to keep.
 */
class ScoresViewModel(
    private val source: LeaderboardSource,
    savedChoice: StateFlow<LeaderboardChoice?> = MutableStateFlow(LeaderboardChoice.Combined),
    private val saveChoice: suspend (LeaderboardChoice) -> Unit = {},
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScoresUiState())
    val uiState: StateFlow<ScoresUiState> = _uiState.asStateFlow()

    /**
     * Each board's page load under way. A new page's load cancels the one before, so a slow earlier page can't land over a
     * later one.
     */
    private val loads = HashMap<LeaderboardChoice, Job>()

    /** The pick under way: a board not read yet is read before the screen moves to it. */
    private var selecting: Job? = null

    init {
        selecting = viewModelScope.launch { open(savedChoice.filterNotNull().first(), persist = false) }
    }

    /** Moves to [choice]'s board, so the board on view stays up until the new one is read and then gives way in one frame. */
    fun selectChoice(choice: LeaderboardChoice) {
        selecting?.cancel()
        selecting = viewModelScope.launch { open(choice, persist = true) }
    }

    private suspend fun open(choice: LeaderboardChoice, persist: Boolean) {
        if (persist) saveChoice(choice)
        val board = _uiState.value.boards[choice] ?: read(choice, 0)
        _uiState.update { it.copy(choice = choice, boards = if (choice in it.boards) it.boards else it.boards + (choice to board), isLoaded = true) }
    }

    fun nextPage() {
        val state = _uiState.value
        val board = state.board ?: return
        if (board.hasNextPage) loadPage(state.choice, board.pageIndex + 1)
    }

    fun previousPage() {
        val state = _uiState.value
        val board = state.board ?: return
        if (board.hasPreviousPage) loadPage(state.choice, board.pageIndex - 1)
    }

    private fun loadPage(choice: LeaderboardChoice, pageIndex: Int) {
        loads[choice]?.cancel()
        loads[choice] = viewModelScope.launch {
            val board = read(choice, pageIndex)
            _uiState.update { it.copy(boards = it.boards + (choice to board)) }
        }
    }

    private suspend fun read(choice: LeaderboardChoice, pageIndex: Int): LeaderboardPage = when (choice) {
        LeaderboardChoice.Combined -> LeaderboardPage(source.page(pageIndex), pageIndex, source.totalCount())
        is LeaderboardChoice.Mode -> LeaderboardPage(source.pageForMode(choice.mode, pageIndex), pageIndex, source.totalCountForMode(choice.mode))
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ScoresViewModel(
                    source = container.scoreRepository,
                    savedChoice = container.leaderboardChoice,
                    saveChoice = { container.settingsRepository.setLeaderboardChoice(it.stored) },
                )
            }
        }
    }
}
