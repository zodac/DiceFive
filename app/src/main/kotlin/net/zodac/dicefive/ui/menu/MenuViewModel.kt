package net.zodac.dicefive.ui.menu

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.launch
import net.zodac.dicefive.data.achievements.AchievementEvent
import net.zodac.dicefive.data.achievements.AchievementEvents
import net.zodac.dicefive.data.achievements.AchievementStore
import net.zodac.dicefive.data.achievements.AchievementsRepository
import net.zodac.dicefive.game.AchievementEngine
import net.zodac.dicefive.model.Achievement

/**
 * Backs the one achievement the menu itself can earn - tapping its own logo dice ("Not Those
 * Dice!") - since [MenuScreen] otherwise has no state or repository of its own. Nullable
 * repository for the same reason every other screen's view model's is: constructible with no
 * Context, e.g. from a plain preview.
 */
class MenuViewModel(private val achievementsRepository: AchievementStore? = null) : ViewModel() {

    fun onDiceTapped() {
        val repository = achievementsRepository ?: return
        viewModelScope.launch {
            val before = repository.current()
            val update = AchievementEngine.unlockNow(setOf(Achievement.NOT_THOSE_DICE), before, System.currentTimeMillis())
            if (update.isEmpty) return@launch
            // Stored before it's announced, so a banner can never outlive its unlock.
            repository.record(update.unlockedAt(), update.counters)
            update.newlyUnlocked.forEach { AchievementEvents.emit(AchievementEvent.Unlocked(it)) }
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer { MenuViewModel(AchievementsRepository(context.applicationContext)) }
        }
    }
}
