package net.zodac.dicefive.ui.menu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.zodac.dicefive.app.AppContainer
import net.zodac.dicefive.data.achievements.AchievementEvent
import net.zodac.dicefive.data.achievements.AchievementEvents
import net.zodac.dicefive.data.achievements.AchievementStore
import net.zodac.dicefive.data.settings.SettingsRepository
import net.zodac.dicefive.game.AchievementEngine
import net.zodac.dicefive.game.nowEpochMillis
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.ui.game.style.DiceCupStyle
import net.zodac.dicefive.ui.game.style.DiceCupStyles
import net.zodac.dicefive.ui.game.style.DiceStyle
import net.zodac.dicefive.ui.game.style.DiceStyles

/** The dice and cup the menu's logo is drawn with - the player's own picks from the Styles screen. */
data class LogoStyles(val dice: DiceStyle, val cup: DiceCupStyle)

/**
 * Backs the menu: the one achievement it can earn itself - tapping its own logo dice ("Not Those
 * Dice!") - and the styles its logo is drawn in. Nullable repositories for the same reason every
 * other screen's view model's are: constructible with no Context, e.g. from a plain preview.
 */
class MenuViewModel(
    private val achievementsRepository: AchievementStore? = null,
    settingsRepository: SettingsRepository? = null,
) : ViewModel() {

    /**
     * Null until the saved picks have loaded, so the logo never shows the defaults for a frame
     * before switching to a player's own; with no repository, straight to the defaults.
     */
    val logoStyles: StateFlow<LogoStyles?> = if (settingsRepository == null) {
        MutableStateFlow(LogoStyles(DiceStyles.default, DiceCupStyles.default))
    } else {
        combine(settingsRepository.diceStyleId, settingsRepository.diceCupStyleId) { diceId, cupId ->
            LogoStyles(DiceStyles.byId(diceId), DiceCupStyles.byId(cupId))
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    }

    fun onDiceTapped() {
        val repository = achievementsRepository ?: return
        viewModelScope.launch {
            val before = repository.current()
            val update = AchievementEngine.unlockNow(setOf(Achievement.NOT_THOSE_DICE), before, nowEpochMillis())
            if (update.isEmpty) return@launch
            // Stored before it's announced, so a banner can never outlive its unlock.
            repository.record(update.unlockedAt(), update.counters)
            update.newlyUnlocked.forEach { AchievementEvents.emit(AchievementEvent.Unlocked(it)) }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { MenuViewModel(container.achievementsRepository, container.settingsRepository) }
        }
    }
}
