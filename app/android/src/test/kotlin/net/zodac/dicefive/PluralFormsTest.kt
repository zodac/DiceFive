package net.zodac.dicefive

import android.icu.text.PluralRules
import android.icu.util.ULocale
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.w3c.dom.Element

/**
 * Every `<plurals>` in every language's strings.xml spells out each form its language's plural rules (CLDR) give a
 * whole number - Russian's "few" and "many", Arabic's "zero" and "two" as well as "one" and "other". A missing form
 * isn't an error anywhere else: Compose resources silently uses "other" instead, so only this
 * catches it. The rules are Android's own ICU's, which is why this is a Robolectric test and not a host one.
 *
 * Counts go up to [LARGEST_COUNT]: a form that only starts at a million (Spanish and French "many") is left out, as no
 * count in the game gets near one.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PluralFormsTest {

    private val resources = File("../shared/src/commonMain/composeResources")

    /** Each language's strings file, by the `common_locale` it declares. */
    private fun stringFiles(): Map<String, Element> =
        resources.listFiles { file -> file.isDirectory && file.name.startsWith("values") }.orEmpty()
            .map { File(it, "strings.xml") }
            .filter { it.exists() }
            .associate { file ->
                val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
                val tag = root.elements("string").first { it.getAttribute("name") == "common_locale" }.textContent
                tag to root
            }

    private fun Element.elements(name: String): List<Element> =
        getElementsByTagName(name).let { nodes -> (0 until nodes.length).map { nodes.item(it) as Element } }

    /**
     * The plural forms [languageTag]'s rules give the counts 0 to [LARGEST_COUNT], and "other" - every plural's fallback,
     * which a language like Russian keeps for fractions only.
     */
    private fun formsFor(languageTag: String): Set<String> {
        val rules = PluralRules.forLocale(ULocale.forLanguageTag(languageTag))
        return (0..LARGEST_COUNT).mapTo(sortedSetOf("other")) { rules.select(it.toDouble()) }
    }

    @Test
    fun `every plural has every form its language uses`() {
        val files = stringFiles()
        assertTrue("Expected English and at least one translation, found ${files.keys}", files.size >= 2)
        val missing = files.flatMap { (tag, root) ->
            val needed = formsFor(tag)
            root.elements("plurals").mapNotNull { plural ->
                val has = plural.elements("item").map { it.getAttribute("quantity") }.toSet()
                (needed - has).takeIf { it.isNotEmpty() }?.let { "$tag ${plural.getAttribute("name")}: no $it" }
            }
        }
        assertTrue("Plurals missing a form their language needs:\n${missing.joinToString("\n")}", missing.isEmpty())
    }

    @Test
    fun `the forms are worked out per language`() {
        // A check that can't pass for the wrong reason: these languages need different forms.
        assertEquals(setOf("one", "other"), formsFor("en-GB"))
        assertEquals(setOf("few", "many", "one", "other"), formsFor("ru"))
        assertEquals(setOf("few", "many", "one", "other", "two", "zero"), formsFor("ar-u-nu-arab"))
    }

    private companion object {
        const val LARGEST_COUNT = 100_000
    }
}
