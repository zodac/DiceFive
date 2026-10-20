package net.zodac.dicefive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.zodac.dicefive.ui.style.D20DiceStyle
import net.zodac.dicefive.ui.style.DiceStyle
import net.zodac.dicefive.ui.style.DiceStyles
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Every dice style draws every face it can land on, each one telling its value apart from the others, and a held die
 * apart from the same face loose. Faces 1 to 6, and to 20 for a D20 style.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1000dp-h400dp")
class DiceFacesTest {

    @get:Rule
    val compose = createComposeRule()

    private fun faces(style: DiceStyle) = if (style is D20DiceStyle) 1..20 else 1..6

    @Test
    fun `every dice style draws each face its own way - and a held die apart from a loose one`() {
        var style by mutableStateOf(DiceStyles.default)
        var held by mutableStateOf(false)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            // Each face in a row, every die with room round it for its shadow.
            Row(Modifier.testTag("dice")) {
                for (value in faces(style)) {
                    Box(Modifier.padding(GAP_DP.dp).size(FACE_DP.dp)) { style.Die(value = value, held = held, modifier = Modifier.fillMaxSize()) }
                }
            }
        }
        /** Each die's pixels, [inset] inside its cell, read out once. */
        fun shoot(count: Int, inset: Int): List<IntArray> {
            compose.mainClock.advanceTimeBy(SETTLE_MILLIS)
            val image = compose.onNodeWithTag("dice").captureToImage().asAndroidBitmap()
            val cell = image.height
            val size = cell - 2 * inset
            return List(count) { index -> IntArray(size * size).also { image.getPixels(it, 0, size, index * cell + inset, inset, size, size) } }
        }
        // A die's shadow falls by where it lies on the screen, so faces in different places are compared inside their edge.
        val faceInset = ((GAP_DP + EDGE_DP) * compose.density.density).toInt()
        val failures = mutableListOf<String>()
        for (dice in DiceStyles.all) {
            Snapshot.withMutableSnapshot {
                style = dice
                held = false
            }
            val values = faces(dice).toList()
            val faces = shoot(values.size, faceInset)
            val loose = shoot(values.size, inset = 0)
            Snapshot.withMutableSnapshot { held = true }
            val heldDice = shoot(values.size, inset = 0)
            for (i in values.indices) {
                for (j in i + 1 until values.size) if (faces[i].contentEquals(faces[j])) failures += "${dice.id}: ${values[i]} and ${values[j]} look the same"
                if (faces[i].distinct().size < 2) failures += "${dice.id}: ${values[i]} is blank"
                if (loose[i].contentEquals(heldDice[i])) failures += "${dice.id}: a held ${values[i]} looks loose"
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    private companion object {
        const val FACE_DP = 32

        /** Room round each die for its shadow, which reaches past its own square. */
        const val GAP_DP = 8

        /** How far inside its square a die's face is compared from: clear of its rim and the shadow round it. */
        const val EDGE_DP = 4

        /** Past any face's entrance; an animated face (a glitch, a sparkle) still differs from its neighbours mid-way. */
        const val SETTLE_MILLIS = 500L
    }
}
