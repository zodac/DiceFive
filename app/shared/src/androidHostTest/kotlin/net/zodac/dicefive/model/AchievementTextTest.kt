package net.zodac.dicefive.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.zodac.dicefive.i18n.StringChecks
import net.zodac.dicefive.i18n.StringResourceFiles
import net.zodac.dicefive.ui.common.isInSoraFont

/**
 * The unlock banner shows a title on one line and never wraps it - see [MAX_ACHIEVEMENT_TITLE_LENGTH].
 * A title that outgrows the cap fails the build here rather than showing up cut off in a real banner.
 *
 * The titles live in `strings.xml`, so this reads every locale's file from disk (`:app:shared`'s host
 * tests can't resolve a resource) and checks each `achievement_*_title` in each, plus that every
 * achievement and category has its text in the base locale.
 */
class AchievementTextTest {

    private val locales = StringResourceFiles.all()

    /** `locale: key -> title`, for every achievement title in every locale. */
    private val titles = locales.flatMap { (locale, strings) ->
        strings.filterKeys { it.startsWith("achievement_") && it.endsWith("_title") }.map { (key, title) -> Triple(locale, key, title) }
    }

    @Test
    fun `every achievement and category has its text in the base locale`() {
        val base = locales.getValue("values")
        val missing = Achievement.entries.flatMap { listOf("achievement_${it.name.lowercase()}_title", "achievement_${it.name.lowercase()}_description") } +
            AchievementCategory.entries.map { "category_${it.name.lowercase()}" }

        assertEquals(emptyList(), missing.filterNot { it in base })
        assertEquals(Achievement.entries.size, titles.count { it.first == "values" })
    }

    /** The longest a title may be in [locale]: its own `achievement_title_max_length`, else the base language's. */
    private fun capFor(locale: String): Int =
        (locales[locale]?.get("achievement_title_max_length") ?: locales.getValue("values").getValue("achievement_title_max_length")).toInt()

    @Test
    fun `the base language's cap is the one the app is built around`() {
        assertEquals(MAX_ACHIEVEMENT_TITLE_LENGTH, capFor("values"))
    }

    @Test
    fun `every title fits the banner's one line in its own language`() {
        val tooLong = titles.filter { it.third.length > capFor(it.first) }
        assertTrue(tooLong.isEmpty(), "Titles over their language's cap: ${tooLong.map { "${it.first} ${it.third} (${it.third.length} > ${capFor(it.first)})" }}")
    }

    @Test
    fun `no title starts or ends with a space`() {
        val untrimmed = titles.filter { it.third != it.third.trim() }
        assertTrue(untrimmed.isEmpty(), "Untrimmed titles: ${untrimmed.map { it.third }}")
    }

    @Test
    fun `the longest title is the one that sets the cap`() {
        assertEquals(MAX_ACHIEVEMENT_TITLE_LENGTH, titles.filter { it.first == "values" }.maxOf { it.third.length })
    }

    @Test
    fun `every title is in the brand font's character set`() {
        // The banner sets its title in Sora, which only holds ASCII and Latin-1 - anything else would draw in a second face.
        val outside = titles.filter { it.first !in StringChecks.localesOutsideSora }.filterNot { it.third.all { c -> c.isInSoraFont() } }
        assertTrue(outside.isEmpty(), "Titles Sora can't draw: ${outside.map { "${it.first} ${it.third}" }}")
    }

    /** Easter Eggs have no ladder to run easiest-first, so they run alphabetically by title instead. */
    @Test
    fun `Easter Eggs are in alphabetical order by title`() {
        val base = locales.getValue("values")
        val titles = Achievement.entries.filter { it.category == AchievementCategory.EASTER_EGGS }.map { base.getValue("achievement_${it.name.lowercase()}_title") }

        assertEquals(titles.sortedBy { it.lowercase() }, titles)
    }
}
