package net.zodac.dicefive

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlin.test.Test
import kotlin.test.assertEquals
import net.zodac.dicefive.ui.menu.PlayButton
import net.zodac.dicefive.ui.theme.DiceFiveTheme

/**
 * The menu's Play button: one button with no saved game, split into New Game and Continue with one -
 * each half acting straight away, with no dialog asking which.
 */
class PlayButtonTest : UiTest() {

    private val taps = mutableListOf<String>()

    private val showcase = Showcase { compose }

    private fun show(hasInProgressGame: Boolean) = showcase.show {
        DiceFiveTheme {
            PlayButton(
                hasInProgressGame = hasInProgressGame,
                onContinue = { taps += "continue" },
                onNewGame = { taps += "new" },
            )
        }
    }

    @Test
    fun `with no saved game Play starts a new one - with one each half does its own action`() = uiTest {
        show(hasInProgressGame = false)
        compose.onNodeWithText("Continue").assertDoesNotExist()
        compose.onNodeWithText("Play").performClick()
        assertEquals(listOf("new"), taps)

        taps.clear()
        show(hasInProgressGame = true)
        compose.onNodeWithText("Play").assertDoesNotExist()
        compose.onNodeWithText("New Game").performClick()
        compose.onNodeWithText("Continue").performClick()
        assertEquals(listOf("new", "continue"), taps)
    }
}
