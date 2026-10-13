package net.zodac.dicefive.ui.game.style

import kotlinx.coroutines.flow.Flow
import net.zodac.dicefive.data.settings.SavedStyles
import net.zodac.dicefive.data.settings.SettingsRepository

/**
 * The five kinds of table art a player picks on the Styles screen, in its order: each one's [catalog] and where its
 * pick is saved. For code that does the same to every category - resetting locked picks, checking for a non-default
 * table - rather than one line per category.
 */
enum class StyleCategory(
    val catalog: StyleCatalog<*>,
    /** The saved pick, as it changes. */
    val savedId: (SettingsRepository) -> Flow<String>,
    /** Saves a pick. */
    val saveId: suspend (SettingsRepository, String) -> Unit,
    /** The pick in a [SavedStyles]. */
    val pickIn: (SavedStyles) -> String,
) {
    DICE(DiceStyles, SettingsRepository::diceStyleId, SettingsRepository::setDiceStyleId, SavedStyles::diceStyleId),
    DICE_CUP(DiceCupStyles, SettingsRepository::diceCupStyleId, SettingsRepository::setDiceCupStyleId, SavedStyles::diceCupStyleId),
    MAT(DiceMats, SettingsRepository::diceMatId, SettingsRepository::setDiceMatId, SavedStyles::diceMatId),
    BACKGROUND(TableBackgrounds, SettingsRepository::tableBackgroundId, SettingsRepository::setTableBackgroundId, SavedStyles::tableBackgroundId),
    FRAME(ScoreFrames, SettingsRepository::scoreFrameId, SettingsRepository::setScoreFrameId, SavedStyles::scoreFrameId),
}
