package net.zodac.dicefive.ui.game.style

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertTrue

class CubeFaceProjectionTest {

    private val side = 132f

    private fun radians(degrees: Float) = degrees * PI.toFloat() / 180f

    private fun assertNear(expected: Offset, actual: Offset, message: String) {
        assertTrue((expected - actual).getDistance() < 0.01f, "$message: expected $expected, was $actual")
    }

    @Test
    fun aCubeFaceIsDrawnFlatWhereItIsAndTippingAwayShrinksWhileTheNextLoomsSharingTheirEdge() {
        // A face lying flat is drawn exactly where it is.
        val flat = cubeFaceProjection(side, 0f)
        for (corner in listOf(Offset(0f, 0f), Offset(side, 0f), Offset(0f, side), Offset(side, side), Offset(40f, 90f))) {
            assertNear(corner, flat.map(corner), "flat face at $corner")
        }

        // The face tipping off and the one tipping on share their edge.
        // The top face's near (bottom) edge is the incoming face's far (top) edge, at every point of a turn.
        for (step in 1..17) {
            val tipped = step * 5f
            val leaving = cubeFaceProjection(side, radians(tipped))
            val arriving = cubeFaceProjection(side, radians(tipped - 90f))
            for (x in listOf(0f, side / 3f, side)) {
                assertNear(leaving.map(Offset(x, side)), arriving.map(Offset(x, 0f)), "edge at x=$x, tipped $tipped")
            }
        }

        // A face tipping away shrinks and one coming up looms.
        val leaving = cubeFaceProjection(side, radians(30f))
        val farEdge = leaving.map(Offset(side, 0f)).x - leaving.map(Offset(0f, 0f)).x
        val nearEdge = leaving.map(Offset(side, side)).x - leaving.map(Offset(0f, side)).x
        assertTrue(farEdge < nearEdge, "the edge tipped away should look narrower: $farEdge vs $nearEdge")
        assertTrue(nearEdge < side * 1.06f, "no edge should loom past the tray's allowance: $nearEdge")
    }
}
