package net.zodac.dicefive.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The unlock banner shows a title on one line and never wraps it - see [MAX_ACHIEVEMENT_TITLE_LENGTH].
 * A title that outgrows the cap fails the build here rather than showing up cut off in a real banner.
 */
class AchievementTextTest {

    @Test
    fun `every title fits the banner's one line`() {
        val tooLong = Achievement.entries.filter { it.title.length > MAX_ACHIEVEMENT_TITLE_LENGTH }
        assertTrue(tooLong.isEmpty(), "Titles over $MAX_ACHIEVEMENT_TITLE_LENGTH characters: ${tooLong.map { "${it.title} (${it.title.length})" }}")
    }

    @Test
    fun `no title starts or ends with a space`() {
        val untrimmed = Achievement.entries.filter { it.title != it.title.trim() }
        assertTrue(untrimmed.isEmpty(), "Untrimmed titles: ${untrimmed.map { it.title }}")
    }

    @Test
    fun `the longest title is the one that sets the cap`() {
        assertEquals(MAX_ACHIEVEMENT_TITLE_LENGTH, Achievement.entries.maxOf { it.title.length })
    }
}
