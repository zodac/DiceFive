package net.zodac.dicefive

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import net.zodac.dicefive.model.Difficulty
import net.zodac.dicefive.ui.setup.DifficultySelector
import net.zodac.dicefive.ui.theme.DiceFiveTheme

/**
 * The AI difficulty row: words while they fit, initials on a row too narrow for them, the word spoken either
 * way. This pins which is chosen (a 40dp row is the "too narrow" case) - the stepping between 14sp and 12sp is for a
 * device.
 */
class DifficultySelectorTest : UiTest() {

    private val showcase = Showcase { compose }

    private fun show(width: Int) = showcase.show {
        DiceFiveTheme { DifficultySelector(selected = Difficulty.MEDIUM, onSelect = {}, modifier = Modifier.width(width.dp)) }
    }

    @Test
    fun aRowWithRoomShowsTheWordsAndOneTooNarrowForMediumAt12spShowsInitialsStillSpeakingTheWords() = uiTest {
        show(width = 300)
        compose.onNodeWithText("Easy").assertExists()
        compose.onNodeWithText("Medium").assertExists()
        compose.onNodeWithText("Hard").assertExists()
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("Medium").fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!(results)
        assertEquals(14f, results.first().layoutInput.style.fontSize.value, 0.01f)

        show(width = 40)
        compose.onNodeWithText("E").assertExists()
        compose.onNodeWithText("M").assertExists()
        compose.onNodeWithText("H").assertExists()
        compose.onNodeWithText("Medium").assertDoesNotExist()
        compose.onNodeWithContentDescription("Medium").assertExists()
        compose.onNodeWithContentDescription("Easy").assertExists()
        compose.onNodeWithContentDescription("Hard").assertExists()
    }
}
