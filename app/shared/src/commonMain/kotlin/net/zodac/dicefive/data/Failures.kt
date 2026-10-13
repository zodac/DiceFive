package net.zodac.dicefive.data

import kotlin.coroutines.cancellation.CancellationException

/**
 * [block]'s result, or null if it threw - except a [CancellationException], which is thrown on so a cancelled
 * coroutine still stops. `runCatching` catches that too, so it is not used around suspending calls.
 */
inline fun <T> orNullOnFailure(block: () -> T): T? =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
