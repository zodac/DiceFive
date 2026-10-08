package net.zodac.dicefive.ui.common

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiceDriftTest {

    private val width = 1080f
    private val height = 2340f
    private val frameSeconds = 1f / 60f

    private fun startingDice() = listOf(
        DriftingDie(x = 0.16f, y = 0.80f, size = 0.30f, rotation = -14f, value = 5),
        DriftingDie(x = 0.82f, y = 0.92f, size = 0.38f, rotation = 11f, value = 3),
        DriftingDie(x = 0.90f, y = 0.42f, size = 0.22f, rotation = 24f, value = 2),
        DriftingDie(x = 0.06f, y = 0.40f, size = 0.18f, rotation = -28f, value = 6),
    )

    /** Runs a fresh drift for [minutes], calling [onFrame] after every frame. */
    private fun run(minutes: Int, seed: Int = 7, onFrame: (DiceDrift) -> Unit = {}): DiceDrift {
        val drift = DiceDrift(startingDice(), Random(seed))
        repeat(minutes * 60 * 60) {
            drift.advance(frameSeconds, width, height)
            onFrame(drift)
        }
        return drift
    }

    @Test
    fun theFirstFrameIsTheStillBackdropAndThreeToFiveDiceAreAlwaysOnScreenAfter() {
        val drift = DiceDrift(startingDice(), Random(1))
        drift.advance(0f, width, height)
        assertEquals(listOf(0.16f, 0.82f, 0.90f, 0.06f), drift.dice.take(4).map { it.x })
        assertEquals(4, drift.dice.count { isShowing(it, width, height) }, "the fifth die starts out of sight")

        for (seed in 1..5) {
            run(minutes = 10, seed = seed) { running ->
                val showing = running.dice.count { isShowing(it, width, height) }
                assertTrue(showing in 3..5, "seed $seed: $showing showing")
            }
        }
    }

    @Test
    fun eachCrossingIsASlowStraightLineUpAndAcrossTurningSlowerThanItTravelsAndDiceComeBackSometimesTheOtherWay() {
        val velocities = mutableMapOf<Pair<DriftingDie, Int>, Pair<Float, Float>>()
        val passes = mutableMapOf<DriftingDie, MutableList<Boolean>>()
        val lastCrossing = mutableMapOf<DriftingDie, Int>()
        run(minutes = 20) { drift ->
            for (die in drift.dice) {
                assertTrue(die.vy < 0f && die.vx != 0f, "a die should travel up and to one side: ${die.vx}, ${die.vy}")
                val first = velocities.getOrPut(die to die.crossing) { die.vx to die.vy }
                assertEquals(first, die.vx to die.vy, "a die's path shouldn't bend mid-crossing")
                // Slowly: no crossing's straight-up speed gets it from bottom to top in under ~26 seconds.
                assertTrue(-die.vy * height <= height / 26f + 0.01f, "too fast: ${-die.vy * height} px/s")
                val corners = cornerSpeedPx(die, width, height)
                val travel = travelSpeedPx(die, width, height)
                assertTrue(corners < travel, "corners at $corners px/s outpace the die at $travel px/s")
                assertTrue(die.spinDegrees != 0f, "every die should turn")
                if (lastCrossing[die] != die.crossing) passes.getOrPut(die) { mutableListOf() } += die.vx > 0f
                lastCrossing[die] = die.crossing
            }
        }
        val headings = passes.values.flatten()
        assertTrue(headings.size > 20, "dice should keep coming back, got ${headings.size} passes")
        assertTrue(true in headings && false in headings, "re-entries should sometimes change direction")
        assertTrue(passes.values.any { it.distinct().size == 2 }, "a single die should sometimes come back the other way")
    }
}
