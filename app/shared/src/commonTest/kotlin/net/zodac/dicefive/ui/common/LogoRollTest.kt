package net.zodac.dicefive.ui.common

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LogoRollTest {

    private val faces = listOf(2, 4, 5, 3, 6)

    @Test
    fun theRollStartsOnTheFansOwnDice() {
        faces.forEachIndexed { i, face ->
            val pose = logoRollPose(i, face, 0f)
            assertEquals(face, pose.value)
            assertEquals(0f, abs(pose.spinDegrees))
            assertEquals(0f, pose.hop)
        }
    }

    @Test
    fun theRollEndsBackOnTheFansOwnDiceLevelAndSquare() {
        faces.forEachIndexed { i, face ->
            val pose = logoRollPose(i, face, LOGO_ROLL_MILLIS)
            assertEquals(face, pose.value)
            assertEquals(360f, abs(pose.spinDegrees), "die $i should end a whole turn round")
            assertTrue(abs(pose.hop) < 1e-5f, "die $i should have landed: ${pose.hop}")
        }
    }

    @Test
    fun midRollEveryDieHopsSpinsAndShowsOtherFaces() {
        faces.forEachIndexed { i, face ->
            val seen = (0 until LOGO_ROLL_MILLIS.toInt() step 10).map { logoRollPose(i, face, it.toFloat()) }
            assertTrue(seen.any { it.hop > 0.3f }, "die $i should hop")
            assertTrue(seen.map { it.value }.filter { it != face }.distinct().size >= 3, "die $i should flick through faces")
        }
    }

    @Test
    fun neighbouringDiceSpinOppositeWaysAndStartOneAfterAnother() {
        val late = logoRollPose(0, 2, LOGO_ROLL_MILLIS)
        val next = logoRollPose(1, 4, LOGO_ROLL_MILLIS)
        assertTrue(late.spinDegrees * next.spinDegrees < 0f)
        assertTrue(logoRollPose(0, 2, 60f).spinDegrees != 0f)
        assertEquals(0f, logoRollPose(4, 6, 60f).spinDegrees, "the last die hasn't started yet")
    }

    @Test
    fun theLastStretchOfEachDiesRollShowsItsOwnFace() {
        faces.forEachIndexed { i, face ->
            val settled = logoRollPose(i, face, LOGO_ROLL_MILLIS - 50f)
            assertEquals(face, settled.value, "die $i should be seen landing on its own face")
        }
    }

    @Test
    fun aTapLandsOnTheCupOnlyInsideItsCentredRectangle() {
        // A 100-wide logo with a 60 by 120 cup: it spans x 20..80 and y 0..120.
        assertTrue(isOnLogoCup(50f, 10f, 100f, 60f, 120f))
        assertTrue(isOnLogoCup(21f, 119f, 100f, 60f, 120f))
        assertTrue(!isOnLogoCup(10f, 60f, 100f, 60f, 120f), "left of the cup")
        assertTrue(!isOnLogoCup(90f, 60f, 100f, 60f, 120f), "right of the cup")
        assertTrue(!isOnLogoCup(50f, 130f, 100f, 60f, 120f), "below the cup")
        assertTrue(!isOnLogoCup(50f, -1f, 100f, 60f, 120f), "above the cup")
    }
}
