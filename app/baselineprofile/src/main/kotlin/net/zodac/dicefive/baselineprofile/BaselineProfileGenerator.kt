package net.zodac.dicefive.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records what a typical session touches, so the generated profile compiles those paths ahead of
 * time: cold start, on the first lap an achievement banner with its jump to the Achievements screen and a solo game of
 * Standard played to the end to unlock the setup's modes, a game (new,
 * played a turn, a game in each of three other modes, then resumed and rolled in landscape), one more played to the
 * end (the Game Over page, its scorecards review, a CPU's turns), a few turns of the tutorial, and every screen
 * reachable from the menu. Run on a device: `./gradlew :app:android:generateBaselineProfile`.
 *
 * Deliberately left out: opening a link (it leaves the app, so no app code runs) and selecting text
 * (Compose's and the platform's code, which their libraries' own profiles already cover).
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    private val laps = intArgument("journeyLaps") ?: DEFAULT_LAPS
    private var lap = 0

    @Test
    fun generate() = rule.collect(
        packageName = PACKAGE,
        // Laps: the library's own defaults (up to 15, stopping after 3 that add nothing new) unless
        // `-Pandroid.testInstrumentationRunnerArguments.journeyLaps=N` asks for exactly N - one or two
        // while the journey is being checked, so a failing step stops the run quickly.
        maxIterations = laps,
        stableIterations = intArgument("journeyLaps") ?: 3,
        // The journey goes far past startup; the startup profile is StartupProfileGenerator's alone.
        includeInStartupProfile = false,
    ) {
        val thisLap = ++lap
        try {
            journey(first = thisLap == 1)
        } catch (failure: Throwable) {
            journeyFailed = true
            captureFailure(thisLap)
            throw failure
        }
    }

    private fun MacrobenchmarkScope.journey(first: Boolean) {
        // Only the first lap starts from nothing, whatever a retried run left on the emulator: the rest keep its unlocks.
        if (first) wipeAppData()
        pressHome()
        startActivityAndWait()

        if (first) {
            // The banner: tapped into existence from the logo, long-pressed to jump to its achievement and its glow. Its
            // achievement can only be earned once, so this lap alone can raise it.
            visitAchievementBanner()

            // A fresh install locks the setup's modes and modifiers until a game is finished, and the steps below pick
            // both. The unlock survives from lap to lap.
            unlockModes()
        }

        // A game is left saved behind, so the menu then offers Continue - and the next lap of the
        // run, which starts from that state, takes the New Game route instead of Play.
        playATurn()
        playAModeGame()
        resumeGame()

        // A game played to the end - after the saved one has been resumed, as finishing a game clears it. Only on
        // the first lap of a short run: the profile is what every lap touched together, so repeating it adds
        // nothing there. A long run (the one that soaks a changed journey) plays it every lap, to find its races.
        if (first || laps > SHORT_RUN_LAPS) playFinishedGame()

        visitStyles()
        visitAchievements()
        visitScoreScreens()
        visitRules()
        visitSettings()
    }
}

private const val DEFAULT_LAPS = 15

/**
 * Set as a lap of [BaselineProfileGenerator] fails. The test classes share one instrumentation process and run in
 * name order, so [StartupProfileGenerator] sees it and skips itself: a failed journey fails the run anyway.
 */
@Volatile
internal var journeyFailed = false

/** A run of this many laps or fewer is a release's usual one (android-release.yml); longer ones are the soak. */
private const val SHORT_RUN_LAPS = 3
