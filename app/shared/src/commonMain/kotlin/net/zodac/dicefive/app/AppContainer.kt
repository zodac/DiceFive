package net.zodac.dicefive.app

import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.room.RoomDatabase
import net.zodac.dicefive.data.PreferencesFile
import net.zodac.dicefive.data.achievements.AchievementsRepository
import net.zodac.dicefive.data.createPreferencesDataStore
import net.zodac.dicefive.data.game.InProgressGameRepository
import net.zodac.dicefive.data.orNullOnFailure
import net.zodac.dicefive.data.scores.AppDatabase
import net.zodac.dicefive.data.scores.ScoreRepository
import net.zodac.dicefive.data.scores.buildAppDatabase
import net.zodac.dicefive.data.settings.SavedStyles
import net.zodac.dicefive.data.settings.SettingsRepository
import net.zodac.dicefive.data.settings.savedStylesFlow
import net.zodac.dicefive.game.StandardPerfectPlayTable
import net.zodac.dicefive.online.OnlineSettingsRepository
import net.zodac.dicefive.purchases.PurchasesRepository
import net.zodac.dicefive.resources.Res
import net.zodac.dicefive.ui.statistics.StatisticsSortOrder

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
    val onlineSettingsRepository: OnlineSettingsRepository,
    val purchasesRepository: PurchasesRepository,
    val buildInfo: BuildInfo,
    /** Where [savedStyles] is kept loaded - the process, not any one screen. */
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    /**
     * Debug builds only: the Styles page's hidden "pretend all styles are owned" switch (see
     * [net.zodac.dicefive.ui.styles.StylesViewModel.onUnlockedCountTapped]). Kept for the process, never saved.
     */
    val pretendOwnsAllStyles = MutableStateFlow(false)

    /**
     * Whether the "Unlock all styles" purchase unlocks every style: what the store last said
     * ([PurchasesRepository.ownsAllStyles]), or, in a debug build only, [pretendOwnsAllStyles].
     */
    val ownsAllStyles: Flow<Boolean> =
        combine(purchasesRepository.ownsAllStyles, pretendOwnsAllStyles) { owns, pretend -> owns || (pretend && buildInfo.isDebug) }
            .distinctUntilChanged()

    /**
     * The saved Styles picks, loaded as soon as the container is built and kept up to date for the
     * life of the process - null only until the first load. The menu's logo needs them at launch
     * anyway, so the Styles screen opens with them already in hand instead of waiting on its own load.
     */
    val savedStyles: StateFlow<SavedStyles?> =
        savedStylesFlow(settingsRepository, achievementsRepository, ownsAllStyles).stateIn(scope, SharingStarted.Eagerly, null)

    private val mutableDatabaseFailure = MutableStateFlow<String?>(null)

    /**
     * Why the scores database couldn't be opened (a migration missing or failing), as the error said it - null while it
     * opens, or did. Opened as the container is built, so the app can say so in place of its pages, rather than fail on
     * the first one to read a score.
     */
    val databaseFailure: StateFlow<String?> = mutableDatabaseFailure.asStateFlow()

    init {
        scope.launch {
            try {
                scoreRepository.open()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableDatabaseFailure.value = e.toString()
            }
        }
    }

    /** The Statistics screen's sort, loaded with the container so the screen opens already in order - null until then. */
    val statisticsSort: StateFlow<StatisticsSortOrder?> =
        settingsRepository.statisticsSort.map { StatisticsSortOrder.parse(it) }.stateIn(scope, SharingStarted.Eagerly, null)

    /** The Achievements page's Locked only switch, loaded with the container so the page opens already filtered - null until then. */
    val lockedOnlyAchievements: StateFlow<Boolean?> = settingsRepository.lockedOnlyAchievements.stateIn(scope, SharingStarted.Eagerly, null)

    /** The Styles page's Unlocked only switch, loaded as [lockedOnlyAchievements] is. */
    val unlockedOnlyStyles: StateFlow<Boolean?> = settingsRepository.unlockedOnlyStyles.stateIn(scope, SharingStarted.Eagerly, null)

    /**
     * The dice every game rolls ([net.zodac.dicefive.ui.game.GameViewModel]'s random). Replaced only by the store-asset
     * generator, to deal the rolls its promo video shows - see `.claude/STORE_ASSETS.md`.
     */
    @set:VisibleForTesting
    var diceRandom: Random = Random.Default

    private val standardPerfectPlay = scope.async(start = CoroutineStart.LAZY) { loadStandardPerfectPlayTable() }

    /**
     * Standard's perfect-play table, which a Hard CPU plays Standard by (see [StandardPerfectPlayTable]):
     * read from the app's resources the first time it's asked for and kept - a megabyte, not worth
     * reading for a player who never meets a Hard CPU in Standard. Null if it can't be read, in which
     * case Hard estimates, as it does in every other mode.
     */
    suspend fun standardPerfectPlayTable(): StandardPerfectPlayTable? = standardPerfectPlay.await()

    // A missing or damaged table costs Hard its strength in Standard, not the game: it falls back to
    // estimating. StandardPerfectPlayTableTest keeps the bundled one whole and current.
    private suspend fun loadStandardPerfectPlayTable(): StandardPerfectPlayTable? =
        orNullOnFailure { StandardPerfectPlayTable.decode(Res.readBytes(StandardPerfectPlayTable.RESOURCE_PATH)) }

    companion object {
        /**
         * Builds the container every platform shares: the scores database from the platform's
         * [databaseBuilder] (its driver and path), and one DataStore per [PreferencesFile] in [dataDir].
         */
        fun create(dataDir: String, databaseBuilder: RoomDatabase.Builder<AppDatabase>, buildInfo: BuildInfo): AppContainer {
            fun dataStore(file: PreferencesFile) = createPreferencesDataStore("$dataDir/${file.fileName}")

            return AppContainer(
                scoreRepository = ScoreRepository(databaseBuilder.buildAppDatabase().scoreDao()),
                settingsRepository = SettingsRepository(dataStore(PreferencesFile.SETTINGS)),
                achievementsRepository = AchievementsRepository(dataStore(PreferencesFile.ACHIEVEMENTS)),
                inProgressGameRepository = InProgressGameRepository(dataStore(PreferencesFile.IN_PROGRESS_GAME)),
                onlineSettingsRepository = OnlineSettingsRepository(dataStore(PreferencesFile.ONLINE)),
                purchasesRepository = PurchasesRepository(dataStore(PreferencesFile.PURCHASES)),
                buildInfo = buildInfo,
            )
        }
    }
}

val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("No AppContainer provided - the root composable provides the one the platform built")
}
