package net.zodac.dicefive.i18n

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.zodac.dicefive.ui.game.style.StyleCatalogs
import org.jetbrains.compose.resources.StringResource

/**
 * Every style, colour and category name the Styles screen shows has its text in strings.xml. Read from disk, like the
 * other i18n guards.
 */
class StyleNamesTest {

    private val locales = StringResourceFiles.all()

    private val resources: List<StringResource> = StyleCatalogs.flatMap { catalog ->
        listOf(catalog.noun, catalog.variantNoun) + catalog.families.flatMap { family -> listOf(family.name) + family.colours.map { it.name } }
    }.distinctBy { it.key }

    @Test
    fun `every name has its text in the base locale`() {
        val base = locales.getValue("values")

        assertEquals(emptyList(), resources.map { it.key }.filterNot { it in base })
        assertTrue(resources.size > 150, "Only ${resources.size} style names found")
    }
}
