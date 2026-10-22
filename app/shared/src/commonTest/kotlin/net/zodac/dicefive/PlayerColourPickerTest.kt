package net.zodac.dicefive

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import kotlin.test.Test
import kotlin.test.assertEquals
import net.zodac.dicefive.model.PlayerColour
import net.zodac.dicefive.ui.setup.PlayerColourPicker
import net.zodac.dicefive.ui.theme.DiceFiveTheme

/** The colour circle on the setup screen: what TalkBack says for it and for each choice in its pop-up. */
class PlayerColourPickerTest : UiTest() {

    private var colour by mutableStateOf(PlayerColour.GREEN)

    private fun show() {
        compose.setContent {
            DiceFiveTheme {
                PlayerColourPicker(
                    slot = 2,
                    colour = colour,
                    holders = mapOf(PlayerColour.CYAN to 1, PlayerColour.GREEN to 2),
                    onColourChange = { colour = it },
                )
            }
        }
    }

    @Test
    fun theCircleIsAButtonNamingThePlayersColourWhosePopUpOffersRadioButtonsSayingASwapAndPickingOneClosesIt() = uiTest {
        show()
        compose.onNodeWithContentDescription("Player 2 colour, Green").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))

        // The choices are radio buttons, the current one selected, and a swap said.
        compose.onNodeWithContentDescription("Player 2 colour, Green").performClick()
        compose.onNodeWithContentDescription("Green").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).assertIsSelected()
        compose.onNodeWithContentDescription("Cyan, swaps with player 1").assertExists()
        compose.onNodeWithContentDescription("Pink").assertExists()

        // Picking a colour reports it and closes the pop-up.
        compose.onNodeWithContentDescription("Pink").performClick()
        compose.waitForIdle()
        assertEquals(PlayerColour.PINK, colour)
        compose.onNodeWithContentDescription("Pink").assertDoesNotExist()
        compose.onNodeWithContentDescription("Player 2 colour, Pink").assertExists()
    }
}
