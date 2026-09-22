package net.zodac.dicefive.ui.achievements

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.zodac.dicefive.data.achievements.AchievementStore
import net.zodac.dicefive.data.achievements.AchievementsRepository
import net.zodac.dicefive.data.achievements.AchievementsState
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.model.AchievementCategory

/** One row on the achievements list. [unlockedAt] is null while it's still locked. */
data class AchievementItem(
    val achievement: Achievement,
    val unlockedAt: Long? = null,
    val progress: Int = 0,
) {
    val progressFraction: Float get() = progress.toFloat() / achievement.target
}

/** A themed run of still-locked achievements, in the order they should be attempted. */
data class AchievementGroup(val category: AchievementCategory, val items: List<AchievementItem>)

data class AchievementsUiState(
    val lockedGroups: List<AchievementGroup> = emptyList(),
    val unlocked: List<AchievementItem> = emptyList(),
    val hideUnlocked: Boolean = false,
) {
    val lockedCount: Int get() = lockedGroups.sumOf { it.items.size }
    val totalCount: Int get() = lockedCount + unlocked.size
}

/** [achievementsRepository] is nullable so this stays constructible/testable without a Context - see [factory]. */
class AchievementsViewModel(private val achievementsRepository: AchievementStore? = null) : ViewModel() {

    private val hideUnlocked = MutableStateFlow(false)

    val uiState: StateFlow<AchievementsUiState> =
        combine(achievementsRepository?.state ?: flowOf(AchievementsState()), hideUnlocked, ::toUiState)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), AchievementsUiState())

    fun setHideUnlocked(hide: Boolean) {
        hideUnlocked.value = hide
    }

    fun resetAll() {
        val repository = achievementsRepository ?: return
        viewModelScope.launch { repository.resetAll() }
    }

    private fun toUiState(state: AchievementsState, hideUnlocked: Boolean): AchievementsUiState {
        val (unlocked, locked) = Achievement.entries
            .map { AchievementItem(it, state.unlockedAt[it], state.progress(it)) }
            .partition { it.unlockedAt != null }

        return AchievementsUiState(
            // Still to do: grouped by theme and, within a theme, easiest first - which is just
            // the catalogue's own declaration order, so the ladders stay intact. Alphabetical
            // would scatter "Sharpshooter", "High Roller" and "Dice Deity" across the list.
            lockedGroups = locked
                .groupBy { it.achievement.category }
                .map { (category, items) -> AchievementGroup(category, items) },
            // Already done: most recent first, so what just happened is at the top. No themes
            // here - this half is a history, not a to-do list.
            unlocked = unlocked.sortedByDescending { it.unlockedAt },
            hideUnlocked = hideUnlocked,
        )
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer { AchievementsViewModel(AchievementsRepository(context.applicationContext)) }
        }
    }
}
