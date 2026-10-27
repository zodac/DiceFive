package net.zodac.dicefive.app

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import net.zodac.dicefive.data.achievements.AchievementStore
import net.zodac.dicefive.data.achievements.AchievementsState
import net.zodac.dicefive.data.orNullOnFailure
import net.zodac.dicefive.data.scores.ScoreRepository
import net.zodac.dicefive.data.settings.SettingsRepository
import net.zodac.dicefive.game.nowEpochMillis
import net.zodac.dicefive.game.style.StyleAccess
import net.zodac.dicefive.game.style.StyleCategory
import net.zodac.dicefive.online.OnlineSettingsRepository
import net.zodac.dicefive.online.OnlineSyncRequests

/**
 * The two progress resets Settings offers, each leaving every store it touches consistent with the others. [ownsAllStyles]
 * ([AppContainer.ownsAllStyles]) is read as an achievements reset runs: the purchase isn't reset.
 */
class ProgressResets(
    private val achievements: AchievementStore,
    private val settings: SettingsRepository,
    private val scores: ScoreRepository,
    private val onlineSettings: OnlineSettingsRepository,
    private val ownsAllStyles: Flow<Boolean>,
    private val now: () -> Long = ::nowEpochMillis,
) {

    /**
     * Wipes every unlock and every progress counter. Only the achievements DataStore file is
     * cleared - the theme, the remembered player names and any game in progress are on their own
     * files and are deliberately untouched. The one exception is the Styles picks: any saved pick
     * that the reset locks again goes back to its category's default, so earning the style a second
     * time doesn't quietly bring the old pick back. Picks that need no achievement are kept, and so is every pick of a
     * player who owns every style ([ownsAllStyles]).
     *
     * Only on this device: nothing marks the reset for the cloud save, so the same account's next sync brings the
     * achievements back, and the picks with them ([SettingsRepository.unstampStylePicks]).
     */
    suspend fun resetAchievements() {
        achievements.resetAll()
        val afterReset = StyleAccess(AchievementsState(), ownsAllStyles.first())
        for (category in StyleCategory.entries) {
            if (!category.registry.isUnlocked(category.savedId(settings).first(), afterReset)) category.saveId(settings, category.registry.defaultId)
        }
        settings.unstampStylePicks()
    }

    /**
     * Wipes every recorded score, clearing both the Leaderboard and Statistics screens - the latter
     * is computed from the same rows, so there's nothing left to show once they're gone.
     * Achievements measured live against the board (the score-collection ones) lose their progress
     * too, though their unlock timestamps live in the separate achievements DataStore and are
     * untouched.
     *
     * On every device of the synced account too: the reset is marked with its time, and the cloud save drops every score
     * from before it ([OnlineSettingsRepository.scoresResetAt]). The online service's own leaderboards keep theirs.
     */
    suspend fun resetLeaderboard() {
        onlineSettings.setScoresResetAt(now())
        // One that can't be written (a full disk) leaves the scores in place, rather than taking the app down.
        orNullOnFailure { scores.resetLeaderboard() }
        OnlineSyncRequests.request()
    }
}
