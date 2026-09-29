package net.zodac.dicefive.ui.game.style

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DieMotionTest {

    private val frameNanos = 16_000_000L

    /** Moves [motion] frame by frame to wherever [place] says the die is at each frame, from frame 1. */
    private fun drive(motion: DieMotion, frames: Int, yawDegrees: Float = 0f, place: (Int) -> Offset): Boolean {
        var moving = true
        for (frame in 1..frames) {
            motion.moveTo(place(frame), yawDegrees)
            moving = motion.step(frame * frameNanos)
        }
        return moving
    }

    private fun startedAt(centre: Offset, yawDegrees: Float = 0f): DieMotion = DieMotion(seed = 1, travel = 0.05f).apply {
        moveTo(centre, yawDegrees)
        step(0L)
    }

    @Test
    fun aDieSpeedingUpToTheRightThrowsItsPupilsLeft() {
        val motion = startedAt(Offset.Zero)
        drive(motion, frames = 30) { frame ->
            val seconds = frame * 0.016f
            Offset(10f * seconds * seconds, 0f)
        }
        assertTrue(motion.pupils.all { it.x < -0.5f && it.getDistance() > 0.99f }, "${motion.pupils}")
    }

    @Test
    fun pupilsNeverLeaveTheirSockets() {
        val motion = startedAt(Offset.Zero)
        drive(motion, frames = 60) { frame -> Offset(if (frame % 10 < 5) frame * 0.2f else 0f, frame * 0.1f) }
        assertTrue(motion.pupils.all { it.getDistance() <= 1.0001f }, "${motion.pupils}")
    }

    @Test
    fun pupilsAreThrownInTheDiesOwnFrame() {
        // Turned a quarter clockwise, the die's own "down" points left across the mat - so speeding
        // up to the right throws its pupils down its own face.
        val motion = startedAt(Offset.Zero, yawDegrees = 90f)
        drive(motion, frames = 30, yawDegrees = 90f) { frame ->
            val seconds = frame * 0.016f
            Offset(10f * seconds * seconds, 0f)
        }
        assertTrue(motion.pupils.all { it.y > 0.5f && it.getDistance() > 0.99f }, "${motion.pupils}")
    }

    @Test
    fun pupilsSlideDownAFaceTippingOver() {
        // Tipping away over its top edge raises its near edge, so its pupils slide to the top.
        val motion = startedAt(Offset.Zero)
        for (frame in 1..30) {
            motion.moveTo(Offset.Zero, 0f, tipDegrees = 40f)
            motion.step(frame * frameNanos)
        }
        assertTrue(motion.pupils.all { it.y < -0.5f && it.getDistance() > 0.99f }, "${motion.pupils}")
    }

    @Test
    fun aDieBeingPutSomewhereElseDoesNotThrowItsPupils() {
        val motion = startedAt(Offset.Zero)
        val before = motion.pupils
        drive(motion, frames = 3) { Offset(5f, 3f) }
        assertEquals(before, motion.pupils)
    }

    @Test
    fun itStopsOnceTheDieAndItsPupilsSettle() {
        val motion = startedAt(Offset.Zero)
        drive(motion, frames = 10) { frame -> Offset(frame * 0.1f, 0f) }
        var frame = 10
        var moving = true
        while (moving && frame < 400) {
            frame++
            moving = motion.step(frame * frameNanos)
        }
        assertFalse(moving)
        val settled = motion.pupils
        motion.step((frame + 1) * frameNanos)
        assertEquals(settled, motion.pupils)
    }

    @Test
    fun movingWakesItAndStandingStillDoesNot() {
        val motion = DieMotion(seed = 1, travel = 0.05f)
        motion.moveTo(Offset(1f, 1f), 0f)
        assertFalse(motion.awake)
        motion.moveTo(Offset(1f, 1f), 0f)
        assertFalse(motion.awake)
        motion.moveTo(Offset(1.1f, 1f), 0f)
        assertTrue(motion.awake)
    }

    /** Steps [motion] on from [from] until it stops or [limit] frames pass; returns the frame it stopped on, or null. */
    private fun runUntilStill(motion: DieMotion, from: Int = 1, limit: Int = 2_000): Int? {
        for (frame in from until from + limit) {
            if (!motion.step(frame * frameNanos)) return frame
        }
        return null
    }

    @Test
    fun aPhoneHeldUprightSettlesThePupilsAtTheBottomOfTheirSockets() {
        val motion = startedAt(Offset.Zero)
        motion.feel(Offset(0f, 1f))

        assertTrue(motion.awake)
        val stoppedAt = runUntilStill(motion)
        assertTrue(stoppedAt != null, "the pupils should come to rest under a steady pull, not jitter forever")
        assertTrue(motion.pupils.all { it.y > 0.95f }, "${motion.pupils}")
    }

    @Test
    fun tippingThePhoneRightRollsThePupilsRight() {
        val motion = startedAt(Offset.Zero)
        motion.feel(Offset(1f, 0f))
        runUntilStill(motion)

        assertTrue(motion.pupils.all { it.x > 0.95f }, "${motion.pupils}")
    }

    @Test
    fun theDevicePullIsTurnedIntoATiltedDiesOwnFrame() {
        // Turned a quarter clockwise, the die's own "down" points left on screen - so a pull straight
        // down the screen sends its pupils towards its own right-hand side (+x in its frame).
        val motion = startedAt(Offset.Zero, yawDegrees = 90f)
        motion.feel(Offset(0f, 1f))
        runUntilStill(motion)

        assertTrue(motion.pupils.all { it.x > 0.95f }, "${motion.pupils}")
    }

    @Test
    fun onlyAPullThatChangesEnoughWakesSettledPupils() {
        val motion = startedAt(Offset.Zero)
        motion.feel(Offset(0f, 1f))
        assertTrue(runUntilStill(motion) != null)
        motion.settle() // As follow() does once the pupils stop, putting it back to sleep.

        motion.feel(Offset(0.01f, 1f))
        assertFalse(motion.awake, "sensor noise on a still phone shouldn't keep the pupils going")
        motion.feel(Offset(0.3f, 1f))
        assertTrue(motion.awake)
    }
}
