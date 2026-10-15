package net.zodac.dicefive.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class ScrollRequestsTest {

    @Test
    fun `the latest request waits for a screen not yet open - and is gone once consumed`() = runTest {
        val requests = ScrollRequests<String>()
        assertNull(requests.pending())
        requests.request("first")
        requests.request("second")
        // Only the latest is kept, for a collector that starts later.
        assertEquals("second", requests.pending())
        assertEquals("second", requests.requests.first())
        requests.consumePending()
        assertNull(requests.pending())
    }
}
