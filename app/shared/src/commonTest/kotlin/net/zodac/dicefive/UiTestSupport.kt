package net.zodac.dicefive

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import kotlin.random.Random
import kotlinx.coroutines.flow.StateFlow
import net.zodac.dicefive.app.AppContainer
import net.zodac.dicefive.app.BuildInfo
import net.zodac.dicefive.app.UnlockedStyle
import net.zodac.dicefive.data.InMemoryPreferences
import net.zodac.dicefive.data.achievements.AchievementStore
import net.zodac.dicefive.data.achievements.AchievementsRepository
import net.zodac.dicefive.data.achievements.InMemoryAchievementStore
import net.zodac.dicefive.data.game.InProgressGameRepository
import net.zodac.dicefive.data.scores.InMemoryScoreDao
import net.zodac.dicefive.data.scores.ScoreEntry
import net.zodac.dicefive.data.scores.ScoreRepository
import net.zodac.dicefive.data.settings.SavedStyles
import net.zodac.dicefive.data.settings.SettingsRepository
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.online.OnlineSettingsRepository
import net.zodac.dicefive.purchases.PurchasesRepository
import net.zodac.dicefive.ui.achievements.AchievementsViewModel
import net.zodac.dicefive.ui.game.GameViewModel
import net.zodac.dicefive.ui.style.StyleCatalog
import net.zodac.dicefive.ui.style.StyleFamily
import net.zodac.dicefive.ui.style.TableArt
import net.zodac.dicefive.ui.styles.StylesViewModel
import org.jetbrains.compose.resources.getString

/** An Achievements view model over in-memory repositories: nothing unlocked and no scores, unless a test passes its own. */
internal fun achievementsViewModel(
    store: AchievementStore = InMemoryAchievementStore(),
    scores: ScoreRepository = ScoreRepository(InMemoryScoreDao()),
    settings: SettingsRepository = SettingsRepository(InMemoryPreferences()),
) = AchievementsViewModel(store, scores, onlineSettingsRepository = OnlineSettingsRepository(InMemoryPreferences()), settingsRepository = settings)

/** A Styles view model over in-memory repositories; [saved] stands in for the app's own copy of the picks, as the container passes it. */
internal fun stylesViewModel(
    saved: StateFlow<SavedStyles?>? = null,
    settings: SettingsRepository = SettingsRepository(InMemoryPreferences()),
) = StylesViewModel(settings, InMemoryAchievementStore(), saved)

/** A Leaderboard row for player [id], scoring [score] in [mode]. */
internal fun rowOf(id: Long, score: Int, mode: GameMode) = ScoreEntry(
    playerName = "Player$id", score = score, timestampEpochMillis = 1_000L * id, won = null, isPrimaryPlayer = false,
    fiveOfAKindCount = 0, zeroedCategoryCount = 0, upperSectionTotal = 0, chanceScore = 0,
    threeOfAKindScore = 0, fourOfAKindScore = 0, gameModeId = mode.id,
)

/**
 * A game view model for a screen test, over in-memory repositories: a first game already finished, so the form's modes
 * are unlocked, unless [modesUnlocked] is false. Seeded dice and a 2pm clock ([TEST_LOCAL_HOUR]), so no run depends on
 * when it runs (the first game's flag is the saved name, written by hand, as the settings tests do). The CPU plays on the view model's own dispatcher, as in the app; a view-model test passes its own with
 * `ui.game.gameViewModel` instead.
 */
internal fun gameViewModel(modesUnlocked: Boolean = true) = GameViewModel(
    ScoreRepository(InMemoryScoreDao()),
    SettingsRepository(
        InMemoryPreferences(if (modesUnlocked) preferencesOf(booleanPreferencesKey("first_game_completed") to true) else emptyPreferences()),
    ),
    InProgressGameRepository(InMemoryPreferences()),
    InMemoryAchievementStore(),
    random = Random(1),
    localHour = { TEST_LOCAL_HOUR },
)

/** The hour a test's game starts at: clear of the hours that earn Rolling The Midnight Dice. */
internal const val TEST_LOCAL_HOUR = 14

/** The family of this catalogue called [name] in the test's language. */
internal suspend fun <T : TableArt> StyleCatalog<T>.familyNamed(name: String): StyleFamily<T> =
    families.first { getString(it.name) == name }

/** A banner's entry for the style [name] of this catalogue. */
internal suspend fun StyleCatalog<*>.unlockedStyle(name: String): UnlockedStyle = UnlockedStyle(familyNamed(name).name, noun)

/**
 * The app's container over in-memory storage, for a screen that takes its view model from `LocalAppContainer`: nothing saved, a release build's flags, and nothing shared with another
 * test.
 */
internal fun inMemoryAppContainer(settings: SettingsRepository = SettingsRepository(InMemoryPreferences())) = AppContainer(
    scoreRepository = ScoreRepository(InMemoryScoreDao()),
    settingsRepository = settings,
    achievementsRepository = AchievementsRepository(InMemoryPreferences()),
    inProgressGameRepository = InProgressGameRepository(InMemoryPreferences()),
    onlineSettingsRepository = OnlineSettingsRepository(InMemoryPreferences()),
    purchasesRepository = PurchasesRepository(InMemoryPreferences()),
    buildInfo = BuildInfo(versionName = "test", isDebug = false),
)
