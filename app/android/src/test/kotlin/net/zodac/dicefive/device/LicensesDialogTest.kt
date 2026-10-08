package net.zodac.dicefive.device

import android.app.Application
import android.content.ClipboardManager
import android.content.Intent
import android.os.Looper
import android.os.SystemClock
import android.text.Layout
import android.text.Selection
import android.text.Spannable
import android.text.Spanned
import android.text.style.URLSpan
import android.view.ContextMenu
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog as isComposeDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Duration
import net.zodac.dicefive.platform.LocalPlatformServices
import net.zodac.dicefive.platform.PlatformServices
import net.zodac.dicefive.platform.SilentPlatformServices
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
import net.zodac.dicefive.Showcase
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The Licences dialog's touch handling, on the real platform TextViews it renders the report with - one
 * per licence card -
 * run under Robolectric, as the sandbox has no device or emulator. Robolectric doesn't simulate the
 * platform's own long-press text selection, so that part (and its Copy / Share toolbar) is
 * device-only; everything the app itself adds is covered here.
 */
@RunWith(AndroidJUnit4::class)
class LicensesDialogTest {

    @get:Rule
    val compose = createComposeRule()

    private val application: Application = ApplicationProvider.getApplicationContext()

    /** The Apache-2.0 card - the first, and the one most of these tests work in. */
    private lateinit var document: TextView

    private val showcase = Showcase(compose)

