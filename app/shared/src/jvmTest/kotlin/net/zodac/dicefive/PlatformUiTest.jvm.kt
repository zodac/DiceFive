package net.zodac.dicefive

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
actual abstract class PlatformUiTest actual constructor() {

    /**
     * The language a test sees unless it asks for another (Robolectric's default device, en-US, as on Android), and a
     * Main dispatcher - the JVM has none of its own - whose scheduler the test's composition then shares: a view model's
     * coroutines run as the test clock advances, as they do on Android's main looper.
     */
    @BeforeTest
    fun setUpPlatform() {
        Locale.setDefault(Locale.US)
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDownPlatform() {
        Dispatchers.resetMain()
    }
}

// The screen is the test window's size, in pixels, and its density. With none asked for, Robolectric's default device,
// so a test sees the same screen on both targets.
internal actual fun runUiTest(screen: TestScreen?, block: suspend ComposeUiTest.() -> Unit): TestResult {
    val (widthDp, heightDp, density) = screen ?: ROBOLECTRIC_DEFAULT_SCREEN
    return runSkikoComposeUiTest(size = Size(widthDp * density, heightDp * density), density = Density(density), block = block)
}

/** Robolectric's own default device: w320dp-h470dp at mdpi. */
private val ROBOLECTRIC_DEFAULT_SCREEN = TestScreen(320, 470)
