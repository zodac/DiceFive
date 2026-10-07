package net.zodac.dicefive

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.zodac.dicefive.ui.common.ChoicePicker
import net.zodac.dicefive.ui.common.ModifierPicker
import net.zodac.dicefive.ui.common.ModifierSetting
import net.zodac.dicefive.ui.common.ModifierStepper
import net.zodac.dicefive.ui.theme.DiceFiveTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The closed field shows only the pick; tapping it opens the list, and choosing closes it again. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ChoicePickerTest {

    @get:Rule
    val compose = createComposeRule()

    private val options = (1..12).map { "Option $it" }
    private var picked by mutableStateOf("Option 1")

    private fun show() {
        compose.setContent {
            DiceFiveTheme {
                ChoicePicker(
                    title = "Thing",
                    options = options,
                    selected = picked,
                    onSelect = { picked = it },
                    label = { it },
                    description = { "About $it" },
                )
            }
        }
    }

    @Test
    fun closedFieldShowsOnlyTheCurrentPickAndIsADropdown() {
        show()
        compose.onNodeWithText("Option 1", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Option 2", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Option 1").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.DropdownList))
    }

    @Test
    fun choosingAnOptionClosesTheListAndReportsIt() {
        show()
        compose.onNodeWithText("Option 1").performClick()
        compose.onNodeWithText("Cancel").assertExists()
        compose.onNodeWithText("Option 3").performClick()
        assertEquals("Option 3", picked)
        compose.onNodeWithText("Cancel").assertDoesNotExist()
    }

    @Test
    fun cancelLeavesThePickAlone() {
        show()
        compose.onNodeWithText("Option 1").performClick()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals("Option 1", picked)
    }

    @Test
    fun modifierPickerSummarisesAndTogglesAndShowsValuesOnlyWhileOn() {
        var on by mutableStateOf(false)
        var value by mutableStateOf(1)
        compose.setContent {
            DiceFiveTheme {
                ModifierPicker(
                    title = "Mods",
                    description = "Extras",
                    modifiers = listOf(
                        ModifierSetting("Timer", "A limit", on, { on = it }, listOf("30s", "60s"), value, { value = it }),
                    ),
                )
            }
        }
        compose.onNodeWithText("None").assertExists()
        compose.onNodeWithText("None").performClick()
        // The values are hidden while the modifier is off, and open out once it's on.
        compose.onNodeWithText("30s").assertDoesNotExist()
        compose.onNodeWithText("Timer").performClick()
        assertEquals(true, on)
        compose.onNodeWithText("30s").assertExists()
        compose.onNodeWithText("30s").performClick()
        compose.onNodeWithText("Done").performClick()
        assertEquals(0, value)
        compose.onNodeWithText("1 enabled").assertExists()
    }

    @Test
    fun modifierStepperStepsWithinItsRangeAndSpeaksItsValue() {
        var rolls by mutableStateOf(1)
        compose.setContent {
            DiceFiveTheme {
                ModifierPicker(
                    title = "Mods",
                    description = "Extras",
                    modifiers = listOf(
                        ModifierSetting(
                            "Rolls", "How many", true, {},
                            steppers = listOf(ModifierStepper(rolls, 1..2, { rolls = it }, "Rolls per turn", "rolls", "roll")),
                        ),
                    ),
                )
            }
        }
        compose.onNodeWithText("1 enabled").performClick()
        compose.onNodeWithContentDescription("Rolls per turn, 1 roll").assertExists()
        compose.onNodeWithContentDescription("Decrease rolls per turn").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Increase rolls per turn").performClick()
        assertEquals(2, rolls)
        compose.onNodeWithContentDescription("Rolls per turn, 2 rolls").assertExists()
        compose.onNodeWithContentDescription("Increase rolls per turn").assertIsNotEnabled()
    }

    @Test
    fun modifierPickerCountsWhatIsOnAndNotesTheLeaderboardOnlyWhileAnyIs() {
        var on by mutableStateOf(false)
        compose.setContent {
            DiceFiveTheme {
                ModifierPicker(
                    title = "Mods",
                    description = "Extras",
                    activeNote = "Off the board",
                    modifiers = listOf(
                        ModifierSetting("A", "a", on, { on = it }),
                        ModifierSetting("B", "b", true, {}, lockedNote = "Set by the mode"),
                    ),
                )
            }
        }
        compose.onNodeWithText("None").assertExists()
        compose.onNodeWithText("Off the board", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Extras").assertExists()
        on = true
        compose.onNodeWithText("Extras").assertDoesNotExist()
        compose.onNodeWithText("1 enabled").assertExists()
        compose.onNodeWithText("Off the board", substring = true).assertExists()
    }
}
