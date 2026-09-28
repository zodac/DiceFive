package net.zodac.dicefive

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.zodac.dicefive.model.Die
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.ui.game.DiceTray
import net.zodac.dicefive.ui.theme.DiceFiveTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * DiceTray's press handling - how a player holds a die - on the real gesture code, under
 * Robolectric: a tap toggles the die under it, a press that wanders within one column still does,
 * one that ends in a different column acts on that one, and superuser mode's long press cycles a
 * held die's face instead of toggling it.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class DiceTrayGestureTest {

    @get:Rule
    val compose = createComposeRule()

    private val toggled = mutableListOf<Int>()
    private val cycled = mutableListOf<Int>()

    private fun showTray(dice: List<Die> = List(5) { Die(value = it + 1) }, superuserModeActive: Boolean = false) {
        compose.setContent {
            DiceFiveTheme {
                DiceTray(
                    dice = dice,
                    gameMode = GameMode.STANDARD,
                    enabled = true,
                    showDice = true,
                    rolling = false,
                    onToggleHold = { toggled += it },
                    modifier = Modifier.width(500.dp).testTag(TRAY),
                    superuserModeActive = superuserModeActive,
                    onCycleValue = { cycled += it },
                )
            }
        }
    }

    /** The x coordinate of the middle of die [index]'s column, in the tray's own pixels. */
    private fun columnCentre(index: Int, trayWidth: Float): Float = trayWidth * (index + 0.5f) / 5

    @Test
    fun `a tap toggles the die under it`() {
        showTray()

        compose.onNodeWithTag(TRAY).performTouchInput { click(position = center.copy(x = columnCentre(3, width.toFloat()))) }

        assertEquals(listOf(3), toggled)
    }

    @Test
    fun `a press that wanders but stays within one column still toggles that die`() {
        showTray()

        compose.onNodeWithTag(TRAY).performTouchInput {
            val x = columnCentre(1, width.toFloat())
            down(center.copy(x = x))
            moveBy(delta = Offset(10f, 15f))
            up()
        }

        assertEquals(listOf(1), toggled)
    }

    @Test
    fun `a press released over a different column acts on that one`() {
        showTray()

        compose.onNodeWithTag(TRAY).performTouchInput {
            down(center.copy(x = columnCentre(0, width.toFloat())))
            moveTo(center.copy(x = columnCentre(4, width.toFloat())))
            up()
        }

        assertEquals(listOf(4), toggled)
    }

    @Test
    fun `in superuser mode a long press on a held die cycles its face instead of toggling it`() {
        showTray(dice = List(5) { Die(value = it + 1, isHeld = it == 2) }, superuserModeActive = true)

        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(TRAY).performTouchInput { down(center.copy(x = columnCentre(2, width.toFloat()))) }
        compose.mainClock.advanceTimeBy(2_500)
        compose.onNodeWithTag(TRAY).performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()

        assertEquals(listOf(2, 2), cycled)
        assertEquals(emptyList<Int>(), toggled)
    }

    @Test
    fun `consecutive taps are each handled`() {
        showTray()

        compose.onNodeWithTag(TRAY).performTouchInput {
            click(position = center.copy(x = columnCentre(0, width.toFloat())))
            click(position = center.copy(x = columnCentre(2, width.toFloat())))
        }

        assertEquals(listOf(0, 2), toggled)
    }

    private companion object {
        const val TRAY = "tray"
    }
}
