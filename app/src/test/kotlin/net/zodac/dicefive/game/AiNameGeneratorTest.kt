package net.zodac.dicefive.game

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Test

class AiNameGeneratorTest {

    @Test
    fun `generateNames returns the requested count with no duplicates`() {
        val names = AiNameGenerator.generateNames(3, random = Random(1))

        assertEquals(3, names.size)
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun `generateNames returns an empty list for zero players`() {
        assertEquals(emptyList<String>(), AiNameGenerator.generateNames(0))
    }
}
