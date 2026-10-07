package net.zodac.dicefive

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.zodac.dicefive.ui.common.DiceFiveDialog
import net.zodac.dicefive.ui.theme.DiceFiveTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The two buttons of a confirm/cancel dialog never touch, whichever way the app is laid out. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp")
class DialogButtonGapTest {

    @get:Rule
    val compose = createComposeRule()

    private fun gap(direction: LayoutDirection): Float {
        compose.setContent {
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
    fun `buttons are apart left to right`() {
        val gap = gap(LayoutDirection.Ltr)
        assertTrue("gap $gap", gap >= 8f)
    }

    @Test
    fun `buttons are apart right to left`() {
        val gap = gap(LayoutDirection.Rtl)
        assertTrue("gap $gap", gap >= 8f)
    }
}
