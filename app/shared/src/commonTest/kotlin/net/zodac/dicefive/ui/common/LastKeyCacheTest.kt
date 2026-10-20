package net.zodac.dicefive.ui.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class LastKeyCacheTest {

    @Test
    fun `the same key gets the value already built - a new key builds a new one and replaces it`() {
        val cache = LastKeyCache<String, StringBuilder>()
        var built = 0
        fun get(key: String) = cache.get(key) { built++; StringBuilder(it) }

        val en = get("en")
        assertSame(en, get("en"))
        assertEquals(1, built)

        val es = get("es")
        assertEquals("es", es.toString())
        assertEquals(2, built)

        // Only the last key is kept.
        assertEquals("en", get("en").toString())
        assertEquals(3, built)
    }
}
