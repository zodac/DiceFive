package net.zodac.dicefive.app

import androidx.compose.runtime.staticCompositionLocalOf
import net.zodac.dicefive.data.achievements.AchievementsRepository
import net.zodac.dicefive.data.game.InProgressGameRepository
import net.zodac.dicefive.data.scores.ScoreRepository
import net.zodac.dicefive.data.settings.SettingsRepository

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
)

val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("No AppContainer provided - the root composable provides the one the platform built")
}
