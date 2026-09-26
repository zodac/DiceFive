package net.zodac.dicefive.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.zodac.dicefive.data.achievements.AchievementEvent
import net.zodac.dicefive.data.achievements.AchievementEvents
import net.zodac.dicefive.data.achievements.AchievementStore
import net.zodac.dicefive.data.achievements.AchievementsRepository
import net.zodac.dicefive.data.scores.AppDatabase
import net.zodac.dicefive.data.scores.ScoreRepository
import net.zodac.dicefive.data.settings.SettingsRepository
import net.zodac.dicefive.data.settings.Theme
import net.zodac.dicefive.game.AchievementEngine
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.ui.game.GameSetupState

/** All repositories are nullable so this stays constructible/testable without a Context - see [factory]. */
class SettingsViewModel(
    private val settingsRepository: SettingsRepository? = null,
    private val achievementsRepository: AchievementStore? = null,
    private val scoreRepository: ScoreRepository? = null,
) : ViewModel() {

    val theme: StateFlow<Theme> = (settingsRepository?.theme ?: flowOf(Theme.SYSTEM))
        .stateIn(viewModelScope, SharingStarted.Eagerly, Theme.SYSTEM)

    val confirmBeforeLeavingGame: StateFlow<Boolean> = (settingsRepository?.confirmBeforeLeavingGame ?: flowOf(true))
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val soundEnabled: StateFlow<Boolean> = (settingsRepository?.soundEnabled ?: flowOf(true))
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val vibrationEnabled: StateFlow<Boolean> = (settingsRepository?.vibrationEnabled ?: flowOf(true))
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** The user's own name - blank until they set one, same as an unset [net.zodac.dicefive.ui.game.PlayerSetupSlot]'s
     * name falls back to "Player 1" at game start rather than needing a non-empty default here. */
    val userName: StateFlow<String> = (settingsRepository?.userName ?: flowOf(null))
        .map { it ?: "" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    fun setTheme(theme: Theme) {
        val repository = settingsRepository ?: return
        viewModelScope.launch { repository.setTheme(theme) }
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

    /** Capped the same way [net.zodac.dicefive.ui.game.GameViewModel.setPlayerName] caps every other
     * player's name - enforced here, not just in the text field, so a longer name saved before the
     * cap existed is trimmed on the way back in rather than reappearing over-long. */
    fun setUserName(name: String) {
        val repository = settingsRepository ?: return
        viewModelScope.launch { repository.setUserName(name.take(GameSetupState.MAX_PLAYER_NAME_LENGTH)) }
    }

    /** Backs the one achievement this screen itself can earn - tapping through to the project's
     * GitHub page ("Who Made This"). Same fire-and-check-once pattern as
     * [net.zodac.dicefive.ui.menu.MenuViewModel.onDiceTapped]. */
    fun onGithubLinkOpened() {
        val repository = achievementsRepository ?: return
        viewModelScope.launch {
            val before = repository.current()
            val update = AchievementEngine.unlockNow(setOf(Achievement.WHO_MADE_THIS), before, System.currentTimeMillis())
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
        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                SettingsViewModel(
                    settingsRepository = SettingsRepository(appContext),
                    achievementsRepository = AchievementsRepository(appContext),
                    scoreRepository = ScoreRepository(AppDatabase.getInstance(appContext).scoreDao()),
                )
            }
        }
    }
}
