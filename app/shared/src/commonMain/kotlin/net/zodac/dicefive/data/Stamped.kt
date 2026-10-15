package net.zodac.dicefive.data

/**
 * [value] as it was set at [atEpochMillis] - what the cloud save's last-writer-wins merges compare. 0 means never
 * stamped, which loses to any value that was.
 */
data class Stamped<out T>(val value: T, val atEpochMillis: Long)

/**
 * Whichever of [a] and [b] was set later. On a tie, a value beats a null - so a setting chosen before stamps existed
 * (stamped 0) beats one never chosen - and otherwise the one [tieBreak] ranks higher, so either order gives the same.
 */
internal fun <T> newerOf(a: Stamped<T>, b: Stamped<T>, tieBreak: (T) -> String): Stamped<T> = when {
    a.atEpochMillis != b.atEpochMillis -> if (a.atEpochMillis > b.atEpochMillis) a else b
    (a.value == null) != (b.value == null) -> if (a.value != null) a else b
    else -> if (tieBreak(a.value) >= tieBreak(b.value)) a else b
}
