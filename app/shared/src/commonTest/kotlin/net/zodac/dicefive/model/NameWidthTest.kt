package net.zodac.dicefive.model

import kotlin.test.Test
import kotlin.test.assertEquals

class NameWidthTest {

    @Test
    fun `a name's width counts full-width characters twice and marks not at all - and a cut never lands inside a character`() {
        // Latin letters are one each - so the cap is unchanged for them.
        assertEquals(19, "Alexandra the Great".nameWidth())
        assertEquals("Alexandra the", "Alexandra the Great".takeNameWidth(13))
        assertEquals("Ann", "Ann".takeNameWidth(12))

        // Full-width characters take two - six fit where twelve latin ones do.
        assertEquals("一二三四五六", "一二三四五六七八".takeNameWidth(12))
        assertEquals("ひらがな", "ひらがなです".takeNameWidth(8))
        assertEquals("한국어", "한국어이름".takeNameWidth(6))

        // An accent or a vowel mark takes no room of its own.
        assertEquals(5, "Josés".nameWidth()) // "Josés" with the accent as a separate mark
        assertEquals(4, "كتابَ".nameWidth()) // an Arabic word with a fatha

        // A cut never lands inside a character.
        val name = "ab😀cd" // the emoji is two UTF-16 units and two wide
        assertEquals("ab", name.takeNameWidth(3))
        assertEquals("ab😀", name.takeNameWidth(4))
    }
}
