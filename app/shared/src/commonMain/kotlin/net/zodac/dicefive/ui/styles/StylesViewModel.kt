package net.zodac.dicefive.ui.styles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.zodac.dicefive.app.AppContainer
import net.zodac.dicefive.data.achievements.AchievementStore
import net.zodac.dicefive.data.settings.SavedStyles
import net.zodac.dicefive.data.settings.SettingsRepository
import net.zodac.dicefive.data.settings.savedStylesFlow
import net.zodac.dicefive.online.OnlineSettingsRepository
import net.zodac.dicefive.ui.style.StyleCategory

class StylesViewModel(
    private val settingsRepository: SettingsRepository,
    achievementsRepository: AchievementStore,
    /** The app's own copy of the saved picks ([AppContainer.savedStyles]); without one, they're read from [settingsRepository]. */
    savedStyles: StateFlow<SavedStyles?>? = null,
    /** The app's own copy of the Unlocked only switch ([AppContainer.unlockedOnlyStyles]); without one, it's read from [settingsRepository]. */
    unlockedOnly: StateFlow<Boolean?>? = null,
    /** Whether the purchase unlocks every style ([AppContainer.ownsAllStyles]), for picks read from [settingsRepository]. */
    ownsAllStyles: Flow<Boolean> = flowOf(false),
    /** Whether this is a debug build - pretending to own every style never happens without it. */
    private val isDebugBuild: Boolean = false,
    /** The app's "pretend all styles are owned" switch ([AppContainer.pretendOwnsAllStyles]). */
    private val pretendOwnsAllStyles: MutableStateFlow<Boolean> = MutableStateFlow(false),
    /** Told when pretending starts, so nothing earned with pretended styles is ever mirrored online. */
    private val onlineSettingsRepository: OnlineSettingsRepository? = null,
) : ViewModel() {

    /**
     * The five saved picks and what's been earned so far, which decides which styles are unlocked -
     * see [StyleUnlock][net.zodac.dicefive.ui.style.StyleUnlock]. Null until they've loaded,
     * since until then every pick would look like the default and every style locked. From the app's
     * copy, that's normally already done before this screen is opened.
     */
    val savedStyles: StateFlow<SavedStyles?> = savedStyles
        ?: savedStylesFlow(settingsRepository, achievementsRepository, ownsAllStyles).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Whether only the unlocked styles are shown - null until it has loaded, when the page shows them all. */
    val unlockedOnly: StateFlow<Boolean?> = unlockedOnly
        ?: settingsRepository.unlockedOnlyStyles.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setUnlockedOnly(unlockedOnly: Boolean) {
        viewModelScope.launch { settingsRepository.setUnlockedOnlyStyles(unlockedOnly) }
    }

    private val _toastMessages = Channel<String>(Channel.BUFFERED)
    val toastMessages: Flow<String> = _toastMessages.receiveAsFlow()

    private var unlockedCountTapCount = 0

    /**
     * Debug builds only: every [PRETEND_TAP_TARGET]th tap on the unlocked count turns "pretend all styles are owned" on
     * or off, for the whole app until it closes. Turning it on blocks online mirroring, as superuser mode does.
     */
    fun onUnlockedCountTapped() {
        if (!isDebugBuild) return
        unlockedCountTapCount++
        if (unlockedCountTapCount < PRETEND_TAP_TARGET) return
        unlockedCountTapCount = 0
        val pretend = !pretendOwnsAllStyles.value
        pretendOwnsAllStyles.value = pretend
        if (pretend) onlineSettingsRepository?.let { viewModelScope.launch { it.blockMirroring() } }
        _toastMessages.trySend(if (pretend) "Pretending all styles are owned" else "No longer pretending all styles are owned") // i18n: not translated - a debug-build developer toast
    }

    /** Saves [id] as [category]'s pick. */
    fun setStyleId(category: StyleCategory, id: String) {
        viewModelScope.launch { category.saveId(settingsRepository, id) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                StylesViewModel(
                    container.settingsRepository,
                    container.achievementsRepository,
                    container.savedStyles,
                    container.unlockedOnlyStyles,
                    container.ownsAllStyles,
                    container.buildInfo.isDebug,
                    container.pretendOwnsAllStyles,
                    container.onlineSettingsRepository,
                )
            }
        }

        /** How many taps on the unlocked count turn pretending to own every style on or off. */
        const val PRETEND_TAP_TARGET = 5
    }
}
