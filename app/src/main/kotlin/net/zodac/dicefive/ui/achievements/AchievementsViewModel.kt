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
import net.zodac.dicefive.data.scores.AppDatabase
import net.zodac.dicefive.data.scores.ScoreRepository
import net.zodac.dicefive.game.AchievementEngine
import net.zodac.dicefive.game.LeaderboardTotals
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.model.AchievementCategory
import net.zodac.dicefive.model.AchievementVisibility

/** One row on the achievements list. [unlockedAt] is null while it's still locked. */
data class AchievementItem(
    val achievement: Achievement,
    val unlockedAt: Long? = null,
    val progress: Int = 0,
) {
    val progressFraction: Float get() = progress.toFloat() / achievement.target
}

/** A themed run of achievements, in the order they should be attempted. */
data class AchievementGroup(val category: AchievementCategory, val items: List<AchievementItem>)

data class AchievementsUiState(
    val groups: List<AchievementGroup> = emptyList(),
    val unlockedCount: Int = 0,
    val totalCount: Int = 0,
)

/**
 * Both repositories are nullable so this stays constructible/testable without a Context - see
 * [factory]. [scoreRepository] is here for the score-collection achievements, whose progress is
 * measured against the leaderboard itself rather than a stored counter.
 */
class AchievementsViewModel(
    private val achievementsRepository: AchievementStore? = null,
    private val scoreRepository: ScoreRepository? = null,
) : ViewModel() {

    // Read once: the leaderboard only changes when a game finishes, which can't happen while this
    // screen is open.
    private val leaderboard = MutableStateFlow(LeaderboardTotals())

    val uiState: StateFlow<AchievementsUiState> =
        combine(
            achievementsRepository?.state ?: flowOf(AchievementsState()),
            leaderboard,
            ::toUiState,
        ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(), AchievementsUiState())

    init {
        scoreRepository?.let { repository ->
            viewModelScope.launch { leaderboard.value = repository.leaderboardTotals() }
        }
    }

    fun resetAll() {
        val repository = achievementsRepository ?: return
        viewModelScope.launch { repository.resetAll() }
    }

    private fun toUiState(state: AchievementsState, leaderboard: LeaderboardTotals): AchievementsUiState {
        val items = Achievement.entries
            // A secret achievement doesn't exist as far as the list (or its counts) is concerned
            // until it's actually been earned - that's the whole point of it being secret.
            .filterNot { it.visibility == AchievementVisibility.SECRET && state.unlockedAt[it] == null }
            .map {
                AchievementItem(it, state.unlockedAt[it], AchievementEngine.progressOf(it, state.counters, leaderboard))
            }

        return AchievementsUiState(
            // Grouped by theme and, within a theme, easiest first - which is just the catalogue's
            // own declaration order, so a ladder stays a ladder whether its achievements are
            // earned yet or not. Alphabetical would scatter "Sharpshooter", "High Roller" and
            // "Dice Deity" across the list; splitting earned ones into their own section would
            // scatter the ladders themselves.
            groups = items
                .groupBy { it.achievement.category }
                .map { (category, categoryItems) -> AchievementGroup(category, categoryItems) },
            unlockedCount = items.count { it.unlockedAt != null },
            totalCount = items.size,
        )
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                AchievementsViewModel(
                    achievementsRepository = AchievementsRepository(appContext),
                    scoreRepository = ScoreRepository(AppDatabase.getInstance(appContext).scoreDao()),
                )
            }
        }
    }
}
