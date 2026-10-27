package net.zodac.dicefive.game.style

import kotlinx.coroutines.flow.Flow
import net.zodac.dicefive.data.settings.SavedStyles
import net.zodac.dicefive.data.settings.SettingsRepository

/**
 * The five kinds of table art a player picks on the Styles screen, in its order: each one's [registry] and where its
 * pick is saved. For code that does the same to every category - resetting locked picks, checking for a non-default
 * table - rather than one line per category. Its art is `ui.style`'s `catalog`.
 */
enum class StyleCategory(
    val registry: StyleRegistry,
    /** The saved pick, as it changes. */
    val savedId: (SettingsRepository) -> Flow<String>,
    /** Saves a pick. */
    val saveId: suspend (SettingsRepository, String) -> Unit,
    /** The pick in a [SavedStyles]. */
    val pickIn: (SavedStyles) -> String,
) {
    DICE(DiceStyleRegistry, SettingsRepository::diceStyleId, SettingsRepository::setDiceStyleId, SavedStyles::diceStyleId),
    DICE_CUP(DiceCupStyleRegistry, SettingsRepository::diceCupStyleId, SettingsRepository::setDiceCupStyleId, SavedStyles::diceCupStyleId),
    MAT(DiceMatRegistry, SettingsRepository::diceMatId, SettingsRepository::setDiceMatId, SavedStyles::diceMatId),
    BACKGROUND(TableBackgroundRegistry, SettingsRepository::tableBackgroundId, SettingsRepository::setTableBackgroundId, SavedStyles::tableBackgroundId),
    FRAME(ScoreFrameRegistry, SettingsRepository::scoreFrameId, SettingsRepository::setScoreFrameId, SavedStyles::scoreFrameId),
}

/** Whether any of [this] picks draws something other than its category's default - a locked one draws the default. */
val SavedStyles.hasNonDefaultStyle: Boolean
    get() = StyleCategory.entries.any { it.registry.unlockedId(it.pickIn(this), access) != it.registry.defaultId }
