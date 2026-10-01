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
    fun aRabbitStartsInTheMiddleAndStaysThereInAStillUprightHat() {
        val slide = RabbitSlide()
        assertTrue(run(slide, 120) { 0f }.all { it == 0f })
        assertTrue(slide.isAtRest)
    }

    @Test
    fun aShakenHatSlidesItFromEndToEndButNeverOutOfTheOpening() {
        val slide = RabbitSlide()
        // The shake: +-7 degrees, a full swing every 180ms.
        val positions = run(slide, 300) { i -> 7f * sin(i * frame / 1e9f * 2f * kotlin.math.PI.toFloat() / 0.18f) }
        assertTrue(positions.all { abs(it) <= RABBIT_SLIDE_RANGE }, "left the opening: ${positions.maxOf { abs(it) }}")
        assertTrue(positions.max() > 0f && positions.min() < 0f, "never crossed the hat: ${positions.min()}..${positions.max()}")
    }

    @Test
    fun aHatTippedOntoOneSideRollsItToThatSideAndHoldsItThere() {
        // Resting tilt is anticlockwise (negative): the left side is the low one.
        val slide = RabbitSlide()
        run(slide, 300) { -32f }
        assertEquals(-RABBIT_SLIDE_RANGE, slide.x)
        val clockwise = RabbitSlide()
        run(clockwise, 300) { 32f }
        assertEquals(RABBIT_SLIDE_RANGE, clockwise.x)
    }

    @Test
    fun aRabbitLyingInATippedHatComesToRest() {
        // At 60fps a bounce off the low end never slowed below the rest speed, so the hat's frame clock never stopped.
        val slide = RabbitSlide()
        run(slide, 300) { -32f }
        assertTrue(slide.isAtRest, "still moving at ${slide.velocity}")
    }

    @Test
    fun aRabbitShakenThenTippedComesToRest() {
        val slide = RabbitSlide()
        // Shaken (+-7 degrees, a swing every 180ms) for 420ms, then tipped over to -32 degrees across 320ms and left there.
        run(slide, 600) { i ->
            val ms = i * 16f
            when {
                ms < 420f -> 7f * sin(ms / 1000f * 2f * kotlin.math.PI.toFloat() / 0.18f)
                ms < 740f -> -32f * (ms - 420f) / 320f
                else -> -32f
            }
        }
        assertTrue(slide.isAtRest, "still moving at ${slide.velocity}, at ${slide.x}")
    }

    @Test
    fun aLongStalledFrameNeverThrowsItOutEither() {
        val slide = RabbitSlide()
        slide.step(frame, 0f)
        slide.step(frame + 5_000_000_000L, 40f)
        assertTrue(abs(slide.x) <= RABBIT_SLIDE_RANGE)
    }
}
