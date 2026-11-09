package net.zodac.dicefive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.zodac.dicefive.data.settings.AnimationLevel
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.ui.common.LocalAnimationLevel
import net.zodac.dicefive.ui.game.CategoryTile
import net.zodac.dicefive.ui.style.GameVisualTheme
import net.zodac.dicefive.ui.style.LocalGameVisualTheme
import net.zodac.dicefive.ui.style.ScoreBoxStyle
import net.zodac.dicefive.ui.style.ScoreBoxes
import org.robolectric.annotation.Config

/**
 * Box styles move as the dice of the same name do: RGB's lights cycle and Neon's glow pulses at the High animation level,
 * and below it every box style holds still, as `RestingDiceStillTest` holds the dice. No other box style moves at all.
 */
@Config(qualifiers = "w1000dp-h400dp")
class BoxStylesMotionTest : UiTest() {

    private var style by mutableStateOf<ScoreBoxStyle>(ScoreBoxes.default)
    private var level by mutableStateOf(AnimationLevel.MEDIUM)

    private fun setUp() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalAnimationLevel provides level, LocalGameVisualTheme provides GameVisualTheme(box = style)) {
                Row(Modifier.testTag("boxes"), horizontalArrangement = Arrangement.spacedBy(GAP_DP.dp)) {
                    // Open boxes, D20's two halves included: the boxes that light up.
                    for (category in BOXES) CategoryTile(category = category, highlighted = false)
                }
            }
        }
    }

    private fun show(box: ScoreBoxStyle, at: AnimationLevel) {
        Snapshot.withMutableSnapshot {
            style = box
            level = at
        }
        compose.mainClock.advanceTimeBy(SETTLE_MILLIS)
    }

    private fun shotAfter(millis: Long): IntArray {
        compose.mainClock.advanceTimeBy(millis)
        val image = compose.onNodeWithTag("boxes").captureToImage().asAndroidBitmap()
        return IntArray(image.width * image.height).also { image.getPixels(it, 0, image.width, 0, 0, image.width, image.height) }
    }

    private fun holdsStill(): Boolean {
        val first = shotAfter(0)
        return OFFSETS_MILLIS.all { shotAfter(it).contentEquals(first) }
    }

    @Test
    fun `every box style holds still below the high animation level`() = uiTest {
        setUp()
        val moving = ScoreBoxes.all.filter { box ->
            AnimationLevel.entries.filter { !it.ambientMotion }.any { at ->
                show(box, at)
                !holdsStill()
            }
        }
        assertTrue(moving.isEmpty(), "These box styles move below High: ${moving.joinToString { it.id }}")
    }

    @Test
    fun `at high only RGB and Neon move - RGB's lights cycling and Neon's glow pulsing - as their dice do`() = uiTest {
        setUp()
        val moving = ScoreBoxes.all.filter { box ->
            show(box, AnimationLevel.HIGH)
            !holdsStill()
        }.map { it.id }
        assertEquals(ScoreBoxes.all.map { it.id }.filter { it.startsWith("rgb_") || it.startsWith("neon_") }, moving)
    }

    private companion object {
        const val GAP_DP = 4
        const val SETTLE_MILLIS = 500L
        val OFFSETS_MILLIS = listOf(400L, 400L, 400L)
        val BOXES = listOf(ScoreCategory.THREES, ScoreCategory.THREE_OF_A_KIND, ScoreCategory.CHANCE, ScoreCategory.D20_7, ScoreCategory.D20_14)
    }
}
