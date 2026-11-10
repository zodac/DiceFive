package net.zodac.dicefive

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.zodac.dicefive.data.achievements.AchievementsState
import net.zodac.dicefive.data.settings.SavedStyles
import net.zodac.dicefive.ui.style.DiceCupStyles
import net.zodac.dicefive.ui.style.DiceMats
import net.zodac.dicefive.ui.style.DiceStyles
import net.zodac.dicefive.ui.style.TableBackgrounds
import net.zodac.dicefive.ui.styles.StyleCategoriesWarmUp
import net.zodac.dicefive.ui.styles.StylesWarmUp
import net.zodac.dicefive.ui.styles.categoryWarmUpDone
import net.zodac.dicefive.ui.styles.categoryWarmUpPasses
import net.zodac.dicefive.ui.theme.DiceFiveTheme

/**
 * The Styles warm-ups: the menu's draws only the Styles page; the Styles page's draws every category page, carrying on
 * from where it was stopped rather than starting again.
 *
 * Android only: it steps the clock through seconds of off-screen pages.
 */
class StylesWarmUpTest : UiTest() {

    private val picks = SavedStyles(DiceStyles.default.id, DiceCupStyles.default.id, TableBackgrounds.default.id, DiceMats.default.id, AchievementsState())

    @Test
    fun `the menu's warm-up leaves the category pages - the Styles page's draws them all, carrying on where it was stopped`() = uiTest(TestScreen(360, 800)) {
        categoryWarmUpDone = 0
        val total = categoryWarmUpPasses(picks).size
        var onMenu by mutableStateOf(true)
        var onStyles by mutableStateOf(false)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            DiceFiveTheme {
                Box {
                    if (onMenu) StylesWarmUp(picks = picks, width = 360.dp)
                    if (onStyles) StyleCategoriesWarmUp(picks = picks, width = 360.dp)
                }
            }
        }
        fun frames(count: Int) = repeat(count) { compose.mainClock.advanceTimeByFrame() }
        fun set(change: () -> Unit) = compose.runOnIdle {
            change()
            Snapshot.sendApplyNotifications()
        }
        // The menu's: long past its start and its two passes, and no category page drawn.
        frames(300)
        assertEquals(0, categoryWarmUpDone)

        // The Styles page opened: some way in, then left (a category opened) before the end.
        set { onMenu = false; onStyles = true }
        frames(60)
        val stoppedAt = categoryWarmUpDone
        assertTrue(stoppedAt in 1 until total, "stopped at $stoppedAt of $total")
        set { onStyles = false }
        frames(60)
        assertEquals(stoppedAt, categoryWarmUpDone)

        // Back on the Styles page: on from there, never from the start, to the end.
        set { onStyles = true }
        frames(40)
        assertTrue(categoryWarmUpDone >= stoppedAt, "went back to $categoryWarmUpDone")
        frames(total * 3)
        assertEquals(total, categoryWarmUpDone)
    }
}
