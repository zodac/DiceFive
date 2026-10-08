package net.zodac.dicefive

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
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

/**
 * DiceTray in a mode that rolls more dice than it holds (Stud's seven dice, five hold slots), on the
 * real gesture code: a tap in a die's mat column holds it, a tap on a hold slot lets its die go, and
 * neither does anything else - a slot never holds, the mat never lets go, and a full set of slots
 * holds no more. And what a screen reader gets for the same: each slot, each die on the mat, and the
 * one action each offers.
 */
@RunWith(AndroidJUnit4::class)
class SlottedDiceTrayTest {

    @get:Rule
    val compose = createComposeRule()

    private val toggled = mutableListOf<Int>()
    private val cycled = mutableListOf<Int>()

    private fun hasStateDescription(state: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, state)

    private val showcase = Showcase(compose)

    private fun showTray(dice: List<Die>, superuserModeActive: Boolean = false) {
        toggled.clear()
        cycled.clear()
        showcase.show {
            DiceFiveTheme {
                DiceTray(
                    dice = dice,
                    gameMode = GameMode.STUD,
                    enabled = true,
                    showDice = true,
                    rolling = false,
                    onToggleHold = { toggled += it },
                    modifier = Modifier.width(TRAY_WIDTH.dp).testTag(TRAY),
                    superuserModeActive = superuserModeActive,
                    onCycleValue = { cycled += it },
                )
            }
        }
    }

    /** Seven dice, 1 to 7 by face (the last a 6), with [held] of them held in the slots given. */
    private fun dice(vararg held: Pair<Int, Int>): List<Die> = List(7) { index ->
        val slot = held.firstOrNull { it.first == index }?.second
        Die(value = minOf(index + 1, 6), isHeld = slot != null, heldSlot = slot)
    }

    // The tray's padding, the hold slots' row (as tall as a slot) and the gap down to the mat - see DiceTray.
    private fun TouchInjectionScope.inner(): Float = width - 2 * PADDING.dp.toPx()

    private fun TouchInjectionScope.slot(index: Int) =
        Offset(PADDING.dp.toPx() + inner() * (index + 0.5f) / 5, (PADDING + SLOT / 2).dp.toPx())

    private fun TouchInjectionScope.matColumn(index: Int) =
        Offset(PADDING.dp.toPx() + inner() * (index + 0.5f) / 7, (PADDING + SLOT + SLOT_TO_MAT + MAT / 2).dp.toPx())

    @Test
    fun `a tap in an unheld die's mat column holds it - one on a slot lets its die go - and nothing else does a thing`() {
        showTray(dice())
        compose.onNodeWithTag(TRAY).performTouchInput { click(matColumn(6)) }
        assertEquals(listOf(6), toggled)

        // A held die's mat column does nothing - it's in a slot.
        showTray(dice(6 to 0))
        compose.onNodeWithTag(TRAY).performTouchInput { click(matColumn(6)) }
        assertEquals(emptyList<Int>(), toggled)

        // With every slot full a tap on the mat holds nothing.
        showTray(dice(0 to 0, 1 to 1, 2 to 2, 3 to 3, 4 to 4))
        compose.onNodeWithTag(TRAY).performTouchInput { click(matColumn(5)) }
        assertEquals(emptyList<Int>(), toggled)

        // A tap on a hold slot lets its die go - and an empty slot does nothing.
        showTray(dice(5 to 2))
        compose.onNodeWithTag(TRAY).performTouchInput {
            click(slot(2))
            click(slot(0))
        }
        assertEquals(listOf(5), toggled)

        // In superuser mode a long press on a slot cycles its die's face.
        showTray(dice(4 to 1), superuserModeActive = true)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(TRAY).performTouchInput { down(slot(1)) }
        compose.mainClock.advanceTimeBy(2_500)
        compose.onNodeWithTag(TRAY).performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals(listOf(4, 4), cycled)
        assertEquals(emptyList<Int>(), toggled)
    }

    @Test
    fun `each slot says what's in it and lets it go - each die on the mat offers to hold it - unless the slots are full`() {
        showTray(dice(2 to 0))
        compose.onNodeWithContentDescription("Hold slot 2 of 5").assert(hasStateDescription("Empty"))
        compose.onNodeWithContentDescription("Die 1, 1").assert(hasStateDescription("Not held")).performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithContentDescription("Hold slot 1 of 5, Die 3, 3").assert(hasStateDescription("Held")).performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(listOf(0, 2), toggled)
        // The held die is in its slot, not on the mat, so it isn't a node of its own there too.
        compose.onNodeWithContentDescription("Die 3, 3").assertDoesNotExist()

        showTray(dice(0 to 0, 1 to 1, 2 to 2, 3 to 3, 4 to 4))
        compose.onNodeWithContentDescription("Die 6, 6")
            .assert(hasStateDescription("Not held, hold slots full"))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
    }

    private companion object {
        const val TRAY = "tray"
        const val TRAY_WIDTH = 500
        const val PADDING = 16
        const val SLOT = 52
        const val SLOT_TO_MAT = 18
        const val MAT = 96
    }
}
