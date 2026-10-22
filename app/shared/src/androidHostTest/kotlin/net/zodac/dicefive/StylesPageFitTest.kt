package net.zodac.dicefive

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onRoot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import net.zodac.dicefive.data.achievements.AchievementsState
import net.zodac.dicefive.data.settings.SavedStyles
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.ui.style.DiceCupStyles
import net.zodac.dicefive.ui.style.DiceMats
import net.zodac.dicefive.ui.style.DiceStyles
import net.zodac.dicefive.ui.style.TableBackgrounds
import net.zodac.dicefive.ui.styles.StylesScreen
import net.zodac.dicefive.ui.styles.StylesWarmUp
import net.zodac.dicefive.ui.theme.DiceFiveTheme

/**
 * The Styles page opens sized to fit its screen, as the menu's warm-up measured it there: a little
 * smaller where it only just doesn't fit, full size where it fits, or where it would need to shrink too far.
 *
 * Android only: its screen heights are chosen against the room Android's system bars leave, which a desktop window
 * doesn't take.
 */
class StylesPageFitTest : UiTest() {

    private val tiles = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)

    /**
     * Warms the page up as the menu does for [frames], then opens it: the width of its first dice tile, in dp, and where its last
     * tile ends.
     */
    private fun openAfterWarmUp(frames: Int = 600): Pair<Float, Float> {
        val achievements = AchievementsState(unlockedAt = Achievement.entries.associateWith { 0L })
        val saved = MutableStateFlow<SavedStyles?>(
            SavedStyles(DiceStyles.default.id, DiceCupStyles.default.id, TableBackgrounds.default.id, DiceMats.default.id, achievements),
        )
        val viewModel = stylesViewModel(saved)
        var open by mutableStateOf(false)
        compose.setContent {
            DiceFiveTheme {
                if (open) {
                    StylesScreen(viewModel = viewModel, onBack = {})
                } else {
                    // As the menu runs it, saving what it measured with the picks.
                    val picks by saved.collectAsState()
                    BoxWithConstraints {
                        StylesWarmUp(picks = picks, width = maxWidth, onPageFit = { fit -> saved.value = saved.value?.copy(stylesPageFit = fit) })
                    }
                }
            }
        }
        compose.mainClock.autoAdvance = false
        repeat(frames) { compose.mainClock.advanceTimeByFrame() }
        compose.runOnIdle {
            open = true
            Snapshot.sendApplyNotifications()
        }
        repeat(120) { compose.mainClock.advanceTimeByFrame() }
        val nodes = compose.onAllNodes(tiles).fetchSemanticsNodes()
        val density = compose.density.density
        val dieTile = nodes.minBy { it.positionInRoot.y + it.positionInRoot.x / 10_000f }.size.width / density
        return dieTile to nodes.maxOf { it.positionInRoot.y + it.size.height }
    }

    @Test
    fun `a page a little too tall for its screen opens shrunk just enough to fit`() = uiTest(TestScreen(393, 820)) {
        val (dieTile, lowest) = openAfterWarmUp()
        assertTrue(dieTile < 72f && dieTile >= 72f * 0.7f, "dice tile $dieTile")
        val height = compose.onRoot().fetchSemanticsNode().size.height
        assertTrue(lowest < height, "tiles end at $lowest on a $height screen")
    }

    @Test
    fun `a page that fits opens full size`() = uiTest(TestScreen(393, 1200)) {
        assertEquals(72f, openAfterWarmUp().first, 0.5f)
    }

    @Test
    fun `a page too tall to fit even shrunk opens full size and scrolls`() = uiTest(TestScreen(393, 700)) {
        assertEquals(72f, openAfterWarmUp().first, 0.5f)
    }

    @Test
    fun `a page opened about a second after the menu, before the tiles are warmed, is already sized to fit`() = uiTest(TestScreen(393, 820)) {
        // 600ms of waiting, then the measures, then the tiles: these frames end long before the tiles do.
        val (dieTile, _) = openAfterWarmUp(frames = 60)
        assertTrue(dieTile < 72f && dieTile >= 72f * 0.7f, "dice tile $dieTile")
    }
}
