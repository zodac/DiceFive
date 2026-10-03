package net.zodac.dicefive

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.zodac.dicefive.app.LocalAppContainer
import net.zodac.dicefive.data.achievements.AchievementEvent
import net.zodac.dicefive.data.achievements.AchievementEvents
import net.zodac.dicefive.data.achievements.UnlockedStyle
import net.zodac.dicefive.device.AndroidAppContainer
import net.zodac.dicefive.ui.achievements.AchievementBannerHost
import net.zodac.dicefive.ui.common.LocalReduceMotion
import net.zodac.dicefive.ui.theme.DiceFiveTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Under reduced motion the achievement banners don't move: a burst is all there at once rather than dealt out one by
 * one, a banner doesn't fade in, and it's gone the moment its time is up rather than fading out.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BannerReducedMotionTest {

    @get:Rule
    val compose = createComposeRule()

    private fun announcement(name: String) = hasContentDescription(": $name dice.", substring = true)

    private fun banner(name: String) = compose.onNode(announcement(name), useUnmergedTree = true)

    private fun exists(name: String) = compose.onAllNodes(announcement(name), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun shot(name: String): Bitmap = banner(name).captureToImage().asAndroidBitmap()

    private fun samePixels(a: Bitmap, b: Bitmap): Boolean =
        a.width == b.width && a.height == b.height && (0 until a.width step 2).all { x -> (0 until a.height step 2).all { y -> a.getPixel(x, y) == b.getPixel(x, y) } }

    @Test
    fun `a burst of banners is there at once, at full strength, and each is gone the moment its time is up`() {
        compose.setContent {
            CompositionLocalProvider(
                LocalAppContainer provides AndroidAppContainer.get(ApplicationProvider.getApplicationContext()),
                LocalReduceMotion provides true,
            ) {
                DiceFiveTheme {
                    AchievementBannerHost(
                        isOnGameScreen = { false },
                        onAchievementSelected = {},
                        onStylesSelected = {},
                    ) { Box(Modifier.fillMaxSize()) }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false

        val names = listOf("Frosted", "Velvet", "Oak")
        names.forEach { AchievementEvents.emit(AchievementEvent.StylesUnlocked(listOf(UnlockedStyle(it, "dice")), 30)) }
        compose.mainClock.advanceTimeBy(FRAMES_TO_SHOW)
        // Idle with the clock still paused: lets the banners' dialog window come up, without any time passing.
        compose.waitForIdle()
        // All of them, not one every STAGGER_MILLIS.
        names.forEach { assertTrue("$it isn't showing yet", exists(it)) }

        // The first to arrive is the front banner. Already as it'll stay: no fade-in still under way.
        val front = names.first()
        val atOnce = shot(front)
        compose.mainClock.advanceTimeBy(1_000)
        assertTrue("$front was still fading in", samePixels(atOnce, shot(front)))

        // Gone as soon as its 4s hold is up, not still there fading out.
        compose.mainClock.advanceTimeBy(HOLD_MILLIS - 1_000 + FRAMES_TO_SHOW)
        assertTrue("$front was still on screen after its time was up", !exists(front))
        assertTrue("${names[1]} was gone too soon", exists(names[1]))
    }

    private companion object {
        /** A few frames: for a change to be composed and a snap to land. */
        const val FRAMES_TO_SHOW = 64L

        /** AchievementBannerHost's HOLD_MILLIS: how long the front banner stays before it goes. */
        const val HOLD_MILLIS = 4_000L
    }
}
