package net.zodac.dicefive

import kotlin.random.Random
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import net.zodac.dicefive.data.achievements.AchievementStore
import net.zodac.dicefive.data.achievements.AchievementsState
import net.zodac.dicefive.data.game.InProgressGameRepository
import net.zodac.dicefive.data.scores.ScoreRepository
import net.zodac.dicefive.data.settings.SavedStyles
import net.zodac.dicefive.data.settings.SettingsRepository
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.model.AchievementCounter
import net.zodac.dicefive.online.OnlineSettingsRepository
import net.zodac.dicefive.ui.achievements.AchievementsViewModel
import net.zodac.dicefive.ui.game.GameViewModel
import net.zodac.dicefive.ui.settings.SettingsViewModel
import net.zodac.dicefive.ui.styles.StylesViewModel

/** An [AchievementStore] kept in memory, starting as [initial]. The `:app:shared` tests have their own; this module can't see them. */
internal class InMemoryAchievementStore(initial: AchievementsState = AchievementsState()) : AchievementStore {
    private val _state = MutableStateFlow(initial)
    override val state: Flow<AchievementsState> = _state
    override suspend fun current(): AchievementsState = _state.value
    override suspend fun record(unlockedAt: Map<Achievement, Long>, counters: Map<AchievementCounter, Int>) = Unit
    override suspend fun resetAll() = Unit
    override suspend fun forceLock(achievement: Achievement) = Unit
}

/** An Achievements view model over in-memory repositories: nothing unlocked and no scores, unless a test passes its own. */
internal fun achievementsViewModel(
    store: AchievementStore = InMemoryAchievementStore(),
    scores: ScoreRepository = ScoreRepository(RowsDao(emptyList())),
) = AchievementsViewModel(store, scores, onlineSettingsRepository = OnlineSettingsRepository(InMemoryPreferences()))

/** A Settings view model over in-memory repositories, with the settings passed in when a test needs to read them back. */
internal fun settingsViewModel(settings: SettingsRepository = SettingsRepository(InMemoryPreferences())) = SettingsViewModel(
    settings,
    InMemoryAchievementStore(),
    ScoreRepository(RowsDao(emptyList())),
    OnlineSettingsRepository(InMemoryPreferences()),
)

/** A Styles view model over in-memory repositories; [saved] stands in for the app's own copy of the picks, as the container passes it. */
internal fun stylesViewModel(saved: StateFlow<SavedStyles?>? = null) = StylesViewModel(
    SettingsRepository(InMemoryPreferences()),
    InMemoryAchievementStore(),
    saved,
)

/**
 * A game view model over in-memory repositories: a first game already finished, so the form's modes are unlocked, unless
 * [modesUnlocked] is false. Seeded dice and a 2pm clock ([TEST_LOCAL_HOUR]), so no run depends on when it runs.
 */
internal fun gameViewModel(modesUnlocked: Boolean = true) = GameViewModel(
    ScoreRepository(RowsDao(emptyList())),
    SettingsRepository(InMemoryPreferences()).also { if (modesUnlocked) runBlocking { it.setFirstGameCompleted() } },
    InProgressGameRepository(InMemoryPreferences()),
    InMemoryAchievementStore(),
    random = Random(1),
    localHour = { TEST_LOCAL_HOUR },
)

/** The hour a test's game starts at: clear of the hours that earn Rolling The Midnight Dice. */
internal const val TEST_LOCAL_HOUR = 14
