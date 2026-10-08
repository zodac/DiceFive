package net.zodac.dicefive.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records what a typical session touches, so the generated profile compiles those paths ahead of
 * time: cold start, an achievement banner and its jump to the Achievements screen, a game (new,
 * played a turn, a game in another mode, then resumed), and every screen reachable from the menu. Run on a device: `./gradlew :app:android:generateBaselineProfile`.
 *
 * Deliberately left out: opening a link (it leaves the app, so no app code runs) and selecting text
 * (Compose's and the platform's code, which their libraries' own profiles already cover).
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = PACKAGE,
        // Laps: the library's own defaults (up to 15, stopping after 3 that add nothing new) unless
        // `-Pandroid.testInstrumentationRunnerArguments.journeyLaps=N` asks for exactly N - one or two
        // while the journey is being checked, so a failing step stops the run quickly.
        maxIterations = intArgument("journeyLaps") ?: 15,
        stableIterations = intArgument("journeyLaps") ?: 3,
        // The journey goes far past startup; the startup profile is StartupProfileGenerator's alone.
        includeInStartupProfile = false,
    ) {
        pressHome()
        startActivityAndWait()

        // Achievements locked again so the banner below can fire, then the banner: tapped into
        // existence from the logo, long-pressed to jump to its achievement and its glow.
        resetAchievements()
        visitAchievementBanner()

        // A game is left saved behind, so the menu then offers Continue - and the next lap of the
        // run, which starts from that state, takes the New Game route instead of Play.
        playATurn()
        playAModeGame()
        resumeGame()

        visitStyles()
        visitAchievements()
        visitScoreScreens()
        visitRules()
        visitSettings()
    }
}
