package net.zodac.dicefive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import kotlinx.coroutines.test.TestResult

/**
 * Each platform's test runner for the shared UI's tests: Robolectric on Android (a real Android window and text
 * rendering on the JVM), plain JUnit on the JVM target (Compose's desktop runtime).
 */
expect abstract class PlatformUiTest()

/**
 * The screen a [UiTest.uiTest] is drawn on: [widthDp] by [heightDp], at [density] pixels to a dp. Landscape when it's
 * wider than it is tall.
 */
data class TestScreen(val widthDp: Int, val heightDp: Int, val density: Float = 1f)

/** Runs [block] in a fresh composition on [screen], or on the platform's default test screen when it's null. */
internal expect fun runUiTest(screen: TestScreen?, block: suspend ComposeUiTest.() -> Unit): TestResult

/**
 * A test of the shared UI, run on every target. Each test is a [uiTest]; inside it, [compose] drives the composition
 * as the `compose` rule of an Android-only test would.
 */
abstract class UiTest : PlatformUiTest() {

    /** The running [uiTest]'s composition. Only there inside one. */
    lateinit var compose: ComposeUiTest
        private set

    fun uiTest(screen: TestScreen? = null, block: suspend ComposeUiTest.() -> Unit) = runUiTest(screen) {
        compose = this
        block()
    }
}

/**
 * One composition for a test that looks at several things in turn - a test of its own for each thing costs a whole
 * window and composition apiece. [show] swaps what is drawn and waits for it; each new thing is composed afresh, so
 * nothing remembered by the last one carries over. [compose] is asked for when first needed, so a test class can make
 * one before its [UiTest.uiTest] starts.
 */
internal class Showcase(private val compose: () -> ComposeUiTest) {
    private var content by mutableStateOf<(@Composable () -> Unit)?>(null)
    private var shown by mutableIntStateOf(0)

    fun show(next: @Composable () -> Unit) {
        val first = content == null
        content = next
        shown++
        if (first) compose().setContent { key(shown) { content?.invoke() } }
        compose().waitForIdle()
    }
}

/** Makes [id] (an IANA zone, "UTC") the time zone dates are shown in, returning the one it replaces to put back after. */
internal expect fun setTimeZone(id: String): String
