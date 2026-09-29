package net.zodac.dicefive.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.zodac.dicefive.app.AppContainer
import net.zodac.dicefive.data.achievements.AchievementEvent
import net.zodac.dicefive.data.achievements.AchievementEvents
import net.zodac.dicefive.data.achievements.AchievementStore
import net.zodac.dicefive.data.scores.ScoreRepository
import net.zodac.dicefive.data.settings.SettingsRepository
import net.zodac.dicefive.game.AchievementEngine
import net.zodac.dicefive.game.nowEpochMillis
import net.zodac.dicefive.model.Achievement

/** All repositories are nullable so this stays constructible/testable without a Context - see [factory]. */
class SettingsViewModel(
    private val settingsRepository: SettingsRepository? = null,
    private val achievementsRepository: AchievementStore? = null,
    private val scoreRepository: ScoreRepository? = null,
) : ViewModel() {

    val confirmBeforeLeavingGame: StateFlow<Boolean> = (settingsRepository?.confirmBeforeLeavingGame ?: flowOf(true))
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val soundEnabled: StateFlow<Boolean> = (settingsRepository?.soundEnabled ?: flowOf(true))
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val vibrationEnabled: StateFlow<Boolean> = (settingsRepository?.vibrationEnabled ?: flowOf(true))
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val simpleDiceRoll: StateFlow<Boolean> = (settingsRepository?.simpleDiceRoll ?: flowOf(false))
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setConfirmBeforeLeavingGame(confirm: Boolean) {
        val repository = settingsRepository ?: return
        viewModelScope.launch { repository.setConfirmBeforeLeavingGame(confirm) }
    }

    fun setSoundEnabled(enabled: Boolean) {
        val repository = settingsRepository ?: return
        viewModelScope.launch { repository.setSoundEnabled(enabled) }
    }

    fun setVibrationEnabled(enabled: Boolean) {
        val repository = settingsRepository ?: return
        viewModelScope.launch { repository.setVibrationEnabled(enabled) }
    }

    fun setSimpleDiceRoll(enabled: Boolean) {
        val repository = settingsRepository ?: return
        viewModelScope.launch { repository.setSimpleDiceRoll(enabled) }
    }

    /** Backs the one achievement this screen itself can earn - opening the Credits dialog
     * ("Who Made This?"). Same fire-and-check-once pattern as
     * [net.zodac.dicefive.ui.menu.MenuViewModel.onDiceTapped]. */
    fun onCreditsViewed() {
        val repository = achievementsRepository ?: return
        viewModelScope.launch {
            val before = repository.current()
            val update = AchievementEngine.unlockNow(setOf(Achievement.WHO_MADE_THIS), before, nowEpochMillis())
            if (update.isEmpty) return@launch
            // Stored before it's announced, so a banner can never outlive its unlock.
            repository.record(update.unlockedAt(), update.counters)
            update.newlyUnlocked.forEach { AchievementEvents.emit(AchievementEvent.Unlocked(it)) }
        }
    }

    /**
     * Wipes every unlock and every progress counter. Only the achievements DataStore file is
     * cleared - the theme, the remembered player names and any game in progress are on their own
     * files and are deliberately untouched.
     */
    fun resetAchievements() {
        val repository = achievementsRepository ?: return
        viewModelScope.launch { repository.resetAll() }
    }

    /**
     * Wipes every recorded score, clearing both the Leaderboard and Statistics screens - the latter
     * is computed from the same rows, so there's nothing left to show once they're gone.
     * Achievements measured live against the board (the score-collection ones) lose their progress
     * too, though their unlock timestamps live in the separate achievements DataStore and are
     * untouched.
     */
    fun resetLeaderboard() {
        val repository = scoreRepository ?: return
        viewModelScope.launch { repository.resetLeaderboard() }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SettingsViewModel(
                    settingsRepository = container.settingsRepository,
                    achievementsRepository = container.achievementsRepository,
                    scoreRepository = container.scoreRepository,
                )
            }
        }
    }
}
