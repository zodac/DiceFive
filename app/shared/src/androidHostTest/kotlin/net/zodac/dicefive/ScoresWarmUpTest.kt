package net.zodac.dicefive

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.zodac.dicefive.ui.scores.ScoresWarmUp
import net.zodac.dicefive.ui.scores.resetScoresWarmUpForTest
import net.zodac.dicefive.ui.theme.DiceFiveTheme

/**
 * The Leaderboard's hidden warm-up draws both views from its made-up board without a repository, then goes away.
 *
 * Android only: on the desktop test harness a composition's coroutines wait in real time, not on the test clock, so
 * the warm-up's start delay never ends there.
 */
class ScoresWarmUpTest : UiTest() {

    @Test
    fun drawsBothViewsThenGoesAway() = uiTest {
        // An earlier test in this process may already have run it (the menu shows it), and it runs once.
        resetScoresWarmUpForTest()
        compose.setContent { DiceFiveTheme { Box { ScoresWarmUp(width = 360.dp) } } }
        compose.mainClock.autoAdvance = false
        fun onScreen() = compose.onAllNodesWithText("Leaderboard").fetchSemanticsNodes().isNotEmpty()

        // Nothing before the menu has settled...
        repeat(60) { compose.mainClock.advanceTimeByFrame() }
        assertFalse(onScreen())
        // ...then the page is drawn for a while...
        var drawn = false
        repeat(300) {
            compose.mainClock.advanceTimeByFrame()
            drawn = drawn || onScreen()
        }
        assertTrue(drawn, "never drawn")
        // ...and once both views have had their frames, it's gone.
        assertFalse(onScreen())
    }
}
