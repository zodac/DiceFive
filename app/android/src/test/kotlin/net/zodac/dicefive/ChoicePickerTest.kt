package net.zodac.dicefive

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.zodac.dicefive.ui.common.ChoicePicker
import net.zodac.dicefive.ui.common.ModifierCustomValue
import net.zodac.dicefive.ui.common.ModifierPicker
import net.zodac.dicefive.ui.common.ModifierSetting
import net.zodac.dicefive.ui.common.ModifierStepper
import net.zodac.dicefive.ui.theme.DiceFiveTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The closed field shows only the pick; tapping it opens the list, and choosing closes it again. */
@RunWith(AndroidJUnit4::class)
class ChoicePickerTest {

    @get:Rule
    val compose = createComposeRule()

    private val options = (1..12).map { "Option $it" }
    private var picked by mutableStateOf("Option 1")

    private val showcase = Showcase(compose)

    @Test
    fun theClosedFieldIsADropdownShowingOnlyThePickAndTheListOpensToChooseOrCancel() {
        compose.setContent {
            DiceFiveTheme {
                ChoicePicker(title = "Thing", options = options, selected = picked, onSelect = { picked = it }, label = { it }, description = { "About $it" })
            }
        }
        compose.onNodeWithText("Option 1", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Option 2", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Option 1").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.DropdownList))

        // Cancel leaves the pick alone.
        compose.onNodeWithText("Option 1").performClick()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals("Option 1", picked)

        // Choosing an option closes the list and reports it.
        compose.onNodeWithText("Option 1").performClick()
        compose.onNodeWithText("Cancel").assertExists()
        compose.onNodeWithText("Option 3").performClick()
        assertEquals("Option 3", picked)
        compose.onNodeWithText("Cancel").assertDoesNotExist()
    }

    @Test
    fun theModifierPickerSummarisesTogglesStepsAndTakesATypedValueOnlyWithinItsRange() {
        // It summarises, toggles, and shows the values only while on.
        var on by mutableStateOf(false)
        var value by mutableStateOf(1)
        showcase.show {
            DiceFiveTheme {
                ModifierPicker(title = "Mods", description = "Extras", modifiers = listOf(ModifierSetting("Timer", "A limit", on, { on = it }, listOf("30s", "60s"), value, { value = it })))
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

        // A stepper steps within its range and speaks its value.
        var rolls by mutableStateOf(1)
        showcase.show {
            DiceFiveTheme {
                ModifierPicker(
                    title = "Mods",
                    description = "Extras",
                    modifiers = listOf(
                        ModifierSetting(
                            "Rolls", "How many", true, {},
                            steppers = listOf(ModifierStepper(rolls, 1..2, { rolls = it }, "Rolls per turn", "Decrease rolls per turn", "Increase rolls per turn", { if (it == 1) "1 roll" else "$it rolls" })),
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

        // It counts what is on, and notes the leaderboard only while any is.
        var counted by mutableStateOf(false)
        showcase.show {
            DiceFiveTheme {
                ModifierPicker(
                    title = "Mods",
                    description = "Extras",
                    activeNote = "Off the board",
                    modifiers = listOf(ModifierSetting("A", "a", counted, { counted = it }), ModifierSetting("B", "b", true, {}, lockedNote = "Set by the mode")),
                )
            }
        }
        compose.onNodeWithText("None").assertExists()
        compose.onNodeWithText("Off the board", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Extras").assertExists()
        counted = true
        compose.onNodeWithText("Extras").assertDoesNotExist()
        compose.onNodeWithText("1 enabled").assertExists()
        compose.onNodeWithText("Off the board", substring = true).assertExists()

        // A typed value under the least keeps it open until it's mended.
        var timer by mutableStateOf(1)
        var custom by mutableStateOf<Int?>(null)
        showcase.show {
            DiceFiveTheme {
                ModifierPicker(
                    title = "Mods",
                    description = "Extras",
                    modifiers = listOf(
                        ModifierSetting(
                            "Timer", "A limit", true, {}, listOf("30s", "60s"), timer, { timer = it },
                            customValue = ModifierCustomValue(
                                active = custom != null, text = "", label = "Custom time", unit = "s", maxDigits = 3,
                                onValueChange = { custom = it }, min = 5, belowMinMessage = "At least 5",
                            ),
                        ),
                    ),
                )
            }
        }
        compose.onNodeWithText("1 enabled").performClick()
        compose.onNodeWithContentDescription("Custom time").performTextInput("1")
        // Under the least: Done is off, and is not passed on as the value.
        compose.onNodeWithText("Done").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Back").assertDoesNotExist()
        assertEquals(null, custom)
        compose.onNodeWithContentDescription("Custom time").performTextInput("5")
        compose.onNodeWithText("Done").assertIsEnabled()
        compose.onNodeWithContentDescription("Back").assertExists()
        assertEquals(15, custom)
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithText("Done").assertDoesNotExist()
    }
}
