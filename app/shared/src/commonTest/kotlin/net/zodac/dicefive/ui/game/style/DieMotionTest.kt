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

    /** Steps [motion] on from [from] until it stops or [limit] frames pass; returns the frame it stopped on, or null. */
    private fun runUntilStill(motion: DieMotion, from: Int = 1, limit: Int = 2_000): Int? {
        for (frame in from until from + limit) {
            if (!motion.step(frame * frameNanos)) return frame
        }
        return null
    }

    @Test
    fun theDiesMovementThrowsItsPupilsInItsOwnFrameWithinTheirSocketsUntilBothSettle() {
        // A die speeding up to the right throws its pupils left.
        val speeding = startedAt(Offset.Zero)
        drive(speeding, frames = 30) { frame -> Offset(10f * (frame * 0.016f) * (frame * 0.016f), 0f) }
        assertTrue(speeding.pupils.all { it.x < -0.5f && it.getDistance() > 0.99f }, "${speeding.pupils}")

        // Pupils never leave their sockets.
        val jerky = startedAt(Offset.Zero)
        drive(jerky, frames = 60) { frame -> Offset(if (frame % 10 < 5) frame * 0.2f else 0f, frame * 0.1f) }
        assertTrue(jerky.pupils.all { it.getDistance() <= 1.0001f }, "${jerky.pupils}")

        // They're thrown in the die's own frame: turned a quarter clockwise, its own "down" points left across the mat - so
        // speeding up to the right throws its pupils down its own face.
        val turned = startedAt(Offset.Zero, yawDegrees = 90f)
        drive(turned, frames = 30, yawDegrees = 90f) { frame -> Offset(10f * (frame * 0.016f) * (frame * 0.016f), 0f) }
        assertTrue(turned.pupils.all { it.y > 0.5f && it.getDistance() > 0.99f }, "${turned.pupils}")

        // Tipping away over its top edge raises its near edge, so its pupils slide to the top.
        val tipping = startedAt(Offset.Zero)
        for (frame in 1..30) {
            tipping.moveTo(Offset.Zero, 0f, tipDegrees = 40f)
            tipping.step(frame * frameNanos)
        }
        assertTrue(tipping.pupils.all { it.y < -0.5f && it.getDistance() > 0.99f }, "${tipping.pupils}")

        // A die being put somewhere else doesn't throw them.
        val placed = startedAt(Offset.Zero)
        val before = placed.pupils
        drive(placed, frames = 3) { Offset(5f, 3f) }
        assertEquals(before, placed.pupils)

        // It stops once the die and its pupils settle.
        val settling = startedAt(Offset.Zero)
        drive(settling, frames = 10) { frame -> Offset(frame * 0.1f, 0f) }
        val stoppedAt = runUntilStill(settling, from = 11, limit = 390)
        assertTrue(stoppedAt != null)
        val settled = settling.pupils
        settling.step((stoppedAt + 1) * frameNanos)
        assertEquals(settled, settling.pupils)

        // Moving wakes it, and standing still doesn't.
        val sleeping = DieMotion(seed = 1, travel = 0.05f)
        sleeping.moveTo(Offset(1f, 1f), 0f)
        assertFalse(sleeping.awake)
        sleeping.moveTo(Offset(1f, 1f), 0f)
        assertFalse(sleeping.awake)
        sleeping.moveTo(Offset(1.1f, 1f), 0f)
        assertTrue(sleeping.awake)
    }

    @Test
    fun theDevicesPullRollsThePupilsToRestInTheDiesOwnFrameAndOnlyARealChangeWakesThem() {
        // A phone held upright settles the pupils at the bottom of their sockets - coming to rest under a steady pull, not
        // jittering forever.
        val upright = startedAt(Offset.Zero)
        upright.feel(Offset(0f, 1f))
        assertTrue(upright.awake)
        assertTrue(runUntilStill(upright) != null, "the pupils should come to rest under a steady pull, not jitter forever")
        assertTrue(upright.pupils.all { it.y > 0.95f }, "${upright.pupils}")

        // Tipping the phone right rolls them right.
        val tippedRight = startedAt(Offset.Zero)
        tippedRight.feel(Offset(1f, 0f))
        runUntilStill(tippedRight)
        assertTrue(tippedRight.pupils.all { it.x > 0.95f }, "${tippedRight.pupils}")

        // Turned a quarter clockwise, the die's own "down" points left on screen - so a pull straight down the screen sends
        // its pupils towards its own right-hand side (+x in its frame).
        val turned = startedAt(Offset.Zero, yawDegrees = 90f)
        turned.feel(Offset(0f, 1f))
        runUntilStill(turned)
        assertTrue(turned.pupils.all { it.x > 0.95f }, "${turned.pupils}")

        // Only a pull that changes enough wakes settled pupils.
        upright.settle() // As follow() does once the pupils stop, putting it back to sleep.
        upright.feel(Offset(0.01f, 1f))
        assertFalse(upright.awake, "sensor noise on a still phone shouldn't keep the pupils going")
        upright.feel(Offset(0.3f, 1f))
        assertTrue(upright.awake)
    }
}
