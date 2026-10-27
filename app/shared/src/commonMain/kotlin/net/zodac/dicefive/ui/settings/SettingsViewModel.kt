package net.zodac.dicefive.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.zodac.dicefive.app.AchievementUnlocker
import net.zodac.dicefive.app.AppContainer
import net.zodac.dicefive.app.ProgressResets
import net.zodac.dicefive.data.achievements.AchievementStore
import net.zodac.dicefive.data.scores.ScoreRepository
import net.zodac.dicefive.data.settings.AnimationLevel
import net.zodac.dicefive.data.settings.ScreenOrientation
import net.zodac.dicefive.data.settings.SettingsRepository
import net.zodac.dicefive.game.nowEpochMillis
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.online.ImportStep
import net.zodac.dicefive.online.LocalCloudSave
import net.zodac.dicefive.online.OnlineSettingsRepository
import net.zodac.dicefive.online.PROGRESS_FILE_EXTENSION
import net.zodac.dicefive.online.ProgressImport
import net.zodac.dicefive.online.ProgressTransfer
import net.zodac.dicefive.platform.PlatformServices
import net.zodac.dicefive.platform.ProgressFileResult
import net.zodac.dicefive.platform.ProgressFiles
import net.zodac.dicefive.ui.common.formatTimestamp

