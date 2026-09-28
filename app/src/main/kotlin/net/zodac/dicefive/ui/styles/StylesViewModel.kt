package net.zodac.dicefive.ui.styles

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
import net.zodac.dicefive.data.settings.SettingsRepository
import net.zodac.dicefive.platform.AppContainer
import net.zodac.dicefive.ui.game.style.DiceCupStyles
import net.zodac.dicefive.ui.game.style.DiceMats
import net.zodac.dicefive.ui.game.style.DiceStyles
import net.zodac.dicefive.ui.game.style.TableBackgrounds

/** Nullable so this stays constructible/testable without a Context - see [factory], mirroring `SettingsViewModel`. */
class StylesViewModel(private val settingsRepository: SettingsRepository? = null) : ViewModel() {

    val diceStyleId: StateFlow<String> = (settingsRepository?.diceStyleId ?: flowOf(DiceStyles.default.id))
        .stateIn(viewModelScope, SharingStarted.Eagerly, DiceStyles.default.id)

    val diceCupStyleId: StateFlow<String> = (settingsRepository?.diceCupStyleId ?: flowOf(DiceCupStyles.default.id))
        .stateIn(viewModelScope, SharingStarted.Eagerly, DiceCupStyles.default.id)

    val tableBackgroundId: StateFlow<String> = (settingsRepository?.tableBackgroundId ?: flowOf(TableBackgrounds.default.id))
        .stateIn(viewModelScope, SharingStarted.Eagerly, TableBackgrounds.default.id)

    val diceMatId: StateFlow<String> = (settingsRepository?.diceMatId ?: flowOf(DiceMats.default.id))
        .stateIn(viewModelScope, SharingStarted.Eagerly, DiceMats.default.id)

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
            initializer { StylesViewModel(container.settingsRepository) }
        }
    }
}
