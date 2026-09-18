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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.zodac.dicefive.data.settings.SettingsRepository
import net.zodac.dicefive.data.settings.Theme

/** [settingsRepository] is nullable so this stays constructible/testable without a Context - see [factory]. */
class SettingsViewModel(private val settingsRepository: SettingsRepository? = null) : ViewModel() {

    val theme: StateFlow<Theme> = (settingsRepository?.theme ?: flowOf(Theme.SYSTEM))
        .stateIn(viewModelScope, SharingStarted.Eagerly, Theme.SYSTEM)

    fun setTheme(theme: Theme) {
        val repository = settingsRepository ?: return
        viewModelScope.launch { repository.setTheme(theme) }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(SettingsRepository(context.applicationContext)) }
        }
    }
}
