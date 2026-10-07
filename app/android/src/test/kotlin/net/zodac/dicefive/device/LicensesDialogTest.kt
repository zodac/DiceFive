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
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isDialog as isComposeDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Duration
import net.zodac.dicefive.platform.LocalPlatformServices
import net.zodac.dicefive.ui.settings.ComponentKind
import net.zodac.dicefive.ui.settings.LicenceScroll
import net.zodac.dicefive.ui.settings.LicenseGroup
import net.zodac.dicefive.ui.settings.LicenseReport
import net.zodac.dicefive.ui.settings.LicensedComponent
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
 * The Licences dialog's touch handling, on the real platform TextViews it renders the report with - one
 * per licence card -
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

    /** The Apache-2.0 card - the first, and the one most of these tests work in. */
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

    @Test
    fun `the dialog has a title, an introduction and a named close button`() {
        showDialog()

        compose.onNodeWithContentDescription("Close licences", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Licences").assertExists()
        compose.onNodeWithText("DiceFive is built with the open-source software, fonts and sounds below, each used under the licence it's listed with.").assertExists()
    }

    /** Every card, top to bottom - the TextViews in the column the scroll view holds. */
    private fun cards(): List<TextView> {
        val column = document.parent as ViewGroup
        return (0 until column.childCount).map { column.getChildAt(it) as TextView }
    }

    /** The one card whose text contains [text]. */
    private fun cardWith(text: String): TextView = cards().single { it.text.contains(text) }

    /** Presses [card] (by default, the one card holding [target]) at the middle of [target]'s first
     * character - held for [holdMillis]. */
    private fun press(target: String, holdMillis: Long = 50, card: TextView = cardWith(target)) {
        val offset = card.text.indexOf(target)
        val layout = card.layout
        val line = layout.getLineForOffset(offset)
        val x = layout.getPrimaryHorizontal(offset) + 2f + card.totalPaddingLeft
        val y = (layout.getLineTop(line) + layout.getLineBottom(line)) / 2f + card.totalPaddingTop
        val downTime = SystemClock.uptimeMillis()
        compose.runOnUiThread {
            card.dispatchTouchEvent(MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0))
        }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(holdMillis))
        compose.runOnUiThread {
            card.dispatchTouchEvent(MotionEvent.obtain(downTime, downTime + holdMillis, MotionEvent.ACTION_UP, x, y, 0))
        }
        compose.waitForIdle()
    }

    /** Selects from [from] to [to] in [card]. */
    private fun select(from: String, to: String, card: TextView = document) {
        compose.runOnUiThread {
            card.requestFocus()
            val text = card.text
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
        val card = cardWith(PROTOBUF)
        var menu: ContextMenu? = null
        compose.runOnUiThread { card.setOnCreateContextMenuListener { built, _, _ -> menu = built } }

        press(PROTOBUF, holdMillis = ViewConfiguration.getLongPressTimeout() + 200L)

        assertFalse(card.hasSelection())
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

        press("Used by", holdMillis = ViewConfiguration.getLongPressTimeout() + 200L, card = document)

        // The platform always asks for a context menu on a long press; an empty one isn't shown, and
        // the press falls through to the TextView's own selection.
        assertEquals(0, menu?.size() ?: 0)
    }

    @Test
    fun `a licence's text is shown and hidden by tapping its toggle`() {
        showDialog()
        assertFalse(document.text.contains(APACHE_TEXT))

        press("Show licence text", card = document)
        assertTrue(document.text.contains(APACHE_TEXT))

        // A second tap inside the double-tap window is the TextView's own double-tap (select a word).
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ViewConfiguration.getDoubleTapTimeout() + 100L))
        press("Hide licence text", card = document)
        assertFalse(document.text.contains(APACHE_TEXT))
    }

    @Test
    fun `one selection can span a card's rows`() {
        showDialog()

        select(from = "Apache License 2.0", to = COMPOSE_UI)

        val selected = document.text.subSequence(document.selectionStart, document.selectionEnd).toString()
        assertTrue(selected.startsWith("Apache License 2.0"))
        assertTrue(selected.contains("Compose Material3 Components"))
        assertTrue(selected.endsWith(COMPOSE_UI))
    }

    @Test
    fun `each licence is its own card, so a selection can't run on into the next`() {
        showDialog()

        val cards = cards()
        assertTrue("Expected a card per licence, got ${cards.size}", cards.size > 1)
        // Each card opens with its own heading - no licence is split across two.
        val headings = cards.map { it.text.lines().first() }
        assertEquals(headings.distinct(), headings)
        assertFalse(document.text.contains(PROTOBUF))

        compose.runOnUiThread {
            document.requestFocus()
            Selection.selectAll(document.text as Spannable)
        }
        val selected = document.text.subSequence(document.selectionStart, document.selectionEnd).toString()
        assertTrue(selected.startsWith("Apache License 2.0"))
        assertFalse(selected.contains(PROTOBUF))
    }

    @Test
    fun `tapping elsewhere clears a selection`() {
        showDialog()
        select(from = "Apache License 2.0", to = COMPOSE_UI)
        assertTrue(document.hasSelection())

        compose.onNodeWithText("Licences").performClick()
        compose.waitForIdle()

        assertFalse(document.hasSelection())
    }

    @Test
    fun `tapping a link while text is selected only clears the selection`() {
        showDialog()
        val card = cardWith(PROTOBUF)
        select(from = PROTOBUF, to = PROTOBUF, card = card)

        press(PROTOBUF)

        assertFalse(card.hasSelection())
        assertNull(shadowOf(application).nextStartedActivity)
    }

    /** The URL linked from [linkText] in the document - read from the text, not hard-coded, as it
     * carries the library's version. */
    private fun linkUrl(linkText: String): String {
        val text = cardWith(linkText).text as Spanned
        val offset = text.indexOf(linkText)
        return text.getSpans(offset, offset, URLSpan::class.java).single().url
    }

    private fun clipboardText(): String? =
        application.getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.text?.toString()

    @Test
    fun `scrolling the list never draws over the title above it`() {
        showDialog()
        val scroll = document.parent.parent as ScrollView
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

    @Test
    fun `the list's own scrollbar is off, and the dialog's follows and drives the list`() {
        val scroll = LicenceScroll()
        compose.setContent {
            CompositionLocalProvider(LocalPlatformServices provides AndroidPlatformServices(application)) {
                DiceFiveTheme {
                    // Long enough to scroll in 400dp.
                    val report = remember {
                        val components = (1..80).map { LicensedComponent("Library $it", "1.0", ComponentKind.LIBRARY, website = null, copyright = null) }
                        LicenseReport(listOf(LicenseGroup("Apache License 2.0", "Licence text", components)), notices = emptyList())
                    }
                    TextViewLicenceDocument(report = report, scroll = scroll, modifier = Modifier.height(400.dp))
                }
            }
        }
        compose.waitUntil(timeoutMillis = 10_000) { scroll.maxPosition > 0 }
        val scrollView = findScrollView()
        assertFalse(scrollView.isVerticalScrollBarEnabled)
        assertEquals(0, scroll.position)

        compose.runOnUiThread { scrollView.scrollTo(0, 300) }
        compose.waitForIdle()
        assertEquals(300, scroll.position)

        // A drag on the dialog's bar arrives as fractions of a pixel, and none of them may be lost.
        compose.runOnUiThread { repeat(4) { scroll.scrollBy(0.5f) } }
        compose.waitForIdle()
        assertEquals(302, scrollView.scrollY)
        assertEquals(302, scroll.position)
    }

    private fun findScrollView(): ScrollView {
        var found: ScrollView? = null
        onView(withText(containsString("Apache License 2.0"))).check { view, _ -> found = view.parent.parent as ScrollView }
        return checkNotNull(found)
    }

    private companion object {
        const val PROTOBUF = "Protocol Buffers (bundled in DataStore)"
        const val COMPOSE_UI = "Compose UI Text"
        const val APACHE_TEXT = "TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION"
    }
}
