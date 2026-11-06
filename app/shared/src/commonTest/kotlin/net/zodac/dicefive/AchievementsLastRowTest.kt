package net.zodac.dicefive

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import kotlin.test.Test
import kotlin.test.assertEquals
import net.zodac.dicefive.app.LocalAppContainer
import net.zodac.dicefive.data.achievements.AchievementsState
import net.zodac.dicefive.data.achievements.InMemoryAchievementStore
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.model.AchievementCategory
import net.zodac.dicefive.platform.LocalPlatformServices
import net.zodac.dicefive.platform.SilentPlatformServices
import net.zodac.dicefive.ui.achievements.AchievementsScreen
import net.zodac.dicefive.ui.achievements.resetAchievementsScrollMemory
import net.zodac.dicefive.ui.common.LocalReduceMotion
import net.zodac.dicefive.ui.theme.DiceFiveTheme

/** Half the header's height (its 48dp arrows), at density 3; an arrow's own node is the 40dp button within that. */
private const val HALF_HEADER_PX = 72

/** Further than the whole list, so each scroll reaches its end. */
private const val PAST_THE_END_PX = 100_000f

/** Scrolled again after each settles: the tail is resized a frame after the last row is first measured. */
private const val SCROLLS_TO_THE_END = 4

/** At the list's furthest scroll, its last row rests whole, directly under the pinned header. */
class AchievementsLastRowTest : UiTest() {

    /** The page at the end of its Unearned list, with [unlocked] already earned. */
    private fun ComposeUiTest.showEndOfUnearned(unlocked: Collection<Achievement>) {
        resetAchievementsScrollMemory()
        val viewModel = achievementsViewModel(store = InMemoryAchievementStore(AchievementsState(unlockedAt = unlocked.associateWith { 1L })))
        mainClock.autoAdvance = false
        setContent {
            CompositionLocalProvider(
                LocalAppContainer provides inMemoryAppContainer(),
                LocalPlatformServices provides SilentPlatformServices,
                LocalReduceMotion provides true,
            ) {
                DiceFiveTheme { AchievementsScreen(viewModel = viewModel, onBack = {}) }
            }
        }
        mainClock.advanceTimeBy(3000)
        onNode(hasText("All") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)).performClick()
        mainClock.advanceTimeBy(1000)
        repeat(SCROLLS_TO_THE_END) {
            onNode(hasScrollToIndexAction()).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, PAST_THE_END_PX) }
            mainClock.advanceTimeBy(1000)
        }
    }

    /** That the card of the list's last row, Completionist, starts exactly where the pinned header ends. */
    private fun ComposeUiTest.assertLastRowDirectlyUnderHeader() {
        val headerBottom = onAllNodes(hasContentDescription("Next category")).fetchSemanticsNodes().minOf { it.boundsInRoot.center.y } + HALF_HEADER_PX
        val row = onNode(hasText("Completionist")).fetchSemanticsNode().boundsInRoot
        assertEquals(headerBottom, row.top, "The last row's top against the pinned header's bottom")
    }

    @Test
    fun `the only row left rests directly under its header`() = uiTest(TestScreen(360, 800, density = 3f)) {
        showEndOfUnearned(Achievement.entries - Achievement.COMPLETIONIST)
        assertLastRowDirectlyUnderHeader()
    }

    @Test
    fun `the last row of a long list rests directly under its header`() = uiTest(TestScreen(360, 800, density = 3f)) {
        showEndOfUnearned(Achievement.entries.filter { it.category == AchievementCategory.EASTER_EGGS })
        assertLastRowDirectlyUnderHeader()
    }

    @Test
    fun `the last row of a landscape page rests directly under its header`() = uiTest(TestScreen(891, 411, density = 3f)) {
        showEndOfUnearned(Achievement.entries.filter { it.category == AchievementCategory.EASTER_EGGS })
        assertLastRowDirectlyUnderHeader()
    }
}
