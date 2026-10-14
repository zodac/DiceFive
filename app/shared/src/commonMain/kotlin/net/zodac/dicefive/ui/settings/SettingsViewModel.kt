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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.zodac.dicefive.app.AppContainer
import net.zodac.dicefive.data.achievements.AchievementStore
import net.zodac.dicefive.data.achievements.AchievementsState
import net.zodac.dicefive.data.scores.ScoreRepository
import net.zodac.dicefive.data.settings.AnimationLevel
import net.zodac.dicefive.data.settings.SettingsRepository
import net.zodac.dicefive.game.nowEpochMillis
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.online.OnlineSettingsRepository
import net.zodac.dicefive.ui.achievements.AchievementUnlocker
import net.zodac.dicefive.ui.game.style.StyleCategory

/** The Settings screen's switches and its animation level, as saved. */
data class SettingsToggles(
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val animationLevel: AnimationLevel = AnimationLevel.default,
    val confirmBeforeLeavingGame: Boolean = true,
)

/** The Settings card for the online service: whether it is used on this device, and whether progress is saved to the cloud. */
data class OnlineToggles(val useOnDevice: Boolean = true, val cloudSaveEnabled: Boolean = true)

/** All repositories are nullable so this stays constructible/testable without a Context - see [factory]. */
class SettingsViewModel(
    private val settingsRepository: SettingsRepository? = null,
    private val achievementsRepository: AchievementStore? = null,
    private val scoreRepository: ScoreRepository? = null,
    private val onlineSettingsRepository: OnlineSettingsRepository? = null,
) : ViewModel() {

    /** The online card's two switches as saved - the defaults until they load, and with no repository. */
    val onlineToggles: StateFlow<OnlineToggles> = if (onlineSettingsRepository == null) {
        MutableStateFlow(OnlineToggles())
    } else {
        combine(onlineSettingsRepository.useOnDevice, onlineSettingsRepository.cloudSaveEnabled, ::OnlineToggles)
            .stateIn(viewModelScope, SharingStarted.Eagerly, OnlineToggles())
    }

    fun setUseOnDevice(use: Boolean) {
        val repository = onlineSettingsRepository ?: return
        viewModelScope.launch { repository.setUseOnDevice(use) }
    }

    fun setCloudSaveEnabled(enabled: Boolean) {
        val repository = onlineSettingsRepository ?: return
        viewModelScope.launch { repository.setCloudSaveEnabled(enabled) }
    }

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

    private val achievementUnlocker = achievementsRepository?.let(::AchievementUnlocker)

    /** Backs the one achievement this screen itself can earn - opening the About dialog
     * ("Who Made This?"). Same fire-and-check-once pattern as
     * [net.zodac.dicefive.ui.menu.MenuViewModel.onDiceTapped]. */
    fun onAboutViewed() {
        val unlocker = achievementUnlocker ?: return
        viewModelScope.launch { unlocker.unlock(setOf(Achievement.WHO_MADE_THIS)) }
    }

    private var versionTaps = 0
    private var lastVersionTapMillis = 0L

    /**
     * A tap on the version number, at [nowMillis]. The [VERSION_TAP_TARGET]th in a row - each within
     * [VERSION_TAP_GAP_MILLIS] of the last - earns "Are You A Developer?", as the same taps on Android's own build
     * number turn on its developer options. A slower tap starts the count again.
     */
    fun onVersionTapped(nowMillis: Long = nowEpochMillis()) {
        versionTaps = if (versionTaps > 0 && nowMillis - lastVersionTapMillis <= VERSION_TAP_GAP_MILLIS) versionTaps + 1 else 1
        lastVersionTapMillis = nowMillis
        if (versionTaps < VERSION_TAP_TARGET) return
        versionTaps = 0
        val unlocker = achievementUnlocker ?: return
        viewModelScope.launch { unlocker.unlock(setOf(Achievement.ARE_YOU_A_DEVELOPER)) }
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
        for (category in StyleCategory.entries) {
            if (!category.catalog.isUnlocked(category.savedId(settings).first(), none)) category.saveId(settings, category.catalog.default.id)
        }
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
        /** How many taps on the version number earn "Are You A Developer?" - Android's own count for its build number. */
        const val VERSION_TAP_TARGET = 7

        /** The longest a tap on the version number can come after the last and still count towards [VERSION_TAP_TARGET]. */
        const val VERSION_TAP_GAP_MILLIS = 1_500L

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SettingsViewModel(
                    settingsRepository = container.settingsRepository,
                    achievementsRepository = container.achievementsRepository,
                    scoreRepository = container.scoreRepository,
                    onlineSettingsRepository = container.onlineSettingsRepository,
                )
            }
        }
    }
}
