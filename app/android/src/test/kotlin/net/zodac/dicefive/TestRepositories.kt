package net.zodac.dicefive

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import net.zodac.dicefive.data.achievements.AchievementStore
import net.zodac.dicefive.data.achievements.AchievementsState
import net.zodac.dicefive.data.scores.ScoreRepository
import net.zodac.dicefive.data.settings.SettingsRepository
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.model.AchievementCounter
import net.zodac.dicefive.online.OnlineSettingsRepository
import net.zodac.dicefive.ui.achievements.AchievementsViewModel
import net.zodac.dicefive.ui.settings.SettingsViewModel

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
