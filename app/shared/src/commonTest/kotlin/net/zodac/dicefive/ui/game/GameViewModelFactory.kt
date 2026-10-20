package net.zodac.dicefive.ui.game

import kotlin.random.Random
import kotlinx.coroutines.CoroutineDispatcher
import net.zodac.dicefive.data.InMemoryPreferences
import net.zodac.dicefive.data.achievements.AchievementStore
import net.zodac.dicefive.data.achievements.InMemoryAchievementStore
import net.zodac.dicefive.data.game.InProgressGameRepository
import net.zodac.dicefive.data.scores.InMemoryScoreDao
import net.zodac.dicefive.data.scores.ScoreRepository
import net.zodac.dicefive.data.settings.SettingsRepository
import net.zodac.dicefive.game.StandardPerfectPlayTable
import net.zodac.dicefive.game.TutorialScript

/**
 * A game view model over in-memory repositories, with any a test cares about passed in. Nothing real by default: the
 * CPU runs on the test's [aiDispatcher], the dice are seeded, and the clock reads 2pm, clear of the hours that earn
 * Rolling The Midnight Dice.
 */
fun gameViewModel(
    scoreRepository: ScoreRepository = ScoreRepository(InMemoryScoreDao()),
    settingsRepository: SettingsRepository = SettingsRepository(InMemoryPreferences()),
    inProgressGameRepository: InProgressGameRepository = InProgressGameRepository(InMemoryPreferences()),
    achievementsRepository: AchievementStore = InMemoryAchievementStore(),
    random: Random = Random(1),
    aiDispatcher: CoroutineDispatcher,
    isDebugBuild: Boolean = false,
    standardPerfectPlay: (suspend () -> StandardPerfectPlayTable?)? = null,
    localHour: () -> Int = { 14 },
    tutorial: TutorialScript? = null,
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
    tutorial,
)
