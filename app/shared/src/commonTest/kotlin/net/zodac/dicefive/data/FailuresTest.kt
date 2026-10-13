package net.zodac.dicefive.data

import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class FailuresTest {

    @Test
    fun `a failure is null - its result otherwise - but a cancellation is passed on`() {
        assertEquals(3, orNullOnFailure { 3 })
        assertNull(orNullOnFailure<Int> { error("disk full") })
        assertFailsWith<CancellationException> { orNullOnFailure { throw CancellationException("left the screen") } }
    }
}
