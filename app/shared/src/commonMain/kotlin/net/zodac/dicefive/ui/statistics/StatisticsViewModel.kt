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

data class StatisticsUiState(
    val players: List<PlayerStatistics> = emptyList(),
    /** Whether [players] has been read yet - until then an empty list means "not known yet", not
     * "no stats", and the screen shows neither the cards nor its empty message. */
    val isLoaded: Boolean = false,
)

class StatisticsViewModel(
    private val scoreRepository: ScoreRepository,
    private val settingsRepository: SettingsRepository,
    /** The app's own copy of the sort ([AppContainer.statisticsSort]); without one, it's read from [settingsRepository]. */
    sortOrder: StateFlow<StatisticsSortOrder?>? = null,
) : ViewModel() {

    /** How the cards are ordered - null until it has loaded, when the screen shows nothing. */
    val sortOrder: StateFlow<StatisticsSortOrder?> = sortOrder
        ?: settingsRepository.statisticsSort.map { StatisticsSortOrder.parse(it) }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setSortOrder(order: StatisticsSortOrder) {
        viewModelScope.launch { settingsRepository.setStatisticsSort(order.stored) }
    }

    private val _uiState = MutableStateFlow(StatisticsUiState())
    val uiState: StateFlow<StatisticsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val players = scoreRepository.playerStatistics()
            _uiState.update { it.copy(players = players, isLoaded = true) }
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
            initializer { StatisticsViewModel(container.scoreRepository, container.settingsRepository, container.statisticsSort) }
        }
    }
}
