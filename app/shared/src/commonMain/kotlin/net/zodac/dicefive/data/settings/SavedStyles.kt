package net.zodac.dicefive.data.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import net.zodac.dicefive.data.achievements.AchievementStore
import net.zodac.dicefive.data.achievements.AchievementsState

/**
 * The five saved Styles picks, as ids, with the achievements that decide which of them are unlocked -
 * everything the menu's logo and the Styles screen need to draw a player's picks. Ids rather than
 * the styles themselves, as in [SettingsRepository]: resolving them is the UI's job.
 */
data class SavedStyles(
    val diceStyleId: String,
    val diceCupStyleId: String,
    val tableBackgroundId: String,
    val diceMatId: String,
    val achievements: AchievementsState,
    // Last, with ScoreFrames.default's id, as it came after the other four.
    val scoreFrameId: String = "classic",
    /** The size the Styles page's cards fit its screen at, as the Styles screen saved it - null until it's measured. */
    val stylesPageFit: String? = null,
)

/**
 * [SavedStyles] as it changes. The app holds one of these open for its whole life
 * ([net.zodac.dicefive.app.AppContainer.savedStyles]), so a screen opened later has the picks the
 * moment it's composed rather than a frame or two after.
 */
fun savedStylesFlow(settings: SettingsRepository, achievements: AchievementStore?): Flow<SavedStyles> =
    combine(
        settings.diceStyleId,
        settings.diceCupStyleId,
        settings.tableBackgroundId,
        settings.diceMatId,
        achievements?.state ?: flowOf(AchievementsState()),
    ) { diceId, cupId, backgroundId, matId, achievementsState ->
        SavedStyles(diceId, cupId, backgroundId, matId, achievementsState)
    }.combine(settings.scoreFrameId) { styles, frameId -> styles.copy(scoreFrameId = frameId) }
        .combine(settings.stylesPageFit) { styles, fit -> styles.copy(stylesPageFit = fit) }
