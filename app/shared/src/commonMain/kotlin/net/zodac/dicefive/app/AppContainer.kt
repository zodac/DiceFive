package net.zodac.dicefive.app

import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import net.zodac.dicefive.data.achievements.AchievementsRepository
import net.zodac.dicefive.data.game.InProgressGameRepository
import net.zodac.dicefive.data.scores.ScoreRepository
import net.zodac.dicefive.data.settings.SavedStyles
import net.zodac.dicefive.data.settings.SettingsRepository
import net.zodac.dicefive.data.settings.savedStylesFlow
import net.zodac.dicefive.game.StandardPerfectPlayTable
import net.zodac.dicefive.resources.Res

/**
 * The app's long-lived objects, built once per process by the platform's entry point and handed to
 * the UI through [LocalAppContainer]: the ViewModel factories take their repositories from here,
 * rather than each one reaching for an Android `Context` to build its own.
 */
class AppContainer(
    val scoreRepository: ScoreRepository,
    val settingsRepository: SettingsRepository,
    val achievementsRepository: AchievementsRepository,
    val inProgressGameRepository: InProgressGameRepository,
    val buildInfo: BuildInfo,
    /** Where [savedStyles] is kept loaded - the process, not any one screen. */
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    /**
     * The saved Styles picks, loaded as soon as the container is built and kept up to date for the
     * life of the process - null only until the first load. The menu's logo needs them at launch
     * anyway, so the Styles screen opens with them already in hand instead of waiting on its own load.
     */
    val savedStyles: StateFlow<SavedStyles?> =
        savedStylesFlow(settingsRepository, achievementsRepository).stateIn(scope, SharingStarted.Eagerly, null)

    private val standardPerfectPlay = scope.async(start = CoroutineStart.LAZY) { loadStandardPerfectPlayTable() }

    /**
     * Standard's perfect-play table, which a Hard CPU plays Standard by (see [StandardPerfectPlayTable]):
     * read from the app's resources the first time it's asked for and kept - a megabyte, not worth
     * reading for a player who never meets a Hard CPU in Standard. Null if it can't be read, in which
     * case Hard estimates, as it does in every other mode.
     */
    suspend fun standardPerfectPlayTable(): StandardPerfectPlayTable? = standardPerfectPlay.await()

    private suspend fun loadStandardPerfectPlayTable(): StandardPerfectPlayTable? =
        try {
            StandardPerfectPlayTable.decode(Res.readBytes(StandardPerfectPlayTable.RESOURCE_PATH))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // A missing or damaged table costs Hard its strength in Standard, not the game: it falls back
            // to estimating. StandardPerfectPlayTableTest keeps the bundled one whole and current.
            null
        }
}

val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("No AppContainer provided - the root composable provides the one the platform built")
}
