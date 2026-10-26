package net.zodac.dicefive.i18n

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.zodac.dicefive.model.AchievementCategory
import net.zodac.dicefive.ui.common.isInSoraFont

/** The Achievements and Styles category headers are set in Sora (`categoryHeaderStyle`). Read from disk, like the other i18n guards. */
class CategoryHeaderTextTest {

    private val locales = StringResourceFiles.all()

    // Every Styles category title; the count shares the prefix but is digits.
    private val styleCategoryKeys = locales.getValue("values").keys.filter { it.startsWith("styles_category_") && it != "styles_category_count" }

    private val keys = AchievementCategory.entries.map { it.label.key } + styleCategoryKeys

    @Test
    fun `every category header is in the brand font's character set`() {
        assertEquals(5, styleCategoryKeys.size, "Styles categories: $styleCategoryKeys")

        // Sora only holds ASCII and Latin-1 - anything else would draw in a second face.
        val outside = locales.filterKeys { it !in StringChecks.localesOutsideSora }.flatMap { (locale, strings) ->
            keys.mapNotNull { key -> strings[key]?.takeIf { name -> !name.all { it.isInSoraFont() } }?.let { "$locale $it" } }
        }

        assertTrue(outside.isEmpty(), "Category headers Sora can't draw: $outside")
    }
}