/** The Settings screen's switches, its animation level and its orientation, as saved. */
data class SettingsToggles(
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val animationLevel: AnimationLevel = AnimationLevel.default,
    val confirmBeforeLeavingGame: Boolean = true,
    val screenOrientation: ScreenOrientation = ScreenOrientation.default,
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val achievementsRepository: AchievementStore,
    scoreRepository: ScoreRepository,
    onlineSettingsRepository: OnlineSettingsRepository,
    private val now: () -> Long = ::nowEpochMillis,
    /** This device's progress to and from a file, for a build with progress files ([PlatformServices.progressFiles]). */
    private val progressTransfer: ProgressTransfer? = null,
    /** Whether the purchase unlocks every style ([AppContainer.ownsAllStyles]): an owner's picks survive a reset. */
    ownsAllStyles: Flow<Boolean> = flowOf(false),
    /** Where its achievements are earned: the app's one ([AppContainer.achievementUnlocker]); a test's own otherwise. */
    private val achievementUnlocker: AchievementUnlocker = AchievementUnlocker(achievementsRepository, ownsAllStyles),
    /** The resets it offers: the app's ([AppContainer.progressResets]); a test's own otherwise. */
    private val progressResets: ProgressResets = ProgressResets(achievementsRepository, settingsRepository, scoreRepository, onlineSettingsRepository, ownsAllStyles, now),
) : ViewModel() {

    /**
     * Null until every setting's saved value has loaded - all of them together, so the screen never
     * draws a setting in its default position for a frame before flipping it to the player's own.
     */
    val toggles: StateFlow<SettingsToggles?> = combine(
        settingsRepository.soundEnabled,
        settingsRepository.vibrationEnabled,
        settingsRepository.animationLevel,
        settingsRepository.confirmBeforeLeavingGame,
        settingsRepository.screenOrientation,
        ::SettingsToggles,
    ).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setConfirmBeforeLeavingGame(confirm: Boolean) {
        viewModelScope.launch { settingsRepository.setConfirmBeforeLeavingGame(confirm) }
    }

    fun setSoundEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setSoundEnabled(enabled) }
    }

    fun setVibrationEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setVibrationEnabled(enabled) }
    }

    fun setAnimationLevel(level: AnimationLevel) {
        viewModelScope.launch { settingsRepository.setAnimationLevel(level) }
    }

    fun setScreenOrientation(orientation: ScreenOrientation) {
        viewModelScope.launch { settingsRepository.setScreenOrientation(orientation) }
    }


    /** Backs the one achievement this screen itself can earn - opening the About dialog
     * ("Who Made This?"). Same fire-and-check-once pattern as
     * [net.zodac.dicefive.ui.menu.MenuViewModel.onDiceTapped]. */
    fun onAboutViewed() {
        viewModelScope.launch { achievementUnlocker.unlock(setOf(Achievement.WHO_MADE_THIS)) }
    }

    private var versionTaps = 0

    // A Channel, not a StateFlow: a one-shot toast rather than state to replay.
    private val _versionTapMessages = Channel<Int>(Channel.BUFFERED)

    /**
     * What to tell the player after a repeated tap on the version number, as the number of taps still needed for
     * "Are You A Developer?" - 0 once they have it. Android's own build number counts down the same way.
     */
    val versionTapMessages: Flow<Int> = _versionTapMessages.receiveAsFlow()

    private var lastVersionTapMillis = 0L

    /**
     * A tap on the version number, at [nowMillis]. The [VERSION_TAP_TARGET]th in a row - each within
     * [VERSION_TAP_GAP_MILLIS] of the last - earns "Are You A Developer?", as the same taps on Android's own build
     * number turn on its developer options. A slower tap starts the count again.
     *
     * From the [VERSION_TAP_MESSAGE_FROM]th tap in a row, each also emits on [versionTapMessages], except the one that
     * earns it (its banner is the message).
     */
    fun onVersionTapped(nowMillis: Long = nowEpochMillis()) {
        versionTaps = if (versionTaps > 0 && nowMillis - lastVersionTapMillis <= VERSION_TAP_GAP_MILLIS) versionTaps + 1 else 1
        lastVersionTapMillis = nowMillis
        if (versionTaps < VERSION_TAP_MESSAGE_FROM) return
        val stepsAway = VERSION_TAP_TARGET - versionTaps
        if (stepsAway <= 0) versionTaps = 0
        viewModelScope.launch {
            when {
                achievementsRepository.current().isUnlocked(Achievement.ARE_YOU_A_DEVELOPER) -> _versionTapMessages.trySend(0)
                stepsAway <= 0 -> achievementUnlocker.unlock(setOf(Achievement.ARE_YOU_A_DEVELOPER))
                else -> _versionTapMessages.trySend(stepsAway)
            }
        }
    }

    /** Wipes every unlock and progress counter, and the Styles picks that locks again - see [ProgressResets.resetAchievements]. */
    fun resetAchievements() {
        viewModelScope.launch { progressResets.resetAchievements() }
    }

    /** Wipes every recorded score, here and in the cloud save - see [ProgressResets.resetLeaderboard]. */
    fun resetLeaderboard() {
        viewModelScope.launch { progressResets.resetLeaderboard() }
    }

    private val _progressFilesStatus = MutableStateFlow<ProgressFilesStatus?>(null)

    /**
     * What the progress files card shows under its rows: an import's step while it runs, then how the last export or
     * import ended, until the next starts. Null before any, and after a picker closed unchosen.
     */
    val progressFilesStatus: StateFlow<ProgressFilesStatus?> = _progressFilesStatus.asStateFlow()

    /** The export or import under way: one at a time, so a second tap while a picker opens starts nothing. */
    private var transfer: Job? = null

    private fun startTransfer(block: suspend (ProgressTransfer) -> ProgressOutcome?) {
        val progressTransfer = progressTransfer ?: return
        if (transfer?.isActive == true) return
        _progressFilesStatus.value = null
        transfer = viewModelScope.launch {
            _progressFilesStatus.value = block(progressTransfer)?.let(ProgressFilesStatus::Finished)
        }
    }

    /**
     * Saves everything a cloud save would hold to a file the player picks through [files], named for today
     * ([progressFileName]). Survives the screen being turned while the picker is open: the view model waits on it.
     */
    fun exportProgress(files: ProgressFiles) = startTransfer { progressTransfer ->
        when (files.save(progressFileName(now()), progressTransfer.export(files.key))) {
            is ProgressFileResult.Done -> ProgressOutcome.EXPORTED
            ProgressFileResult.Cancelled -> null
            ProgressFileResult.Failed -> ProgressOutcome.EXPORT_FAILED
        }
    }

    /**
     * Merges in a progress file the player picks through [files], as a cloud save is merged ([ProgressTransfer.import]),
     * telling each step as it starts ([ProgressFilesStatus.Importing]): the card paces them ([nextShownStatus]).
     */
    fun importProgress(files: ProgressFiles) = startTransfer { progressTransfer ->
        when (val opened = files.open()) {
            is ProgressFileResult.Done -> {
                val imported = progressTransfer.import(opened.bytes, files.key) { _progressFilesStatus.value = ProgressFilesStatus.Importing(it) }
                when (imported) {
                    ProgressImport.IMPORTED -> ProgressOutcome.IMPORTED
                    ProgressImport.NOT_A_PROGRESS_FILE -> ProgressOutcome.NOT_A_PROGRESS_FILE
                    ProgressImport.FROM_NEWER_APP -> ProgressOutcome.FROM_NEWER_APP
                }
            }
            ProgressFileResult.Cancelled -> null
            ProgressFileResult.Failed -> ProgressOutcome.IMPORT_FAILED
        }
    }

    companion object {
        /** The tap on the version number from which each one gets a toast. */
        const val VERSION_TAP_MESSAGE_FROM = 2

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
                    ownsAllStyles = container.ownsAllStyles,
                    achievementUnlocker = container.achievementUnlocker,
                    progressResets = container.progressResets,
                    progressTransfer = ProgressTransfer(
                        LocalCloudSave(
                            container.achievementsRepository,
                            container.scoreRepository,
                            container.settingsRepository,
                            container.inProgressGameRepository,
                            container.onlineSettingsRepository,
                        ),
                        container.achievementsRepository,
                        container.scoreRepository::leaderboardTotals,
                    ),
                )
            }
        }

        /** An exported file's suggested name, by the day it was made: "DiceFive-2026-10-21.dicefive". */
        internal fun progressFileName(epochMillis: Long): String =
            "DiceFive-${formatTimestamp(epochMillis, "yyyy-MM-dd", "en")}.$PROGRESS_FILE_EXTENSION" // i18n: not translated - a file name, the brand and a date
    }
}

/** What the progress files card shows ([SettingsViewModel.progressFilesStatus]). */
sealed interface ProgressFilesStatus {

    /** An import on its [step]. */
    data class Importing(val step: ImportStep) : ProgressFilesStatus

    /** How the last export or import ended. */
    data class Finished(val outcome: ProgressOutcome) : ProgressFilesStatus
}

/** How an export or import ended, as the progress files card says it. */
enum class ProgressOutcome {
    EXPORTED,
    IMPORTED,
    NOT_A_PROGRESS_FILE,
    FROM_NEWER_APP,
    EXPORT_FAILED,
    IMPORT_FAILED;

    /** Whether it went wrong: said in red. */
    val failed: Boolean get() = this != EXPORTED && this != IMPORTED
}
