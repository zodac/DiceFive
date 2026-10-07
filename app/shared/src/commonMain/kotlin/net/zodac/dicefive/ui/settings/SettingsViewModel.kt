package net.zodac.dicefive.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.zodac.dicefive.app.AppContainer
import net.zodac.dicefive.data.achievements.AchievementStore
import net.zodac.dicefive.data.achievements.AchievementsState
import net.zodac.dicefive.data.scores.ScoreRepository
import net.zodac.dicefive.data.settings.AnimationLevel
import net.zodac.dicefive.data.settings.SettingsRepository
import net.zodac.dicefive.game.AchievementEngine
import net.zodac.dicefive.game.nowEpochMillis
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.ui.achievements.announce
import net.zodac.dicefive.ui.game.style.DiceCupStyles
import net.zodac.dicefive.ui.game.style.DiceMats
import net.zodac.dicefive.ui.game.style.DiceStyles
import net.zodac.dicefive.ui.game.style.ScoreFrames
import net.zodac.dicefive.ui.game.style.TableBackgrounds

/** The Settings screen's switches and its animation level, as saved. */
data class SettingsToggles(
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val animationLevel: AnimationLevel = AnimationLevel.default,
    val confirmBeforeLeavingGame: Boolean = true,
)

/** All repositories are nullable so this stays constructible/testable without a Context - see [factory]. */
class SettingsViewModel(
    private val settingsRepository: SettingsRepository? = null,
    private val achievementsRepository: AchievementStore? = null,
    private val scoreRepository: ScoreRepository? = null,
) : ViewModel() {

    /**
     * Null until every setting's saved value has loaded - all four together, so the screen never
     * draws a setting in its default position for a frame before flipping it to the player's own.
     * With no repository, straight to the defaults.
     */
    val toggles: StateFlow<SettingsToggles?> = if (settingsRepository == null) {
        MutableStateFlow(SettingsToggles())
    } else {
        combine(
            settingsRepository.soundEnabled,
            settingsRepository.vibrationEnabled,
            settingsRepository.animationLevel,
            settingsRepository.confirmBeforeLeavingGame,
            ::SettingsToggles,
        ).stateIn(viewModelScope, SharingStarted.Eagerly, null)
    }

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

    fun setAnimationLevel(level: AnimationLevel) {
        val repository = settingsRepository ?: return
        viewModelScope.launch { repository.setAnimationLevel(level) }
    }

    /** Backs the one achievement this screen itself can earn - opening the About dialog
     * ("Who Made This?"). Same fire-and-check-once pattern as
     * [net.zodac.dicefive.ui.menu.MenuViewModel.onDiceTapped]. */
    fun onAboutViewed() {
        val repository = achievementsRepository ?: return
        viewModelScope.launch {
            val before = repository.current()
            val update = AchievementEngine.unlockNow(setOf(Achievement.WHO_MADE_THIS), before, nowEpochMillis())
            if (update.isEmpty) return@launch
            // Stored before it's announced, so a banner can never outlive its unlock.
            repository.record(update.unlockedAt(), update.counters)
            announce(update)
        }
    }

    /**
     * Wipes every unlock and every progress counter. Only the achievements DataStore file is
     * cleared - the theme, the remembered player names and any game in progress are on their own
     * files and are deliberately untouched. The one exception is the Styles picks: any saved pick
     * that the reset locks again goes back to its category's default, so earning the style a second
     * time doesn't quietly bring the old pick back. Picks that need no achievement are kept.
     */
    fun resetAchievements() {
        val repository = achievementsRepository ?: return
        viewModelScope.launch {
            repository.resetAll()
            settingsRepository?.let { resetLockedStylePicks(it) }
        }
    }

    private suspend fun resetLockedStylePicks(settings: SettingsRepository) {
        val none = AchievementsState()
        if (!DiceStyles.isUnlocked(settings.diceStyleId.first(), none)) settings.setDiceStyleId(DiceStyles.default.id)
        if (!DiceCupStyles.isUnlocked(settings.diceCupStyleId.first(), none)) settings.setDiceCupStyleId(DiceCupStyles.default.id)
        if (!TableBackgrounds.isUnlocked(settings.tableBackgroundId.first(), none)) settings.setTableBackgroundId(TableBackgrounds.default.id)
        if (!DiceMats.isUnlocked(settings.diceMatId.first(), none)) settings.setDiceMatId(DiceMats.default.id)
        if (!ScoreFrames.isUnlocked(settings.scoreFrameId.first(), none)) settings.setScoreFrameId(ScoreFrames.default.id)
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
