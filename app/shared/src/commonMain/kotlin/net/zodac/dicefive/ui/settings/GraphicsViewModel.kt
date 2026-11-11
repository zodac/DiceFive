package net.zodac.dicefive.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.zodac.dicefive.app.AppContainer
import net.zodac.dicefive.data.settings.AnimationLevel
import net.zodac.dicefive.data.settings.DiceRollStyle
import net.zodac.dicefive.data.settings.FrameRate
import net.zodac.dicefive.data.settings.IdleTimeout
import net.zodac.dicefive.data.settings.MotionGroup
import net.zodac.dicefive.data.settings.MotionSettings
import net.zodac.dicefive.data.settings.ScreenOrientation
import net.zodac.dicefive.data.settings.SettingsRepository

/** The Graphics page's settings, as saved. [level] is the Animations level [motion] is the preset of, or null where it matches none (no level is picked). */
data class GraphicsSettings(
    val orientation: ScreenOrientation,
    val motion: MotionSettings,
) {
    val level: AnimationLevel? get() = AnimationLevel.matching(motion)
}

/** The Graphics page, opened from Settings: the Orientation, the Animations level, and the Advanced graphics settings. */
class GraphicsViewModel(private val settingsRepository: SettingsRepository) : ViewModel() {

    /** Null until both have loaded, so the page never draws a default before the player's own. */
    val settings: StateFlow<GraphicsSettings?> = combine(settingsRepository.screenOrientation, settingsRepository.motionSettings, ::GraphicsSettings)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setScreenOrientation(orientation: ScreenOrientation) {
        viewModelScope.launch { settingsRepository.setScreenOrientation(orientation) }
    }

    /** Picks [level], putting every group of the Advanced graphics settings back to its preset. */
    fun setAnimationLevel(level: AnimationLevel) {
        viewModelScope.launch { settingsRepository.setAnimationLevel(level) }
    }

    /** Sets [group] to [rate], the other groups as they are. */
    fun setFrameRate(group: MotionGroup, rate: FrameRate) = setMotion { it.with(group, rate) }

    fun setRollStyle(style: DiceRollStyle) = setMotion { it.copy(rollStyle = style) }

    fun setIdleTimeout(timeout: IdleTimeout) = setMotion { it.copy(idleTimeout = timeout) }

    private fun setMotion(change: (MotionSettings) -> MotionSettings) {
        val current = settings.value?.motion ?: return
        viewModelScope.launch { settingsRepository.setMotionSettings(change(current)) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { GraphicsViewModel(container.settingsRepository) }
        }
    }
}
