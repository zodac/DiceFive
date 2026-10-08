package net.zodac.dicefive

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.random.Random
import net.zodac.dicefive.game.GameEngine
import net.zodac.dicefive.model.PlayerConfig
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.ui.game.GameBoard
import net.zodac.dicefive.ui.theme.DiceFiveTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Score boxes only take a tap once a roll has fully played out - not while the cup is shaking, and
 * not while the dice it produced are still tumbling to a stop on the mat.
 */
@RunWith(AndroidJUnit4::class)
class GameBoardScoringGateTest {

    @get:Rule
    val compose = createComposeRule()

    private val rolled = GameEngine.rollDice(
        GameEngine.newGame(listOf(PlayerConfig(slot = 0, type = PlayerType.HUMAN, name = "Alex"))),
        Random(7),
    )

    private var rolling by mutableStateOf(false)
    private var diceSettling by mutableStateOf(false)

    private fun showBoard() {
        compose.setContent {
            DiceFiveTheme {
                GameBoard(
                    state = rolled,
                    rolling = rolling,
                    diceSettling = diceSettling,
                    canUndo = false,
                    onScoreCategory = {},
                    onCupTap = {},
                    onUndo = {},
                )
            }
        }
    }

    private fun tappableCount(rolling: Boolean, diceSettling: Boolean): Int {
        this.rolling = rolling
        this.diceSettling = diceSettling
        compose.waitForIdle()
        return compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().size
    }

    @Test
    fun scoresCanBeTappedOnlyOnceTheDiceHaveSettledAndSettlingDiceOfferNoMoreThanAShakingCup() {
        showBoard()
        val settled = tappableCount(rolling = false, diceSettling = false)
        val settling = tappableCount(rolling = false, diceSettling = true)
        assertTrue("settled dice should offer score boxes to tap ($settled) that settling ones don't ($settling)", settled > settling)
        assertEquals(tappableCount(rolling = true, diceSettling = false), settling)
    }
}
