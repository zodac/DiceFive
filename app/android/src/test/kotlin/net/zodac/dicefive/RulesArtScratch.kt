package net.zodac.dicefive

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import net.zodac.dicefive.platform.LocalPlatformServices
import net.zodac.dicefive.platform.SilentPlatformServices
import net.zodac.dicefive.ui.rules.ExampleHighlight
import net.zodac.dicefive.ui.rules.RulesScreen
import net.zodac.dicefive.ui.rules.exampleHighlight
import net.zodac.dicefive.ui.theme.DiceFiveTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h1100dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RulesArtScratch {
    @get:Rule val compose = createComposeRule()

    private fun render(variant: ExampleHighlight) {
        exampleHighlight = variant
        compose.setContent {
            CompositionLocalProvider(LocalPlatformServices provides SilentPlatformServices) {
                DiceFiveTheme { RulesScreen(onBack = {}) }
            }
        }
        for (tab in listOf("Upper Section", "Lower Section", "5x & Joker", "Tricolour")) {
            compose.onNodeWithText(tab).performClick(); compose.mainClock.advanceTimeBy(3000); compose.waitForIdle(); compose.onNodeWithText(tab).performClick(); compose.mainClock.advanceTimeBy(3000)
            compose.waitForIdle()
            val bmp = compose.onRoot().captureToImage().asAndroidBitmap()
            File("/tmp/claude-0/-home-user-DiceFive/59c6399d-0ab6-5321-b5c7-6abe74efb024/scratchpad/rules_${variant.name.lowercase()}_${tab.replace(" ", "_").replace("&", "and")}.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test fun faded() = render(ExampleHighlight.FADE_OTHERS)
    @Test fun underlined() = render(ExampleHighlight.UNDERLINE_COUNTED)
}
