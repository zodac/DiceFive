package net.zodac.dicefive.ui.statistics

import android.content.Context
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
import net.zodac.dicefive.data.scores.AppDatabase
import net.zodac.dicefive.data.scores.PlayerStatistics
import net.zodac.dicefive.data.scores.ScoreRepository

data class StatisticsUiState(
    val players: List<PlayerStatistics> = emptyList(),
    val isLoading: Boolean = false,
)

/** [scoreRepository] is nullable so this stays constructible/testable without a Context - see [factory]. */
class StatisticsViewModel(private val scoreRepository: ScoreRepository? = null) : ViewModel() {

    private val _uiState = MutableStateFlow(StatisticsUiState())
    val uiState: StateFlow<StatisticsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    private fun load() {
        val repository = scoreRepository ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val players = repository.playerStatistics()
            _uiState.update { it.copy(players = players, isLoading = false) }
        }
    }

    /** Hides [playerName]'s card from this screen - their leaderboard history is untouched. */
    fun dismissPlayer(playerName: String) {
        val repository = scoreRepository ?: return
        viewModelScope.launch {
            repository.dismissPlayerStatistics(playerName)
            load()
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val scoreDao = AppDatabase.getInstance(context.applicationContext).scoreDao()
                StatisticsViewModel(ScoreRepository(scoreDao))
            }
        }
    }
}
