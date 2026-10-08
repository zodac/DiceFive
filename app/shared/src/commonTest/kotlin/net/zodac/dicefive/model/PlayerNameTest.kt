package net.zodac.dicefive.model

import kotlin.test.Test
import kotlin.test.assertEquals

class PlayerNameTest {

    private val stackedMarks = "́̂̃̄̅̆̇̈̉̊"

    @Test
    fun `ordinary names in many scripts come through untouched`() {
        listOf(
            "Alexandra the Great", "José", "Zoë Müller", "Nguyễn Văn", "Åsa", "Łukasz", "Иван", "Ελένη", "李小龍", "ひらがな", "한국어",
            "محمد", "كتابَ", "שָׁלוֹם", "ภาษาไทย", "नमस्ते", "தமிழ்", "Ana 😀", "👨‍👩‍👧", "🇮🇪", "1️⃣", "می‌خواهم",
        ).forEach { assertEquals(it, sanitizePlayerName(it), it) }
    }

    @Test
    fun `stacked combining marks are cut back to a few per character`() {
        val glitch = "x" + stackedMarks + "q" + stackedMarks
        assertEquals("x\u0301\u0302\u0303\u0304q\u0301\u0302\u0303\u0304", sanitizePlayerName(glitch))
        // Ten thousand marks on one letter is a short name in the end.
        assertEquals(1 + MAX_MARKS_PER_CHARACTER, sanitizePlayerName("x" + stackedMarks.repeat(1000)).length)
    }

    @Test
    fun `a mark with nothing to sit on is dropped`() {
        assertEquals("Bob", sanitizePlayerName("́̂Bob"))
        assertEquals("a b", sanitizePlayerName("a ́b"))
    }

    @Test
    fun `the bidirectional overrides and isolates and the invisible characters are removed`() {
        assertEquals("Bob", sanitizePlayerName("‮Bob‬"))
        assertEquals("Bob", sanitizePlayerName("⁦Bo⁩b"))
        assertEquals("Bob", sanitizePlayerName("B​o⁠b﻿­"))
        assertEquals("Bob", sanitizePlayerName("B؜ob�￼"))
        assertEquals("", sanitizePlayerName("ㅤﾠᅟ​"))
    }

    @Test
    fun `joiners and direction marks stay when they follow a letter`() {
        assertEquals("می‌خواهم", sanitizePlayerName("می‌خواهم"))
        assertEquals("abc‏", sanitizePlayerName("abc‏"))
        assertEquals("", sanitizePlayerName("‍‌"))
    }

    @Test
    fun `control characters go and any whitespace becomes one space`() {
        assertEquals("Bob Smith", sanitizePlayerName("Bob\n\t\r Smith"))
        assertEquals("Bob Smith", sanitizePlayerName("Bob 　Smith"))
        assertEquals("Bob", sanitizePlayerName("B\u0000o\u0007b\u007F\u0085"))
        // Not trimmed: the second word is still to be typed.
        assertEquals("Bob ", sanitizePlayerName("Bob "))
    }

    @Test
    fun `private use and unpaired surrogates and non-characters go`() {
        assertEquals("Bob", sanitizePlayerName("Bo\uD800b\uDC00﷐￿"))
        assertEquals("Bob", sanitizePlayerName("Bob󰀀"))
    }

    @Test
    fun `a flag's tag characters stay but only so many`() {
        val england = "🏴󠁧󠁢󠁥󠁮󠁧󠁿"
        assertEquals(england, sanitizePlayerName(england))
        assertEquals(2 + 8 * 2, sanitizePlayerName("🏴" + "󠁧".repeat(100)).length)
    }

    @Test
    fun `the length is bounded whatever the name is made of`() {
        assertEquals(MAX_PLAYER_NAME_CODE_POINTS, sanitizePlayerName("a".repeat(100_000)).length)
        assertEquals(MAX_PLAYER_NAME_CODE_POINTS * 2, sanitizePlayerName("😀".repeat(100_000)).length)
    }

    @Test
    fun `a name is put in composed form`() {
        assertEquals("é", sanitizePlayerName("é"))
        assertEquals("ệ", sanitizePlayerName("ệ"))
        assertEquals("가", sanitizePlayerName("가"))
    }

    @Test
    fun `sanitising twice is the same as once`() {
        listOf("Z" + stackedMarks, "‮ a \n b ​", "é́", "x‍‍‍y", " a  b ").forEach {
            assertEquals(sanitizePlayerName(it), sanitizePlayerName(sanitizePlayerName(it)), it)
        }
    }

    @Test
    fun `names that look the same have the same key`() {
        assertEquals(playerNameKey("José"), playerNameKey("José "))
        assertEquals(playerNameKey("BOB"), playerNameKey("b​ob"))
        assertEquals("", playerNameKey("​ ㅤ"))
    }

    @Test
    fun `a name cut to a width keeps its marks`() {
        val name = sanitizePlayerName("ệ".repeat(3) + "abc")
        assertEquals("ệệ", name.takeNameWidth(2).take(2))
    }
}
