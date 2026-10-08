package net.zodac.dicefive.ui.game.style

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TossPathTest {

    private fun path(seed: Int, result: Int, startFace: Int?, restTop: Int? = null) = TossPath(
        seed = seed,
        result = result,
        startFace = startFace,
        restTop = restTop,
        startY = 160f,
        restY = 30f,
        dieSize = 44f,
        sideRoom = 4f,
    )

    // The face showing after [turns] quarter-turns, as TossedCube and the Cube style read the ring.
    private fun TossPath.faceAfter(turns: Int) = ring[(turns - finalTurns).mod(ring.size)]

    @Test
    fun everyTossTurnsRoundOneAxisRingOfFourFromTheFacePickedUpToTheResultWithTheStylesTopFaceOnTop() {
        // Every ring is four faces round one axis.
        for (seed in 0 until 40) {
            for (result in 1..6) {
                val ring = path(seed, result, startFace = null).ring
                assertEquals(result, ring[0])
                assertEquals(7 - ring[0], ring[2])
                assertEquals(7 - ring[1], ring[3])
                assertEquals(4, ring.toSet().size)
            }
        }

        // It lands on the result and starts on the face picked up.
        for (seed in 0 until 20) {
            for (result in 1..6) {
                for (start in 1..6) {
                    val path = path(seed, result, startFace = start)
                    assertEquals(result, path.faceAfter(path.finalTurns))
                    assertEquals(start, path.faceAfter(0), "result $result from $start")
                    assertTrue(path.finalTurns >= 1)
                }
            }
        }

        // It lands with the styles top face on top whatever it started on.
        for (seed in 0 until 20) {
            for (result in 1..6) {
                val top = (1..6).first { it != result && it != 7 - result }
                for (start in listOf(null) + (1..6)) {
                    val path = path(seed, result, startFace = start, restTop = top)
                    assertEquals(result, path.faceAfter(path.finalTurns))
                    assertEquals(top, path.faceAfter(path.finalTurns - 1), "result $result from $start")
                }
            }
        }
    }
}
