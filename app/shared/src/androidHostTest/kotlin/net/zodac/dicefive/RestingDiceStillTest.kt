package net.zodac.dicefive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.zodac.dicefive.data.settings.AnimationLevel
import net.zodac.dicefive.ui.common.LocalAnimationLevel
import net.zodac.dicefive.ui.style.DiceStyle
import net.zodac.dicefive.ui.style.DiceStyles
import net.zodac.dicefive.ui.style.GoldenEggDiceStyle
import org.robolectric.annotation.Config

/**
 * Dice lying on the mat move on their own only at the High animation level: below it, every dice style holds still, so
 * a turn spent thinking redraws nothing. The golden egg's sparkle used to keep going at Medium.
 */
@Config(qualifiers = "w1000dp-h400dp")
class RestingDiceStillTest : UiTest() {

    private var style by mutableStateOf<DiceStyle>(DiceStyles.default)
    private var level by mutableStateOf(AnimationLevel.MEDIUM)

    private fun setUp() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalAnimationLevel provides level) {
                Row(Modifier.testTag("dice")) {
                    for (value in 1..6) {
                        Box(Modifier.padding(GAP_DP.dp).size(FACE_DP.dp)) { style.Die(value = value, held = false, modifier = Modifier.fillMaxSize()) }
                    }
                }
            }
        }
    }

    private fun show(dice: DiceStyle, at: AnimationLevel) {
        Snapshot.withMutableSnapshot {
            style = dice
            level = at
        }
        compose.mainClock.advanceTimeBy(SETTLE_MILLIS)
    }

    /** The dice's pixels after [millis] more. */
    private fun shotAfter(millis: Long): IntArray {
        compose.mainClock.advanceTimeBy(millis)
        val image = compose.onNodeWithTag("dice").captureToImage().asAndroidBitmap()
        return IntArray(image.width * image.height).also { image.getPixels(it, 0, image.width, 0, 0, image.width, image.height) }
    }

    /** Whether the dice look the same at each of [OFFSETS_MILLIS] as they did at first. */
    private fun holdsStill(): Boolean {
        val first = shotAfter(0)
        return OFFSETS_MILLIS.all { shotAfter(it).contentEquals(first) }
    }

    @Test
    fun `every dice style holds still on the mat below the high animation level`() = uiTest {
        setUp()
        val moving = DiceStyles.all.filter { dice ->
            AnimationLevel.entries.filter { !it.ambientMotion }.any { at ->
                show(dice, at)
                !holdsStill()
            }
        }
        assertTrue(moving.isEmpty(), "These dice move on their own below High: ${moving.joinToString { it.id }}")
    }

    @Test
    fun `the golden egg sparkles at high`() = uiTest {
        setUp()
        show(GoldenEggDiceStyle, AnimationLevel.HIGH)
        assertFalse(holdsStill(), "The golden egg's sparkle should swell and shrink at High")
    }

    private companion object {
        const val FACE_DP = 32

        /** Room round each die for its shadow, which reaches past its own square. */
        const val GAP_DP = 8

        /** Past any face's entrance. */
        const val SETTLE_MILLIS = 500L

        /** Times after the first look, staggered so a loop of any length is caught away from where it started. */
        val OFFSETS_MILLIS = listOf(170L, 330L, 610L, 1_090L)
    }
}
