package net.zodac.dicefive.ui.styles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.zodac.dicefive.app.AppContainer
import net.zodac.dicefive.data.achievements.AchievementStore
import net.zodac.dicefive.data.achievements.AchievementsState
import net.zodac.dicefive.data.settings.SavedStyles
import net.zodac.dicefive.data.settings.SettingsRepository
import net.zodac.dicefive.data.settings.savedStylesFlow
import net.zodac.dicefive.ui.game.style.DiceCupStyles
import net.zodac.dicefive.ui.game.style.DiceMats
import net.zodac.dicefive.ui.game.style.DiceStyles
import net.zodac.dicefive.ui.game.style.TableBackgrounds

/** Nullable so this stays constructible/testable without a Context - see [factory], mirroring `SettingsViewModel`. */
class StylesViewModel(
    private val settingsRepository: SettingsRepository? = null,
    achievementsRepository: AchievementStore? = null,
    /** The app's own copy of the saved picks ([AppContainer.savedStyles]); without one, they're read from [settingsRepository]. */
    savedStyles: StateFlow<SavedStyles?>? = null,
) : ViewModel() {

    /**
     * The four saved picks and what's been earned so far, which decides which styles are unlocked -
     * see [StyleUnlock][net.zodac.dicefive.ui.game.style.StyleUnlock]. Null until they've loaded,
     * since until then every pick would look like the default and every style locked. From the app's
     * copy, that's normally already done before this screen is opened.
     */
    val savedStyles: StateFlow<SavedStyles?> = when {
        savedStyles != null -> savedStyles
        settingsRepository != null -> savedStylesFlow(settingsRepository, achievementsRepository)
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)
        else -> MutableStateFlow(
            SavedStyles(DiceStyles.default.id, DiceCupStyles.default.id, TableBackgrounds.default.id, DiceMats.default.id, AchievementsState()),
        )
    }

    fun setDiceStyleId(id: String) {
        val repository = settingsRepository ?: return
        viewModelScope.launch { repository.setDiceStyleId(id) }
    }

    fun setDiceCupStyleId(id: String) {
        val repository = settingsRepository ?: return
        viewModelScope.launch { repository.setDiceCupStyleId(id) }
    }

    fun setTableBackgroundId(id: String) {
        val repository = settingsRepository ?: return
        viewModelScope.launch { repository.setTableBackgroundId(id) }
    }

    fun setDiceMatId(id: String) {
        val repository = settingsRepository ?: return
        viewModelScope.launch { repository.setDiceMatId(id) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { StylesViewModel(container.settingsRepository, container.achievementsRepository, container.savedStyles) }
        }
    }
}
