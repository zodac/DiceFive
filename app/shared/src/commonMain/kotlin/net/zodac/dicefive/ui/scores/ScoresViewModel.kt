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

data class ScoresUiState(
    val entries: List<ScoreEntry> = emptyList(),
    val pageIndex: Int = 0,
    val totalCount: Int = 0,
    val isLoading: Boolean = false,
) {
    val totalPages: Int get() = ((totalCount - 1) / SCORES_PAGE_SIZE + 1).coerceAtLeast(1)
    val hasNextPage: Boolean get() = pageIndex < totalPages - 1
    val hasPreviousPage: Boolean get() = pageIndex > 0
}

/** [scoreRepository] is nullable so this stays constructible/testable without a Context - see [factory]. */
class ScoresViewModel(private val scoreRepository: ScoreRepository? = null) : ViewModel() {

    private val _uiState = MutableStateFlow(ScoresUiState())
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

    private fun loadPage(pageIndex: Int) {
        val repository = scoreRepository ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val entries = repository.page(pageIndex)
            val totalCount = repository.totalCount()
            _uiState.update {
                it.copy(entries = entries, pageIndex = pageIndex, totalCount = totalCount, isLoading = false)
            }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { ScoresViewModel(container.scoreRepository) }
        }
    }
}
