package net.zodac.dicefive.device

import android.app.Application
import android.content.ClipboardManager
import android.content.Intent
import android.os.Looper
import android.os.SystemClock
import android.text.Selection
import android.text.Spannable
import android.text.Spanned
import android.text.style.URLSpan
import android.view.ContextMenu
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.ScrollView
import android.widget.TextView
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isDialog as isComposeDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Duration
import net.zodac.dicefive.platform.LocalPlatformServices
import net.zodac.dicefive.ui.settings.LicensesDialog
import net.zodac.dicefive.ui.theme.DiceFiveTheme
import org.hamcrest.Matchers.containsString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Licences dialog's touch handling, on the real platform TextView it renders the report with -
 * run under Robolectric, as the sandbox has no device or emulator. Robolectric doesn't simulate the
 * platform's own long-press text selection, so that part (and its Copy / Share toolbar) is
 * device-only; everything the app itself adds is covered here.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
// Real (native) drawing, so vector icons can rasterise - the default legacy mode has no bitmaps.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LicensesDialogTest {

    @get:Rule
    val compose = createComposeRule()

    private val application: Application = ApplicationProvider.getApplicationContext()

    private lateinit var document: TextView

    private fun showDialog() {
        compose.setContent {
            CompositionLocalProvider(LocalPlatformServices provides AndroidPlatformServices(application)) {
                DiceFiveTheme { LicensesDialog(onDismissRequest = {}) }
            }
        }
        compose.waitUntil(timeoutMillis = 10_000) { findDocument() != null }
        document = findDocument()!!
    }

    private fun findDocument(): TextView? {
        var found: TextView? = null
        runCatching {
            onView(withText(containsString("Apache License 2.0"))).inRoot(isDialog()).check { view, _ -> found = view as? TextView }
        }
        return found
    }

    /** Presses [document] at the middle of the first character of [target] - held for [holdMillis]. */
    private fun press(target: String, holdMillis: Long = 50) {
        val offset = document.text.indexOf(target)
        check(offset >= 0) { "\"$target\" isn't in the document" }
        val layout = document.layout
        val line = layout.getLineForOffset(offset)
        val x = layout.getPrimaryHorizontal(offset) + 2f + document.totalPaddingLeft
        val y = (layout.getLineTop(line) + layout.getLineBottom(line)) / 2f + document.totalPaddingTop
        val downTime = SystemClock.uptimeMillis()
        compose.runOnUiThread {
            document.dispatchTouchEvent(MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0))
        }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(holdMillis))
        compose.runOnUiThread {
            document.dispatchTouchEvent(MotionEvent.obtain(downTime, downTime + holdMillis, MotionEvent.ACTION_UP, x, y, 0))
        }
        compose.waitForIdle()
    }

    private fun select(from: String, to: String) {
        compose.runOnUiThread {
            document.requestFocus()
            val text = document.text
            Selection.setSelection(text as Spannable, text.indexOf(from), text.indexOf(to) + to.length)
        }
    }

    @Test
    fun `tapping a link opens it`() {
        showDialog()

        press(PROTOBUF)

        val opened = shadowOf(application).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, opened?.action)
        assertTrue(opened?.data.toString().startsWith("https://"))
    }

    @Test
    fun `long-pressing a link offers Copy link and Copy text, instead of selecting or opening it`() {
        showDialog()
        var menu: ContextMenu? = null
        compose.runOnUiThread { document.setOnCreateContextMenuListener { built, _, _ -> menu = built } }

        press(PROTOBUF, holdMillis = ViewConfiguration.getLongPressTimeout() + 200L)

        assertFalse(document.hasSelection())
        assertNull(shadowOf(application).nextStartedActivity)
        val shown = checkNotNull(menu) { "No context menu was shown" }
        assertEquals(listOf("Copy link", "Copy text"), (0 until shown.size()).map { shown.getItem(it).title.toString() })

        compose.runOnUiThread { shown.performIdentifierAction(LinkTextView.COPY_LINK, 0) }
        assertEquals(linkUrl(PROTOBUF), clipboardText())

        compose.runOnUiThread { shown.performIdentifierAction(LinkTextView.COPY_TEXT, 0) }
        assertEquals(PROTOBUF, clipboardText())
    }

    @Test
    fun `long-pressing plain text doesn't show the link menu`() {
        showDialog()
        var menu: ContextMenu? = null
        compose.runOnUiThread { document.setOnCreateContextMenuListener { built, _, _ -> menu = built } }

        press("Used by", holdMillis = ViewConfiguration.getLongPressTimeout() + 200L)

        // The platform always asks for a context menu on a long press; an empty one isn't shown, and
        // the press falls through to the TextView's own selection.
        assertEquals(0, menu?.size() ?: 0)
    }

    @Test
    fun `a licence's text is shown and hidden by tapping its toggle`() {
        showDialog()
        assertFalse(document.text.contains(APACHE_TEXT))

        press("Show licence text")
        assertTrue(document.text.contains(APACHE_TEXT))

        // A second tap inside the double-tap window is the TextView's own double-tap (select a word).
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ViewConfiguration.getDoubleTapTimeout() + 100L))
        press("Hide licence text")
        assertFalse(document.text.contains(APACHE_TEXT))
    }

    @Test
    fun `one selection can span rows and licences`() {
        showDialog()

        select(from = "Apache License 2.0", to = PROTOBUF)

        val selected = document.text.subSequence(document.selectionStart, document.selectionEnd).toString()
        assertTrue(selected.startsWith("Apache License 2.0"))
        assertTrue(selected.contains("Compose Material3 Components"))
        assertTrue(selected.endsWith(PROTOBUF))
    }

    @Test
    fun `tapping elsewhere clears a selection`() {
        showDialog()
        select(from = "Apache License 2.0", to = PROTOBUF)
        assertTrue(document.hasSelection())

        compose.onNodeWithText("Licences").performClick()
        compose.waitForIdle()

        assertFalse(document.hasSelection())
    }

    @Test
    fun `tapping a link while text is selected only clears the selection`() {
        showDialog()
        select(from = "Apache License 2.0", to = "Apache License 2.0")

        press(PROTOBUF)

        assertFalse(document.hasSelection())
        assertNull(shadowOf(application).nextStartedActivity)
    }

    /** The URL linked from [linkText] in the document - read from the text, not hard-coded, as it
     * carries the library's version. */
    private fun linkUrl(linkText: String): String {
        val text = document.text as Spanned
        val offset = text.indexOf(linkText)
        return text.getSpans(offset, offset, URLSpan::class.java).single().url
    }

    private fun clipboardText(): String? =
        application.getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.text?.toString()

    @Test
    fun `scrolling the list never draws over the title above it`() {
        showDialog()
        val scroll = document.parent as ScrollView
        val listTop = IntArray(2).also(scroll::getLocationInWindow)[1]
        val before = compose.onNode(isComposeDialog()).captureToImage().asAndroidBitmap()

        compose.runOnUiThread { scroll.scrollTo(0, 1500) }
        compose.waitForIdle()
        val after = compose.onNode(isComposeDialog()).captureToImage().asAndroidBitmap()

        // Everything above the list - the close button, title and description - is untouched.
        for (y in 0 until listTop) {
            for (x in 0 until before.width) {
                assertEquals("Pixel ($x, $y) changed", before.getPixel(x, y), after.getPixel(x, y))
            }
        }
    }

    private companion object {
        const val PROTOBUF = "Protocol Buffers (bundled in DataStore)"
        const val APACHE_TEXT = "TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION"
    }
}
