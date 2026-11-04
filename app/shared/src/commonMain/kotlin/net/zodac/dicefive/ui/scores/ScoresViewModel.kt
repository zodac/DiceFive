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
import net.zodac.dicefive.data.orNullOnFailure
import net.zodac.dicefive.data.scores.LeaderboardSource
import net.zodac.dicefive.data.scores.SCORES_PAGE_SIZE
import net.zodac.dicefive.data.scores.ScoreEntry
import net.zodac.dicefive.model.ModePage

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
    val choice: ModePage = ModePage.STANDARD,
    /** Every board read so far, by choice. A board is read the first time it is picked, and kept. */
    val boards: Map<ModePage, LeaderboardPage> = emptyMap(),
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
    savedChoice: StateFlow<ModePage?> = MutableStateFlow(ModePage.STANDARD),
    private val saveChoice: suspend (ModePage) -> Unit = {},
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScoresUiState())
    val uiState: StateFlow<ScoresUiState> = _uiState.asStateFlow()

    /**
     * Each board's page load under way. A new page's load cancels the one before, so a slow earlier page can't land over a
     * later one.
     */
    private val loads = HashMap<ModePage, Job>()

    /** The pick under way: a board not read yet is read before the screen moves to it. */
    private var selecting: Job? = null

    init {
        selecting = viewModelScope.launch { open(savedChoice.filterNotNull().first()) }
    }

    /**
     * Moves to [choice]'s board, so the board on view stays up until the new one is read and then gives way in one frame.
     * The pick is kept apart from that, so the screen doesn't wait on the write.
     */
    fun selectChoice(choice: ModePage) {
        viewModelScope.launch { saveChoice(choice) }
        selecting?.cancel()
        selecting = viewModelScope.launch { open(choice) }
    }

    /**
     * Shows [choice]'s board. One that can't be read leaves the board on view as it is, or, with none on view yet, opens
     * empty, so the screen isn't left waiting, rather than taking the app down.
     */
    private suspend fun open(choice: ModePage) {
        val board = _uiState.value.boards[choice] ?: orNullOnFailure { read(choice, 0) }
        _uiState.update { state ->
            when {
                choice in state.boards -> state.copy(choice = choice, isLoaded = true)
                board != null -> state.copy(choice = choice, boards = state.boards + (choice to board), isLoaded = true)
                state.isLoaded -> state
                else -> state.copy(choice = choice, boards = state.boards + (choice to LeaderboardPage()), isLoaded = true)
            }
        }
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

    private fun loadPage(choice: ModePage, pageIndex: Int) {
        loads[choice]?.cancel()
        loads[choice] = viewModelScope.launch {
            // One that can't be read stays on the page on view.
            val board = orNullOnFailure { read(choice, pageIndex) } ?: return@launch
            _uiState.update { it.copy(boards = it.boards + (choice to board)) }
        }
    }

    private suspend fun read(choice: ModePage, pageIndex: Int): LeaderboardPage = when (choice) {
        is ModePage.Mode -> LeaderboardPage(source.pageForMode(choice.mode, pageIndex), pageIndex, source.totalCountForMode(choice.mode))
        ModePage.Combined -> LeaderboardPage(source.page(pageIndex), pageIndex, source.totalCount())
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
