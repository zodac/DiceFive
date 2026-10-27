package net.zodac.dicefive.game

/**
 * The hidden hold sequence: every die held then released, the first die's pair first through the last's, on any turn and
 * any player's. A toggle out of order starts it again - from that toggle, when it's the first die being held.
 * [net.zodac.dicefive.model.Achievement.TIME_WASTING]'s, and in a debug build superuser mode's.
 */
class HoldSequence {

    /** The die whose hold-then-release pair is next. */
    private var dieIndex = 0

    /** Whether [dieIndex] is held, and its release comes next. */
    private var awaitingRelease = false

    /** A toggle of the die at [index], before it's applied: [holding] it, or releasing it. Returns whether that finished the sequence over [dieCount] dice. */
    fun onToggle(index: Int, holding: Boolean, dieCount: Int): Boolean {
        if (index != dieIndex || holding == awaitingRelease) {
            reset()
            if (index == 0 && holding) awaitingRelease = true
            return false
        }
        if (!awaitingRelease) {
            awaitingRelease = true
            return false
        }
        dieIndex++
        awaitingRelease = false
        if (dieIndex < dieCount) return false
        reset()
        return true
    }

    /** Back to the start: nothing of the sequence done. */
    fun reset() {
        dieIndex = 0
        awaitingRelease = false
    }
}
