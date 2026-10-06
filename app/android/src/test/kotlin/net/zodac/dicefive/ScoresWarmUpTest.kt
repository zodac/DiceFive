package net.zodac.dicefive

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.zodac.dicefive.ui.scores.ScoresWarmUp
import net.zodac.dicefive.ui.theme.DiceFiveTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The Leaderboard's hidden warm-up draws both views from its made-up board without a repository, then goes away. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ScoresWarmUpTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun drawsBothViewsThenGoesAway() {
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
        assertTrue("never drawn", drawn)
        // ...and once both views have had their frames, it's gone.
        assertFalse(onScreen())
    }
}
