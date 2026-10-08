package net.zodac.dicefive.ui.game.style

import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RabbitSlideTest {

    private val frame = 16_000_000L

    /** Runs [slide] for [frames] frames at 60fps with the hat at [angleAt] (degrees) each one, returning every position. */
    private fun run(slide: RabbitSlide, frames: Int, angleAt: (Int) -> Float): List<Float> = (1..frames).map { i ->
        slide.step(i * frame, angleAt(i))
        slide.x
    }

    @Test
    fun aRabbitStaysMidHatWhenStillIsShakenEndToEndRollsToTheLowSideAndNeverLeavesTheOpening() {
        val still = RabbitSlide()
        assertTrue(run(still, 120) { 0f }.all { it == 0f })
        assertTrue(still.isAtRest)

        // The shake: +-7 degrees, a full swing every 180ms.
        val shaken = run(RabbitSlide(), 300) { i -> 7f * sin(i * frame / 1e9f * 2f * kotlin.math.PI.toFloat() / 0.18f) }
        assertTrue(shaken.all { abs(it) <= RABBIT_SLIDE_RANGE }, "left the opening: ${shaken.maxOf { abs(it) }}")
        assertTrue(shaken.max() > 0f && shaken.min() < 0f, "never crossed the hat: ${shaken.min()}..${shaken.max()}")

        // Resting tilt is anticlockwise (negative): the left side is the low one.
        val anticlockwise = RabbitSlide()
        run(anticlockwise, 300) { -32f }
        assertEquals(-RABBIT_SLIDE_RANGE, anticlockwise.x)
        val clockwise = RabbitSlide()
        run(clockwise, 300) { 32f }
        assertEquals(RABBIT_SLIDE_RANGE, clockwise.x)

        // A long stalled frame never throws it out either.
        val stalled = RabbitSlide()
        stalled.step(frame, 0f)
        stalled.step(frame + 5_000_000_000L, 40f)
        assertTrue(abs(stalled.x) <= RABBIT_SLIDE_RANGE)
    }

    @Test
    fun aRabbitLyingInATippedHatComesToRestEvenAfterAShake() {
        // At 60fps a bounce off the low end never slowed below the rest speed, so the hat's frame clock never stopped.
        val tipped = RabbitSlide()
        run(tipped, 300) { -32f }
        assertTrue(tipped.isAtRest, "still moving at ${tipped.velocity}")

        // Shaken (+-7 degrees, a swing every 180ms) for 420ms, then tipped over to -32 degrees across 320ms and left there.
        val shakenThenTipped = RabbitSlide()
        run(shakenThenTipped, 600) { i ->
            val ms = i * 16f
            when {
                ms < 420f -> 7f * sin(ms / 1000f * 2f * kotlin.math.PI.toFloat() / 0.18f)
                ms < 740f -> -32f * (ms - 420f) / 320f
                else -> -32f
            }
        }
        assertTrue(shakenThenTipped.isAtRest, "still moving at ${shakenThenTipped.velocity}, at ${shakenThenTipped.x}")
    }
}
