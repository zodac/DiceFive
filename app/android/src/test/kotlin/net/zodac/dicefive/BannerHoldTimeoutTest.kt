package net.zodac.dicefive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.AccessibilityManager
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.zodac.dicefive.app.LocalAppContainer
import net.zodac.dicefive.device.AndroidAppContainer
import net.zodac.dicefive.ui.achievements.AchievementBannerHost
import net.zodac.dicefive.ui.achievements.AchievementEvent
import net.zodac.dicefive.ui.achievements.AchievementEvents
import net.zodac.dicefive.ui.achievements.StylesEarnedBy
import net.zodac.dicefive.ui.common.LocalReduceMotion
import net.zodac.dicefive.ui.style.DiceStyles
import net.zodac.dicefive.ui.theme.DiceFiveTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A banner stays up as long as the player's "Time to take action" accessibility setting asks, when that's longer than
 * its own hold, and is asked for as text and icons, not controls.
 */
@RunWith(AndroidJUnit4::class)
class BannerHoldTimeoutTest {

    @get:Rule
    val compose = createComposeRule()

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
    fun `a banner stays for the time to take action the player has asked for, then goes`() {
        val accessibility = TimeToTakeAction()
        compose.setContent {
            CompositionLocalProvider(
                LocalAppContainer provides AndroidAppContainer.get(ApplicationProvider.getApplicationContext()),
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
        assertTrue("the banner never showed", exists("Frosted"))
        assertEquals("icons, text, no controls", listOf(true, true, false), accessibility.asked.first())

        // Well past its own 4s hold.
        compose.mainClock.advanceTimeBy(HOLD_MILLIS * 2)
        assertTrue("the banner went at its own hold, not the player's", exists("Frosted"))

        compose.mainClock.advanceTimeBy(TIME_TO_TAKE_ACTION_MS - HOLD_MILLIS * 2 + FRAMES_TO_SHOW)
        assertTrue("the banner was still up after the player's time", !exists("Frosted"))
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
