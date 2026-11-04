package net.zodac.dicefive

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.zodac.dicefive.ui.common.DiceFiveDialog
import net.zodac.dicefive.ui.icons.Symbols
import net.zodac.dicefive.ui.theme.DiceFiveTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * A dialog too long for the screen shows the app's scrollbar in its side padding, on the end edge, and one that fits
 * shows none; either way its text keeps the same width. The bar is silent to TalkBack, so it's looked for in the pixels.
 *
 * Here rather than in app/shared because right to left needs this app's manifest (`supportsRtl`) to reach a dialog's window.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp-xxhdpi")
class DialogScrollbarTest {

    @get:Rule
    val compose = createComposeRule()

    private var thumb = Color.Unspecified

    private fun show(message: String, direction: LayoutDirection = LayoutDirection.Ltr) {
        compose.setContent {
            DiceFiveTheme {
                thumb = MaterialTheme.colorScheme.primary
                CompositionLocalProvider(LocalLayoutDirection provides direction) {
                    DiceFiveDialog(icon = Symbols.Lock, title = "Title", message = message, confirmLabel = "Confirm", onConfirm = {}, onDismissRequest = {})
                }
            }
        }
        compose.waitForIdle()
    }

    private fun messageWidth(start: String): Float = compose.onNodeWithText(start, substring = true).fetchSemanticsNode().boundsInRoot.width

    /** Thumb-coloured pixels in the dialog's side padding on that side. */
    private fun thumbPixels(onRight: Boolean): Int {
        val image = compose.onNode(isDialog()).captureToImage().toPixelMap()
        val padding = with(compose.density) { 24.dp.roundToPx() }
        val columns = if (onRight) image.width - padding until image.width else 0 until padding
        return columns.sumOf { x -> (0 until image.height).count { y -> image[x, y] == thumb } }
    }

    @Test
    fun `a dialog that fits has no bar`() {
        show(SHORT)
        assertEquals(0, thumbPixels(onRight = true) + thumbPixels(onRight = false))
    }

    @Test
    fun `a dialog that doesn't fit has its bar on the right - and its text as wide as one that fits`() {
        show(LONG)
        assertTrue(thumbPixels(onRight = true) > 0)
        assertEquals(0, thumbPixels(onRight = false))
        // The dialog's width less its 24dp padding each side: the bar takes none of the text's room.
        assertEquals(with(compose.density) { (DIALOG_WIDTH - 48.dp).toPx() }, messageWidth("Every word"), 1f)
    }

    @Test
    fun `right to left the bar is on the left`() {
        show(LONG, LayoutDirection.Rtl)
        assertTrue(thumbPixels(onRight = false) > 0)
        assertEquals(0, thumbPixels(onRight = true))
    }

    private companion object {
        const val SHORT = "This fits."
        val DIALOG_WIDTH = 360.dp
        val LONG = "Every word of this message is here to make it far too long for the screen. ".repeat(40)
    }
}
