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
    fun aLongStalledFrameNeverThrowsItOutEither() {
        val slide = RabbitSlide()
        slide.step(frame, 0f)
        slide.step(frame + 5_000_000_000L, 40f)
        assertTrue(abs(slide.x) <= RABBIT_SLIDE_RANGE)
    }
}
