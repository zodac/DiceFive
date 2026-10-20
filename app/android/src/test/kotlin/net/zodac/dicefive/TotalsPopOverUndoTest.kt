package net.zodac.dicefive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.math.roundToInt
import net.zodac.dicefive.ui.game.TotalsButton
import net.zodac.dicefive.ui.game.UndoButton
import net.zodac.dicefive.ui.theme.DiceFiveTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * A bonus earned in a solo game pops the Totals button to half as big again, reaching over the 8dp gap onto the
 * Undo button beside it: it swells over Undo, not behind it.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class TotalsPopOverUndoTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `at the height of its pop the Totals button is drawn over the Undo button beside it`() {
        var upperBonus by mutableIntStateOf(0)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            DiceFiveTheme {
                // The same pop twice: beside Undo, as on the board, and beside an empty space Undo's size.
                Column(Modifier.background(Color.Black).padding(24.dp), verticalArrangement = Arrangement.spacedBy(48.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(BUTTON_GAP)) {
                        TotalsButton(upperTotal = 63, upperBonus = upperBonus, lowerTotal = 0, minSize = BUTTON_SIZE, flourish = true)
                        UndoButton(enabled = true, onClick = {}, minSize = BUTTON_SIZE)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(BUTTON_GAP)) {
                        TotalsButton(upperTotal = 63, upperBonus = upperBonus, lowerTotal = 0, minSize = BUTTON_SIZE, flourish = true)
                        Spacer(Modifier.size(BUTTON_SIZE))
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(100)
        compose.runOnIdle {
            upperBonus = 35
            Snapshot.sendApplyNotifications()
        }
        // Half of the 1000ms pop: as big as it gets.
        compose.mainClock.advanceTimeBy(500)

        val totals = compose.onAllNodesWithContentDescription("Totals").fetchSemanticsNodes().map { it.boundsInRoot }
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        val density = compose.density.density
        // A strip of Undo's leading edge that the swollen button covers: just past the gap, a few dp tall, mid-height.
        val left = (totals[0].right + (BUTTON_GAP.value + 1) * density).roundToInt()
        val right = (totals[0].right + (BUTTON_GAP.value + 3) * density).roundToInt()
        val halfHeight = (6 * density).roundToInt()
        val beside = totals[0].center.y.roundToInt()
        val alone = totals[1].center.y.roundToInt()
        var differing = 0
        var uncovered = 0
        for (x in left until right) {
            for (dy in -halfHeight..halfHeight) {
                if (image.getPixel(x, beside + dy) != image.getPixel(x, alone + dy)) differing++
                if (image.getPixel(x, alone + dy) == android.graphics.Color.BLACK) uncovered++
            }
        }
        assertEquals("Pixels of the strip the swollen button doesn't reach", 0, uncovered)
        assertEquals("Pixels of the swollen Totals button hidden by Undo", 0, differing)
    }

    private companion object {
        val BUTTON_SIZE = 48.dp
        val BUTTON_GAP = 8.dp
    }
}
