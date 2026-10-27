package net.zodac.dicefive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.AccessibilityManager
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.test.hasContentDescription
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.zodac.dicefive.app.AchievementEvent
import net.zodac.dicefive.app.AchievementEvents
import net.zodac.dicefive.app.LocalAppContainer
import net.zodac.dicefive.app.StylesEarnedBy
import net.zodac.dicefive.ui.achievements.AchievementBannerHost
import net.zodac.dicefive.ui.common.LocalReduceMotion
import net.zodac.dicefive.ui.style.DiceStyles
import net.zodac.dicefive.ui.theme.DiceFiveTheme

/**
 * A banner stays up as long as the player's "Time to take action" accessibility setting asks, when that's longer than
 * its own hold, and is asked for as text and icons, not controls.
 *
 * Android only: on the desktop test harness a composition's coroutines wait in real time, not on the test clock, so
 * the banner's hold never ends there.
 */
class BannerHoldTimeoutTest : UiTest() {

    /** Stands in for the platform's: the setting at [TIME_TO_TAKE_ACTION_MS], and what each banner asked for. */
    private class TimeToTakeAction : AccessibilityManager {
        val asked = mutableListOf<List<Boolean>>()

        override fun calculateRecommendedTimeoutMillis(
            originalTimeoutMillis: Long,
            containsIcons: Boolean,
            containsText: Boolean,
            containsControls: Boolean,
        ): Long {
            asked += listOf(containsIcons, containsText, containsControls)
            return maxOf(originalTimeoutMillis, TIME_TO_TAKE_ACTION_MS)
        }
    }

    private fun exists(name: String) =
        compose.onAllNodes(hasContentDescription(": $name dice.", substring = true), useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `a banner stays for the time to take action the player has asked for, then goes`() = uiTest {
        val accessibility = TimeToTakeAction()
        compose.setContent {
            CompositionLocalProvider(
                LocalAppContainer provides inMemoryAppContainer(),
                LocalAccessibilityManager provides accessibility,
                // No fades, so the banner is gone the moment its time is up.
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

        AchievementEvents.emit(AchievementEvent.StylesUnlocked(listOf(DiceStyles.unlockedStyle("Frosted")), StylesEarnedBy.Count(30)))
        compose.mainClock.advanceTimeBy(FRAMES_TO_SHOW)
        compose.waitForIdle()
        assertTrue(exists("Frosted"), "the banner never showed")
        assertEquals(listOf(true, true, false), accessibility.asked.first(), "icons, text, no controls")

        // Well past its own 4s hold.
        compose.mainClock.advanceTimeBy(HOLD_MILLIS * 2)
        assertTrue(exists("Frosted"), "the banner went at its own hold, not the player's")

        compose.mainClock.advanceTimeBy(TIME_TO_TAKE_ACTION_MS - HOLD_MILLIS * 2 + FRAMES_TO_SHOW)
        assertTrue(!exists("Frosted"), "the banner was still up after the player's time")
    }

    private companion object {
        /** A few frames: for a change to be composed and a snap to land. */
        const val FRAMES_TO_SHOW = 64L

        /** AchievementBannerHost's HOLD_MILLIS. */
        const val HOLD_MILLIS = 4_000L

        /** The player's "Time to take action" setting: one of the platform's own choices. */
        const val TIME_TO_TAKE_ACTION_MS = 30_000L
    }
}
