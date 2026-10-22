package net.zodac.dicefive

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertTrue
import net.zodac.dicefive.ui.common.DiceFiveDialog
import net.zodac.dicefive.ui.theme.DiceFiveTheme

/** The screen this class's tests are drawn on. */
private val SCREEN = TestScreen(360, 800)

/** The two buttons of a confirm/cancel dialog never touch, whichever way the app is laid out. */
class DialogButtonGapTest : UiTest() {

    private val showcase = Showcase { compose }

    private fun gap(direction: LayoutDirection): Float {
        showcase.show {
            DiceFiveTheme {
                CompositionLocalProvider(LocalLayoutDirection provides direction) {
                    DiceFiveDialog(
                        icon = ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).build(), title = "Title", message = "Message",
                        confirmLabel = "Confirm", onConfirm = {}, onDismissRequest = {}, dismissLabel = "Cancel", onDismiss = {},
                    )
                }
            }
        }
        val confirm = compose.onNodeWithText("Confirm").fetchSemanticsNode().boundsInRoot
        val cancel = compose.onNodeWithText("Cancel").fetchSemanticsNode().boundsInRoot
        return if (confirm.left > cancel.left) confirm.left - cancel.right else cancel.left - confirm.right
    }

    @Test
    fun `the buttons are apart left to right and right to left`() = uiTest(SCREEN) {
        for (direction in LayoutDirection.entries) {
            val gap = gap(direction)
            assertTrue(gap >= 8f, "$direction gap $gap")
        }
    }
}
