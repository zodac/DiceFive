package net.zodac.dicefive

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.TestResult
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment

// Robolectric, on the SDK and graphics mode src/androidHostTest/resources/robolectric.properties sets.
@RunWith(AndroidJUnit4::class)
actual abstract class PlatformUiTest actual constructor()

// The screen is Robolectric's device configuration, set before the test's Activity starts.
internal actual fun runUiTest(screen: TestScreen?, block: suspend ComposeUiTest.() -> Unit): TestResult {
    if (screen != null) RuntimeEnvironment.setQualifiers(screen.qualifiers())
    return runComposeUiTest(block = block)
}

private fun TestScreen.qualifiers(): String {
    val orientation = if (widthDp > heightDp) "-land" else ""
    val dpi = when (density) {
        1f -> "mdpi"
        1.5f -> "hdpi"
        2f -> "xhdpi"
        3f -> "xxhdpi"
        4f -> "xxxhdpi"
        else -> error("No Android density bucket for $density")
    }
    return "w${widthDp}dp-h${heightDp}dp$orientation-$dpi"
}