    /** The dialog, in an app laid out in [direction] - right to left as a right-to-left translation would be. */
    private fun showDialog(direction: LayoutDirection = LayoutDirection.Ltr) {
        showcase.show {
            CompositionLocalProvider(LocalPlatformServices provides AndroidPlatformServices(application)) {
                DiceFiveTheme {
                    CompositionLocalProvider(LocalLayoutDirection provides direction) { LicensesDialog(onDismissRequest = {}) }
                }
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

    /** The URL linked from [linkText] in the document - read from the text, not hard-coded, as it
     * carries the library's version. */
    private fun linkUrl(linkText: String): String {
        val text = cardWith(linkText).text as Spanned
        val offset = text.indexOf(linkText)
        return text.getSpans(offset, offset, URLSpan::class.java).single().url
    }

    private fun clipboardText(): String? =
        application.getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.text?.toString()

    /** Lets the double-tap window pass, so the next press isn't taken as the second of a double tap. */
    private fun pause() = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ViewConfiguration.getDoubleTapTimeout() + 100L))

    @Test
    fun `the dialog is titled and introduced with a named close button - a card a licence - its list scrolling under the title with the dialog's own scrollbar`() {
        showDialog()
        compose.onNodeWithContentDescription("Close licences", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Licences").assertExists()
        compose.onNodeWithText("DiceFive is built with the open-source software, fonts and sounds below, each used under the licence it's listed with.").assertExists()

        // Each licence is its own card, so a selection can't run on into the next: each card opens with its own heading,
        // and no licence is split across two.
        val cards = cards()
        assertTrue("Expected a card per licence, got ${cards.size}", cards.size > 1)
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

        // Scrolling the list never draws over the title above it: everything above the list - the close button, title and
        // description - is untouched.
        val scroll = document.parent.parent as ScrollView
        val listTop = IntArray(2).also(scroll::getLocationInWindow)[1]
        val before = compose.onNode(isComposeDialog()).captureToImage().asAndroidBitmap()
        compose.runOnUiThread { scroll.scrollTo(0, 1500) }
        compose.waitForIdle()
        val after = compose.onNode(isComposeDialog()).captureToImage().asAndroidBitmap()
        for (y in 0 until listTop) {
            for (x in 0 until before.width) {
                assertEquals("Pixel ($x, $y) changed", before.getPixel(x, y), after.getPixel(x, y))
            }
        }

        // The list's own scrollbar is off, and the dialog's follows and drives the list.
        val licenceScroll = LicenceScroll()
        showcase.show {
            CompositionLocalProvider(LocalPlatformServices provides AndroidPlatformServices(application)) {
                DiceFiveTheme {
                    // Long enough to scroll in 400dp.
                    val report = remember {
                        val components = (1..80).map { LicensedComponent("Library $it", "1.0", ComponentKind.LIBRARY, website = null, copyright = null) }
                        LicenseReport(listOf(LicenseGroup("Apache License 2.0", "Licence text", components)), notices = emptyList())
                    }
                    TextViewLicenceDocument(report = report, scroll = licenceScroll, modifier = Modifier.height(400.dp))
                }
            }
        }
        compose.waitUntil(timeoutMillis = 10_000) { licenceScroll.maxPosition > 0 }
        val scrollView = findScrollView()
        assertFalse(scrollView.isVerticalScrollBarEnabled)
        assertEquals(0, licenceScroll.position)
        compose.runOnUiThread { scrollView.scrollTo(0, 300) }
        compose.waitForIdle()
        assertEquals(300, licenceScroll.position)
        // A drag on the dialog's bar arrives as fractions of a pixel, and none of them may be lost.
        compose.runOnUiThread { repeat(4) { licenceScroll.scrollBy(0.5f) } }
        compose.waitForIdle()
        assertEquals(302, scrollView.scrollY)
        assertEquals(302, licenceScroll.position)
    }

    @Test
    fun `a card's text is toggled - a link tapped open or long-pressed for its menu - and a selection spans rows until a tap clears it`() {
        showDialog()

        // A licence's text is shown and hidden by tapping its toggle - a second tap inside the double-tap window would be
        // the TextView's own double-tap (select a word).
        assertFalse(document.text.contains(APACHE_TEXT))
        press("Show licence text", card = document)
        assertTrue(document.text.contains(APACHE_TEXT))
        pause()
        press("Hide licence text", card = document)
        assertFalse(document.text.contains(APACHE_TEXT))

        // Tapping a link opens it.
        press(PROTOBUF)
        val opened = shadowOf(application).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, opened?.action)
        assertTrue(opened?.data.toString().startsWith("https://"))

        // Long-pressing a link offers Copy link and Copy text, instead of selecting or opening it.
        pause()
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

        // Long-pressing plain text doesn't show the link menu: the platform always asks for a context menu on a long press,
        // but an empty one isn't shown, and the press falls through to the TextView's own selection.
        var plainMenu: ContextMenu? = null
        compose.runOnUiThread { document.setOnCreateContextMenuListener { built, _, _ -> plainMenu = built } }
        press("Used by", holdMillis = ViewConfiguration.getLongPressTimeout() + 200L, card = document)
        assertEquals(0, plainMenu?.size() ?: 0)

        // A selection leaves a card waiting on it, so the rest start from a fresh dialog. One selection can span a card's
        // rows - and tapping elsewhere clears it.
        showDialog()
        select(from = "Apache License 2.0", to = COMPOSE_UI)
        val selected = document.text.subSequence(document.selectionStart, document.selectionEnd).toString()
        assertTrue(selected.startsWith("Apache License 2.0"))
        assertTrue(selected.contains("Compose Material3 Components"))
        assertTrue(selected.endsWith(COMPOSE_UI))
        compose.onNodeWithText("Licences").performClick()
        compose.waitForIdle()
        assertFalse(document.hasSelection())

        // Tapping a link while text is selected only clears the selection.
        val selectedCard = cardWith(PROTOBUF)
        select(from = PROTOBUF, to = PROTOBUF, card = selectedCard)
        press(PROTOBUF)
        assertFalse(selectedCard.hasSelection())
        assertNull(shadowOf(application).nextStartedActivity)
    }

    // The licences are English whatever the app's language: on a right-to-left phone, in a right-to-left app, they stay
    // left to right - not mirrored to the right, nor set as right-to-left paragraphs.
    @Test
    @Config(qualifiers = "ar")
    fun `in a right-to-left app the licences stay left to right - the TextView card and the Compose list alike`() {
        showDialog(LayoutDirection.Rtl)
        assertEquals(View.LAYOUT_DIRECTION_LTR, document.layoutDirection)
        val layout = document.layout
        assertEquals(Layout.DIR_LEFT_TO_RIGHT, layout.getParagraphDirection(0))
        assertEquals(0f, layout.getLineLeft(0), 0.5f)

        // The list iOS and previews draw, given Android's real reports.
        val platform = object : PlatformServices by SilentPlatformServices {
            override suspend fun loadLicenceReports() = AndroidPlatformServices(application).loadLicenceReports()
        }
        showcase.show {
            CompositionLocalProvider(LocalPlatformServices provides platform) {
                DiceFiveTheme {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { LicensesDialog(onDismissRequest = {}) }
                }
            }
        }
        val title = hasText("Apache License 2.0")
        compose.waitUntil(timeoutMillis = 10_000) { compose.onAllNodes(title, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onAllNodes(title, useUnmergedTree = true)[0].fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        assertEquals(ResolvedTextDirection.Ltr, layouts.single().getParagraphDirection(0))
        assertEquals(0f, layouts.single().getLineLeft(0), 0.5f)
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
