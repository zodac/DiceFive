package net.zodac.dicefive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import net.zodac.dicefive.model.PlayerColour
import net.zodac.dicefive.model.PlayerState
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.ui.game.PlayerHeaderBar
import net.zodac.dicefive.ui.style.GameVisualTheme
import net.zodac.dicefive.ui.style.LocalGameVisualTheme
import net.zodac.dicefive.ui.style.ScoreFrames
import net.zodac.dicefive.ui.theme.DiceFiveTheme

/** The screen this class's tests are drawn on. */
private val SCREEN = TestScreen(412, 732, 3f)

/**
 * The player tabs are the same height at one to four players (three or more step their names down a size) and with
 * any frame, so the board under them never moves when the player count or the frame changes.
 */
class PlayerTabHeightTest : UiTest() {
    private val everyone = listOf(
        PlayerState(name = "Sam", type = PlayerType.HUMAN, colour = PlayerColour.CYAN),
        PlayerState(name = "Ada", type = PlayerType.AI, colour = PlayerColour.GREEN),
        PlayerState(name = "Jo", type = PlayerType.HUMAN, colour = PlayerColour.PURPLE),
        PlayerState(name = "Alex", type = PlayerType.HUMAN, colour = PlayerColour.AMBER),
    ).mapIndexed { i, p -> p.copy(scorecard = p.scorecard + (ScoreCategory.CHANCE to listOf(120 + i))) }

    @Test
    fun theTabsAreOneHeightAtAnyPlayerCountAndFrame() = uiTest(SCREEN) {
        var fontScale by mutableStateOf(1f)
        var players by mutableStateOf(everyone.take(1))
        var frame by mutableStateOf(ScoreFrames.all.first())
        compose.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale)) {
                DiceFiveTheme {
                    CompositionLocalProvider(LocalGameVisualTheme provides GameVisualTheme(frame = frame)) {
                        // The game screen's width for the tabs: 16dp padding each side and the back arrow's 36dp.
                        Box(Modifier.testTag("header").width(344.dp)) {
                            PlayerHeaderBar(players, currentPlayerIndex = 0, viewedPlayerIndex = null, enabled = true, onPlayerTap = {})
                        }
                    }
                }
            }
        }
        for (scale in listOf(1f, 1.3f)) {
            fontScale = scale
            val heights = mutableMapOf<String, Float>()
            for (count in 1..4) for (f in ScoreFrames.all) {
                players = everyone.take(count)
                frame = f
                compose.waitForIdle()
                val bounds = compose.onNodeWithTag("header").getBoundsInRoot()
                heights["$count players, ${f.id}"] = (bounds.bottom - bounds.top).value
            }
            val solo = heights.getValue("1 players, ${ScoreFrames.all.first().id}")
            assertEquals(emptyMap<String, Float>(), heights.filterValues { it != solo }, "Tabs not ${solo}dp high at font scale $scale")
        }
    }
}
