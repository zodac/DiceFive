package net.zodac.dicefive.ui.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.zodac.dicefive.app.AppContainer
import net.zodac.dicefive.data.orNullOnFailure
import net.zodac.dicefive.data.scores.PlayerStatistics
import net.zodac.dicefive.data.scores.ScoreRepository
import net.zodac.dicefive.data.settings.SettingsRepository
import net.zodac.dicefive.model.ModePage

data class StatisticsUiState(
    /** Every player's stats, every mode's ([PlayerStatistics]); a page shows its own of them ([ModePage.cards]). */
    val players: List<PlayerStatistics> = emptyList(),
    /** Whether [players] has been read yet - until then an empty list means "not known yet", not
     * "no stats", and the screen shows neither the cards nor its empty message. */
    val isLoaded: Boolean = false,
)

class StatisticsViewModel(
    private val scoreRepository: ScoreRepository,
    private val settingsRepository: SettingsRepository,
    /** The app's own copy of each page's sort ([AppContainer.statisticsSorts]); without one, it's read from [settingsRepository]. */
    sortOrders: StateFlow<Map<ModePage, StatisticsSortOrder>?>? = null,
    /** The app's own copy of the page ([AppContainer.statisticsChoice]); without one, it's read from [settingsRepository]. */
    page: StateFlow<ModePage?>? = null,
) : ViewModel() {

    /** How each page's cards are ordered - null until loaded, when the screen shows nothing. */
    val sortOrders: StateFlow<Map<ModePage, StatisticsSortOrder>?> = sortOrders
        ?: settingsRepository.statisticsSorts
            .map(::parseStatisticsSorts)
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The page on view - null until loaded. */
    val page: StateFlow<ModePage?> = page
        ?: settingsRepository.statisticsChoice.map { ModePage.parse(it) }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Sets [page]'s own sort, kept for that page alone. */
    fun setSortOrder(page: ModePage, order: StatisticsSortOrder) {
        viewModelScope.launch { settingsRepository.setStatisticsSort(page.stored, order.stored) }
    }

    /** Moves to [page], and keeps it for the next visit. */
    fun selectPage(page: ModePage) {
        viewModelScope.launch { settingsRepository.setStatisticsChoice(page.stored) }
    }

    private val _uiState = MutableStateFlow(StatisticsUiState())
    val uiState: StateFlow<StatisticsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            // One that can't be read keeps the cards on view, or leaves the screen's empty message on a first read, rather
            // than taking the app down.
            val players = orNullOnFailure { scoreRepository.playerStatistics() }
            _uiState.update { it.copy(players = players ?: it.players, isLoaded = true) }
        }
    }

    /** Hides [playerName]'s card from this screen - their leaderboard history is untouched. */
    fun dismissPlayer(playerName: String) {
        viewModelScope.launch {
            // One that can't be written (a full disk) leaves the card in place, rather than taking the app down.
            orNullOnFailure { scoreRepository.dismissPlayerStatistics(playerName) }
            load()
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                StatisticsViewModel(container.scoreRepository, container.settingsRepository, container.statisticsSorts, container.statisticsChoice)
            }
        }
    }
}
