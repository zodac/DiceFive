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
import net.zodac.dicefive.data.achievements.AchievementStore
import net.zodac.dicefive.data.achievements.AchievementsRepository
import net.zodac.dicefive.data.settings.SettingsRepository
import net.zodac.dicefive.data.settings.Theme

/** Both repositories are nullable so this stays constructible/testable without a Context - see [factory]. */
class SettingsViewModel(
    private val settingsRepository: SettingsRepository? = null,
    private val achievementsRepository: AchievementStore? = null,
) : ViewModel() {

    val theme: StateFlow<Theme> = (settingsRepository?.theme ?: flowOf(Theme.SYSTEM))
        .stateIn(viewModelScope, SharingStarted.Eagerly, Theme.SYSTEM)

    val confirmBeforeLeavingGame: StateFlow<Boolean> = (settingsRepository?.confirmBeforeLeavingGame ?: flowOf(true))
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setTheme(theme: Theme) {
        val repository = settingsRepository ?: return
        viewModelScope.launch { repository.setTheme(theme) }
    }

    fun setConfirmBeforeLeavingGame(confirm: Boolean) {
        val repository = settingsRepository ?: return
        viewModelScope.launch { repository.setConfirmBeforeLeavingGame(confirm) }
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

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                SettingsViewModel(
                    settingsRepository = SettingsRepository(appContext),
                    achievementsRepository = AchievementsRepository(appContext),
                )
            }
        }
    }
}
