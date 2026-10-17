package net.zodac.dicefive.ui.game

import kotlin.random.Random
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import net.zodac.dicefive.data.InMemoryPreferences
import net.zodac.dicefive.data.achievements.AchievementStore
import net.zodac.dicefive.data.achievements.InMemoryAchievementStore
import net.zodac.dicefive.data.game.InProgressGameRepository
import net.zodac.dicefive.data.scores.InMemoryScoreDao
import net.zodac.dicefive.data.scores.ScoreRepository
import net.zodac.dicefive.data.settings.SettingsRepository
import net.zodac.dicefive.game.StandardPerfectPlayTable
import net.zodac.dicefive.game.localHourOfDay
import net.zodac.dicefive.game.nowEpochMillis

/** A game view model over in-memory repositories, with any a test cares about passed in. */
fun gameViewModel(
    scoreRepository: ScoreRepository = ScoreRepository(InMemoryScoreDao()),
    settingsRepository: SettingsRepository = SettingsRepository(InMemoryPreferences()),
    inProgressGameRepository: InProgressGameRepository = InProgressGameRepository(InMemoryPreferences()),
    achievementsRepository: AchievementStore = InMemoryAchievementStore(),
    random: Random = Random.Default,
    aiDispatcher: CoroutineDispatcher = Dispatchers.Default,
    isDebugBuild: Boolean = false,
    standardPerfectPlay: (suspend () -> StandardPerfectPlayTable?)? = null,
    localHour: () -> Int = { localHourOfDay(nowEpochMillis()) },
) = GameViewModel(
    scoreRepository,
    settingsRepository,
    inProgressGameRepository,
    achievementsRepository,
    random,
    aiDispatcher,
    isDebugBuild,
    standardPerfectPlay,
    localHour,
)
