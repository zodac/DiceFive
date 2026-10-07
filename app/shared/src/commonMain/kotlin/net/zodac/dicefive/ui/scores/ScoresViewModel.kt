package net.zodac.dicefive.ui.scores

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.zodac.dicefive.app.AppContainer
import net.zodac.dicefive.data.scores.SCORES_PAGE_SIZE
import net.zodac.dicefive.data.scores.ScoreEntry
import net.zodac.dicefive.data.scores.ScoreRepository
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.resources.Res
import net.zodac.dicefive.resources.scores_view_combined
import net.zodac.dicefive.resources.scores_view_game_mode
import org.jetbrains.compose.resources.StringResource

/** The two ways to read the Leaderboard, switched at the top of its screen. */
enum class LeaderboardView(val label: StringResource) {
    /** Every game mode's scores in one ranked table. */
    COMBINED(Res.string.scores_view_combined),

    /** One card per game mode, each ranked on its own. */
    GAME_MODE(Res.string.scores_view_game_mode),
}

/** The game modes that can get a card in [LeaderboardView.GAME_MODE] - every one, including those that stay off the Combined table - once it has a score. */
val LEADERBOARD_MODES: List<GameMode> = GameMode.entries.toList()

/** How many pages [totalCount] rows make up - at least one, so an empty board still has a "page 1". */
private fun pageCount(totalCount: Int): Int = ((totalCount - 1) / SCORES_PAGE_SIZE + 1).coerceAtLeast(1)

/** One game mode's card: the page of its scores on view, which page that is and how many rows the mode has. */
data class ModeBoard(
    val entries: List<ScoreEntry> = emptyList(),
    val pageIndex: Int = 0,
    val totalCount: Int = 0,
) {
    val totalPages: Int get() = pageCount(totalCount)
    val hasNextPage: Boolean get() = pageIndex < totalPages - 1
    val hasPreviousPage: Boolean get() = pageIndex > 0
}

data class ScoresUiState(
    val entries: List<ScoreEntry> = emptyList(),
    val pageIndex: Int = 0,
    val totalCount: Int = 0,
    val isLoading: Boolean = false,
    /** Whether the first page has been read yet - until then an empty [entries] means "not known
     * yet", not "no scores", and the screen shows neither the list nor its empty message. */
    val isLoaded: Boolean = false,
    val view: LeaderboardView = LeaderboardView.COMBINED,
    /** Each mode's card, once read - read the first time [LeaderboardView.GAME_MODE] is shown, so a
     * mode missing from the map just isn't loaded yet. */
    val modeBoards: Map<GameMode, ModeBoard> = emptyMap(),
) {
    /** Whether every mode's card has been read - until then the cards shown so far are all there is to show. */
    val modeBoardsLoaded: Boolean get() = modeBoards.keys.containsAll(LEADERBOARD_MODES)

    val totalPages: Int get() = pageCount(totalCount)
    val hasNextPage: Boolean get() = pageIndex < totalPages - 1
    val hasPreviousPage: Boolean get() = pageIndex > 0
}

/**
 * [scoreRepository] is nullable so this stays constructible/testable without a Context - see [factory]. [initialState]
 * starts it somewhere other than empty: [ScoresWarmUp] draws the screen from a made-up one, with no repository to read.
 */
class ScoresViewModel(
    private val scoreRepository: ScoreRepository? = null,
    initialState: ScoresUiState? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(initialState ?: ScoresUiState(isLoaded = scoreRepository == null))
    val uiState: StateFlow<ScoresUiState> = _uiState.asStateFlow()

    init {
        loadPage(0)
    }

    fun nextPage() {
        if (_uiState.value.hasNextPage) loadPage(_uiState.value.pageIndex + 1)
    }

    fun previousPage() {
        if (_uiState.value.hasPreviousPage) loadPage(_uiState.value.pageIndex - 1)
    }

    fun selectView(view: LeaderboardView) {
        _uiState.update { it.copy(view = view) }
        if (view == LeaderboardView.GAME_MODE) {
            for (mode in LEADERBOARD_MODES) if (mode !in _uiState.value.modeBoards) loadModePage(mode, 0)
        }
    }

    fun nextModePage(mode: GameMode) {
        val board = _uiState.value.modeBoards[mode] ?: return
        if (board.hasNextPage) loadModePage(mode, board.pageIndex + 1)
    }

    fun previousModePage(mode: GameMode) {
        val board = _uiState.value.modeBoards[mode] ?: return
        if (board.hasPreviousPage) loadModePage(mode, board.pageIndex - 1)
    }

    private fun loadPage(pageIndex: Int) {
        val repository = scoreRepository ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val entries = repository.page(pageIndex)
            val totalCount = repository.totalCount()
            _uiState.update {
                it.copy(entries = entries, pageIndex = pageIndex, totalCount = totalCount, isLoading = false, isLoaded = true)
            }
        }
    }

    private fun loadModePage(mode: GameMode, pageIndex: Int) {
        val repository = scoreRepository ?: return
        viewModelScope.launch {
            val board = ModeBoard(
                entries = repository.pageForMode(mode, pageIndex),
                pageIndex = pageIndex,
                totalCount = repository.totalCountForMode(mode),
            )
            _uiState.update { it.copy(modeBoards = it.modeBoards + (mode to board)) }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { ScoresViewModel(container.scoreRepository) }
        }
    }
}
