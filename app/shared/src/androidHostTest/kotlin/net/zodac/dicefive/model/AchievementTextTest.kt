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
 * The titles live in `strings.xml`, so this reads every locale's file from disk ([StringResourceFiles]) and checks each
 * `achievement_*_title` in each, plus that every
 * achievement and category has its text in the base locale.
 */
class AchievementTextTest {

    private val locales = StringResourceFiles.all()

    /** `locale: key -> title`, for every achievement title in every locale. */
    private val titles = locales.flatMap { (locale, strings) ->
        strings.filterKeys { it.startsWith("achievement_") && it.endsWith("_title") }.map { (key, title) -> Triple(locale, key, title) }
    }

    /** The longest a title may be in [locale]: its own `achievement_title_max_length`, else the base language's. */
    private fun capFor(locale: String): Int =
        (locales[locale]?.get("achievement_title_max_length") ?: locales.getValue("values").getValue("achievement_title_max_length")).toInt()

    @Test
    fun `every achievement and category has its text in the base locale - Easter Eggs, Miscellaneous, Misfortune, and Winning in alphabetical order by title`() {
        val base = locales.getValue("values")
        val missing = Achievement.entries.flatMap { listOf("achievement_${it.name.lowercase()}_title", "achievement_${it.name.lowercase()}_description") } +
            AchievementCategory.entries.map { "category_${it.name.lowercase()}" }
        assertEquals(emptyList(), missing.filterNot { it in base })
        assertEquals(Achievement.entries.size, titles.count { it.first == "values" })

        // Easter Eggs, Miscellaneous, Misfortune, and Winning run alphabetically by title, not easiest-first.
        listOf(AchievementCategory.EASTER_EGGS, AchievementCategory.MISCELLANEOUS, AchievementCategory.MISFORTUNE, AchievementCategory.WINNING).forEach { category ->
            val categoryTitles = Achievement.entries.filter { it.category == category }.map { base.getValue("achievement_${it.name.lowercase()}_title") }
            assertEquals(categoryTitles.sortedBy { it.lowercase() }, categoryTitles, category.name)
        }
    }

    @Test
    fun `every title fits the banner's one line in its own language - trimmed and in the brand font - the longest setting the cap`() {
        // The base language's cap is the one the app is built around, and the longest title is the one that sets it.
        assertEquals(MAX_ACHIEVEMENT_TITLE_LENGTH, capFor("values"))
        assertEquals(MAX_ACHIEVEMENT_TITLE_LENGTH, titles.filter { it.first == "values" }.maxOf { it.third.length })

        val tooLong = titles.filter { it.third.length > capFor(it.first) }
        assertTrue(tooLong.isEmpty(), "Titles over their language's cap: ${tooLong.map { "${it.first} ${it.third} (${it.third.length} > ${capFor(it.first)})" }}")
        val untrimmed = titles.filter { it.third != it.third.trim() }
        assertTrue(untrimmed.isEmpty(), "Untrimmed titles: ${untrimmed.map { it.third }}")
        // The banner sets its title in Sora, which only holds ASCII and Latin-1 - anything else would draw in a second face.
        val outside = titles.filter { it.first !in StringChecks.localesOutsideSora }.filterNot { it.third.all { c -> c.isInSoraFont() } }
        assertTrue(outside.isEmpty(), "Titles Sora can't draw: ${outside.map { "${it.first} ${it.third}" }}")
    }

    @Test
    fun `a description's backticks are paired, so its highlighted mode never swallows the rest of the line`() {
        val unpaired = locales.flatMap { (locale, strings) ->
            strings.filterKeys { it.startsWith("achievement_") && it.endsWith("_description") }
                .filterValues { description -> description.count { it == '`' } % 2 != 0 }
                .map { "$locale ${it.key}" }
        }
        assertEquals(emptyList(), unpaired)
    }
}
