package net.zodac.dicefive.ui.styles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.zodac.dicefive.app.AppContainer
import net.zodac.dicefive.data.achievements.AchievementStore
import net.zodac.dicefive.data.settings.SavedStyles
import net.zodac.dicefive.data.settings.SettingsRepository
import net.zodac.dicefive.data.settings.savedStylesFlow
import net.zodac.dicefive.ui.style.StyleCategory

class StylesViewModel(
    private val settingsRepository: SettingsRepository,
    achievementsRepository: AchievementStore,
    /** The app's own copy of the saved picks ([AppContainer.savedStyles]); without one, they're read from [settingsRepository]. */
    savedStyles: StateFlow<SavedStyles?>? = null,
    /** The app's own copy of the Unlocked only switch ([AppContainer.unlockedOnlyStyles]); without one, it's read from [settingsRepository]. */
    unlockedOnly: StateFlow<Boolean?>? = null,
) : ViewModel() {

    /**
     * The five saved picks and what's been earned so far, which decides which styles are unlocked -
     * see [StyleUnlock][net.zodac.dicefive.ui.style.StyleUnlock]. Null until they've loaded,
     * since until then every pick would look like the default and every style locked. From the app's
     * copy, that's normally already done before this screen is opened.
     */
    val savedStyles: StateFlow<SavedStyles?> = savedStyles
        ?: savedStylesFlow(settingsRepository, achievementsRepository).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Whether only the unlocked styles are shown - null until it has loaded, when the page shows them all. */
    val unlockedOnly: StateFlow<Boolean?> = unlockedOnly
        ?: settingsRepository.unlockedOnlyStyles.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setUnlockedOnly(unlockedOnly: Boolean) {
        viewModelScope.launch { settingsRepository.setUnlockedOnlyStyles(unlockedOnly) }
    }

    /** Saves [id] as [category]'s pick. */
    fun setStyleId(category: StyleCategory, id: String) {
        viewModelScope.launch { category.saveId(settingsRepository, id) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { StylesViewModel(container.settingsRepository, container.achievementsRepository, container.savedStyles, container.unlockedOnlyStyles) }
        }
    }
}
