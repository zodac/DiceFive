package net.zodac.dicefive.ui.settings

import net.zodac.dicefive.data.InMemoryPreferences
import net.zodac.dicefive.data.achievements.AchievementStore
import net.zodac.dicefive.data.achievements.InMemoryAchievementStore
import net.zodac.dicefive.data.scores.InMemoryScoreDao
import net.zodac.dicefive.data.scores.ScoreRepository
import net.zodac.dicefive.data.settings.SettingsRepository
import net.zodac.dicefive.game.nowEpochMillis
import net.zodac.dicefive.online.OnlineSettingsRepository

/** A Settings view model over in-memory repositories, with any a test cares about passed in. */
fun settingsViewModel(
    settings: SettingsRepository = SettingsRepository(InMemoryPreferences()),
    achievements: AchievementStore = InMemoryAchievementStore(),
    scores: ScoreRepository = ScoreRepository(InMemoryScoreDao()),
    online: OnlineSettingsRepository = OnlineSettingsRepository(InMemoryPreferences()),
    now: () -> Long = ::nowEpochMillis,
) = SettingsViewModel(settings, achievements, scores, online, now)
