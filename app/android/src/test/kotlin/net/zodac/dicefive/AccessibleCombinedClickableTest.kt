package net.zodac.dicefive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.zodac.dicefive.ui.common.accessibleCombinedClickable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** One element's tap and long press, and what a screen reader is offered for each - word for word. */
@RunWith(AndroidJUnit4::class)
class AccessibleCombinedClickableTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()

    private fun node() = compose.onNodeWithTag("target")

    private fun config() = node().fetchSemanticsNode().config

    @Test
    fun `a tap and a long press do their own things - and a screen reader is offered both under their labels`() {
        compose.setContent {
            Box(
                Modifier.size(48.dp).testTag("target").accessibleCombinedClickable(
                    onClick = { calls += "tap" },
                    onClickLabel = "select",
                    onLongClick = { calls += "hold" },
                    onLongClickLabel = "choose a colour",
                    role = Role.Button,
                ),
            )
        }
        assertEquals("select", config()[SemanticsActions.OnClick].label)
        assertEquals("choose a colour", config()[SemanticsActions.OnLongClick].label)
        assertEquals(Role.Button, config()[SemanticsProperties.Role])

        node().performClick()
        node().performTouchInput { longClick() }
        assertEquals(listOf("tap", "hold"), calls)

        node().performSemanticsAction(SemanticsActions.OnClick)
        node().performSemanticsAction(SemanticsActions.OnLongClick)
        assertEquals(listOf("tap", "hold", "tap", "hold"), calls)
    }

    @Test
    fun `a screen reader's activate does its own thing when a tap does something else`() {
        compose.setContent {
            Box(
                Modifier.size(48.dp).testTag("target").accessibleCombinedClickable(
                    onClick = { calls += "tooltip" },
                    onClickLabel = "show details",
                    onLongClick = { calls += "dialog" },
                    onLongClickLabel = "show details",
                    role = Role.Button,
                    screenReaderClick = { calls += "dialog" },
                ),
            )
        }
        node().performClick()
        assertEquals(listOf("tooltip"), calls)
        node().performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(listOf("tooltip", "dialog"), calls)
        assertEquals("show details", config()[SemanticsActions.OnClick].label)
        assertEquals("show details", config()[SemanticsActions.OnLongClick].label)
    }

    @Test
    fun `cleared semantics speak for the element alone - what is inside it unheard`() {
        compose.setContent {
            Box(
                Modifier.size(48.dp).testTag("target").accessibleCombinedClickable(
                    onClick = { calls += "tap" },
                    onClickLabel = "select",
                    onLongClick = { calls += "hold" },
                    onLongClickLabel = "choose a colour",
                    role = Role.RadioButton,
                    clearSemantics = true,
                ) {
                    contentDescription = "Felt, 2 of 5"
                },
            ) { Text("badge") }
        }
        node().assertContentDescriptionEquals("Felt, 2 of 5")
        assertNull(config().getOrNull(SemanticsProperties.Text))
        assertEquals(Role.RadioButton, config()[SemanticsProperties.Role])
        assertEquals("select", config()[SemanticsActions.OnClick].label)
        assertEquals("choose a colour", config()[SemanticsActions.OnLongClick].label)

        node().performSemanticsAction(SemanticsActions.OnClick)
        node().performSemanticsAction(SemanticsActions.OnLongClick)
        assertEquals(listOf("tap", "hold"), calls)
    }

    @Test
    fun `cleared semantics offer no long press a label does not name - the tap still works for touch`() {
        compose.setContent {
            Box(
                Modifier.size(48.dp).testTag("target").accessibleCombinedClickable(
                    onClick = { calls += "tap" },
                    onClickLabel = "show unlock requirement",
                    onLongClick = { calls += "hold" },
                    clearSemantics = true,
                ) {
                    contentDescription = "Locked"
                },
            )
        }
        assertFalse(SemanticsActions.OnLongClick in config())
        node().performTouchInput { longClick() }
        assertTrue("hold" in calls)
    }
}
