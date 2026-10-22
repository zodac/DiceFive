package net.zodac.dicefive.ui.settings

import kotlinx.coroutines.flow.flowOf
import net.zodac.dicefive.data.InMemoryPreferences
import net.zodac.dicefive.data.achievements.AchievementStore
import net.zodac.dicefive.data.achievements.InMemoryAchievementStore
import net.zodac.dicefive.data.scores.InMemoryScoreDao
import net.zodac.dicefive.data.scores.ScoreRepository
import net.zodac.dicefive.data.settings.SettingsRepository
import net.zodac.dicefive.game.nowEpochMillis
import net.zodac.dicefive.online.OnlineSettingsRepository
import net.zodac.dicefive.online.ProgressTransfer

/** A Settings view model over in-memory repositories, with any a test cares about passed in. */
fun settingsViewModel(
    settings: SettingsRepository = SettingsRepository(InMemoryPreferences()),
    achievements: AchievementStore = InMemoryAchievementStore(),
    scores: ScoreRepository = ScoreRepository(InMemoryScoreDao()),
    online: OnlineSettingsRepository = OnlineSettingsRepository(InMemoryPreferences()),
    now: () -> Long = ::nowEpochMillis,
    progressTransfer: ProgressTransfer? = null,
    ownsAllStyles: Boolean = false,
) = SettingsViewModel(settings, achievements, scores, online, now, progressTransfer, flowOf(ownsAllStyles))
