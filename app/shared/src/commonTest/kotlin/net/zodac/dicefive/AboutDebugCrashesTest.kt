package net.zodac.dicefive

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import net.zodac.dicefive.platform.LocalPlatformServices
import net.zodac.dicefive.platform.PlatformServices
import net.zodac.dicefive.platform.SilentPlatformServices
import net.zodac.dicefive.ui.settings.AboutDialog
import net.zodac.dicefive.ui.settings.DebugCrashes
import net.zodac.dicefive.ui.settings.DeliberateCrash
import net.zodac.dicefive.ui.theme.DiceFiveTheme

/**
 * A debug build's crash mode on Settings > About: five taps on the author's name turn it on, with a toast, and each link
 * then crashes the app its own way instead of opening. Any other build has none of it.
 */
class AboutDebugCrashesTest : UiTest() {

    private val opened = mutableListOf<String>()
    private val toasts = mutableListOf<String>()
    private val defaultBackgroundScope = DebugCrashes.backgroundScope

    @AfterTest
    fun restoreBackgroundScope() {
        DebugCrashes.backgroundScope = defaultBackgroundScope
    }

    private fun showDialog(debugCrashes: Boolean) {
        val uriHandler = object : UriHandler {
            override fun openUri(uri: String) {
                opened += uri
            }
        }
        val platform = object : PlatformServices by SilentPlatformServices {
            override fun showTransientMessage(message: String) {
                toasts += message
            }
        }
        compose.setContent {
            CompositionLocalProvider(LocalUriHandler provides uriHandler, LocalPlatformServices provides platform) {
                DiceFiveTheme { AboutDialog(onDismissRequest = {}, debugCrashes = debugCrashes) }
            }
        }
    }

    /** Taps the author's name in the Author section, [times] times. */
    private fun tapAuthorName(times: Int) {
        val body = compose.onNodeWithText("DiceFive is created by zodac.").performScrollTo()
        val layouts = mutableListOf<TextLayoutResult>()
        body.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        val name = layouts.single().getBoundingBox("DiceFive is created by z".length)
        // The text is drawn below its 4dp top padding, which the node's bounds include.
        val top = with(compose.density) { 4.dp.toPx() }
        repeat(times) { body.performTouchInput { click(Offset(name.center.x, name.center.y + top)) } }
        compose.waitForIdle()
    }

    @Test
    fun `five taps on the author's name turn the crash mode on, with a toast - and not before`() = uiTest {
        showDialog(debugCrashes = true)
        tapAuthorName(DebugCrashes.TAP_TARGET - 1)
        assertEquals(emptyList(), toasts)
        compose.onNodeWithText("Source code on GitHub").performScrollTo().performClick()
        assertEquals(listOf("https://github.com/zodac/DiceFive"), opened)

        tapAuthorName(1)
        assertEquals(listOf("Crash mode activated! Each link now crashes the app"), toasts)
    }

    @Test
    fun `then the author's link crashes on the main thread`() = uiTest {
        showDialog(debugCrashes = true)
        tapAuthorName(DebugCrashes.TAP_TARGET)
        val crash = assertFailsWith<DeliberateCrash> { compose.onNodeWithText("Source code on GitHub").performScrollTo().performClick() }
        assertEquals("Debug build's crash mode: A tap on the main thread", crash.message)
        assertEquals(emptyList(), opened)
    }

    @Test
    fun `the inspiration link crashes in a coroutine off the main thread`() = uiTest {
        val thrown = mutableListOf<Throwable>()
        val job = Job()
        DebugCrashes.backgroundScope = CoroutineScope(Dispatchers.Default + job + CoroutineExceptionHandler { _, error -> thrown += error })
        showDialog(debugCrashes = true)
        tapAuthorName(DebugCrashes.TAP_TARGET)
        compose.onNodeWithText("Dice Me Online on Google Play").performScrollTo().performClick()
        runBlocking { job.children.forEach { it.join() } }

        assertEquals(listOf("Debug build's crash mode: A coroutine off the main thread"), thrown.map { it.message })
        assertEquals(emptyList(), opened)
    }

    @Test
    fun `the privacy link crashes as the page is drawn`() = uiTest {
        showDialog(debugCrashes = true)
        tapAuthorName(DebugCrashes.TAP_TARGET)
        val crash = assertFailsWith<DeliberateCrash> {
            compose.onNodeWithText("Full privacy policy").performScrollTo().performClick()
            compose.waitForIdle()
        }
        assertEquals("Debug build's crash mode: The About page, as it was drawn", crash.message)
        assertEquals(emptyList(), opened)
    }

    @Test
    fun `any other build has no crash mode - the taps do nothing and the links open`() = uiTest {
        showDialog(debugCrashes = false)
        tapAuthorName(DebugCrashes.TAP_TARGET)
        assertTrue(toasts.isEmpty())
        compose.onNodeWithText("Full privacy policy").performScrollTo().performClick()
        assertEquals(listOf("https://zodac.github.io/DiceFive/privacy-policy"), opened)
    }
}
