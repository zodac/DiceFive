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
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.MutableStateFlow
import net.zodac.dicefive.data.achievements.AchievementsState
import net.zodac.dicefive.data.settings.SavedStyles
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.ui.game.style.DiceCupStyles
import net.zodac.dicefive.ui.game.style.DiceMats
import net.zodac.dicefive.ui.game.style.DiceStyles
import net.zodac.dicefive.ui.game.style.TableBackgrounds
import net.zodac.dicefive.ui.styles.StylesScreen
import net.zodac.dicefive.ui.styles.StylesViewModel
import net.zodac.dicefive.ui.styles.StylesWarmUp
import net.zodac.dicefive.ui.theme.DiceFiveTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The Styles page opens sized to fit its screen, as the menu's warm-up measured it there: a little
 * smaller where it only just doesn't fit, full size where it fits, or where it would need to shrink too far.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class StylesPageFitTest {

    @get:Rule
    val compose = createComposeRule()

    private val tiles = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)

    /** Warms the page up as the menu does, then opens it: the width of its first dice tile, in dp, and where its last tile ends. */
    private fun openAfterWarmUp(): Pair<Float, Float> {
        val achievements = AchievementsState(unlockedAt = Achievement.entries.associateWith { 0L })
        val saved = MutableStateFlow<SavedStyles?>(
            SavedStyles(DiceStyles.default.id, DiceCupStyles.default.id, TableBackgrounds.default.id, DiceMats.default.id, achievements),
        )
        val viewModel = StylesViewModel(savedStyles = saved)
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
        repeat(600) { compose.mainClock.advanceTimeByFrame() }
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
    @Config(qualifiers = "w393dp-h1000dp")
    fun `a page a little too tall for its screen opens shrunk just enough to fit`() {
        val (dieTile, lowest) = openAfterWarmUp()
        assertTrue("dice tile $dieTile", dieTile < 72f && dieTile >= 72f * 0.75f)
        val height = compose.onRoot().fetchSemanticsNode().size.height
        assertTrue("tiles end at $lowest on a $height screen", lowest < height)
    }

    @Test
    @Config(qualifiers = "w393dp-h1200dp")
    fun `a page that fits opens full size`() {
        assertEquals(72f, openAfterWarmUp().first, 0.5f)
    }

    @Test
    @Config(qualifiers = "w393dp-h700dp")
    fun `a page too tall to fit even shrunk opens full size and scrolls`() {
        assertEquals(72f, openAfterWarmUp().first, 0.5f)
    }
}
