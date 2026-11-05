package net.zodac.dicefive

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.zodac.dicefive.ui.common.DropdownChoiceField
import net.zodac.dicefive.ui.theme.DiceFiveTheme

private val OPTIONS = listOf("First", "Second", "Third", "Fourth", "Fifth", "Sixth", "Seventh", "Last")

/** A drop-down's first and last options reach the menu's edges, with none of the menu's stock padding around them. */
class DropdownChoiceFieldTest : UiTest() {

    private fun ComposeUiTest.openMenu() {
        setContent {
            DiceFiveTheme {
                DropdownChoiceField(
                    options = OPTIONS,
                    selected = OPTIONS[1],
                    onSelect = {},
                    label = "Board",
                    optionText = { it },
                    dividerAfterFirst = true,
                    dividerBeforeLast = true,
                )
            }
        }
        onNode(hasContentDescription("Board, Second")).performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.menuBounds(): Rect = onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.CollectionInfo)).fetchSemanticsNode().boundsInRoot

    private fun ComposeUiTest.optionBounds(text: String): Rect = onNode(hasContentDescription(text)).fetchSemanticsNode().boundsInRoot

    @Test
    fun `the first and last options reach the menu's top and bottom edges`() = uiTest(TestScreen(360, 800)) {
        openMenu()
        assertEquals(menuBounds().top, optionBounds("First").top, "Space above the first option")
        assertEquals(menuBounds().bottom, optionBounds("Last").bottom, "Space below the last option")
    }

    @Test
    fun `a menu too tall for the screen still scrolls to its last option, which ends at the menu's edge`() = uiTest(TestScreen(360, 240)) {
        openMenu()
        assertEquals(menuBounds().top, optionBounds("First").top, "Space above the first option")
        val rowHeight = optionBounds("First").height
        assertTrue(menuBounds().height < OPTIONS.size * rowHeight, "The menu fitted, so this tested no scrolling")
        onNode(hasContentDescription("Last")).performScrollTo()
        waitForIdle()
        assertEquals(rowHeight, optionBounds("Last").height, "The last option wasn't fully shown")
        assertEquals(menuBounds().bottom, optionBounds("Last").bottom, "Space below the last option")
    }
}
