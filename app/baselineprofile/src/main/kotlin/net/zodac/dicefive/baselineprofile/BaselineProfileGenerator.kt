package net.zodac.dicefive.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records what a typical session touches, so the generated profile compiles those paths ahead of
 * time: cold start, a game (new, played a turn, then resumed), and every screen reachable from the
 * menu. Run on a device: `./gradlew :app:android:generateBaselineProfile`.
 *
 * Deliberately left out: opening a link (it leaves the app, so no app code runs) and selecting text
 * (Compose's and the platform's code, which their libraries' own profiles already cover).
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = PACKAGE, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()

        // A game is left saved behind, so the menu then offers Continue - and the next lap of the
        // run, which starts from that state, takes the New Game route instead of Play.
        playATurn()
        resumeGame()

        visitStyles()
        visitAchievements()
        visitScoreScreens()
        visitRules()
        visitSettings()
    }
}
