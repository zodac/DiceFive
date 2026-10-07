package net.zodac.dicefive.i18n

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Guards over `strings.xml` in every locale. See `.claude/I18N.md`, Step 1. */
class StringResourcesTest {

    private val locales = StringResourceFiles.all()

    @Test
    fun `the base strings exist`() {
        assertTrue("values" in locales, "No values/strings.xml found - is the working directory the module?")
    }

    @Test
    fun `no locale uses a banned word`() {
        // The wording the rules ban from anything a player sees: the trademark, and "three/four/five of a kind" (use 3x, 4x, 5x).
        for ((locale, strings) in locales) {
            assertEquals(emptyList(), StringChecks.bannedWords(strings), "$locale uses a banned word - see CLAUDE.md")
        }
    }

    @Test
    fun `every translation matches the base strings`() {
        val base = locales.getValue("values")
        val problems = locales.filterKeys { it != "values" }.flatMap { (locale, strings) -> StringChecks.translationProblems(locale, base, strings) }
        assertEquals(emptyList(), problems)
    }

    @Test
    fun `every base plural has an other item`() {
        assertEquals(emptyList(), StringChecks.pluralsWithoutOther(locales.getValue("values")))
    }

    @Test
    fun `the banned-word check finds what it should`() {
        val found = StringChecks.bannedWords(
            mapOf(
                "a" to "Fine, 3x and 5x",
                "b" to "Roll a Three of a kind",
                "c" to "four-of-a-kind",
                "d" to "FIVE OF A KIND",
                "e" to "yahtzee!",
            ),
        )

        assertEquals(listOf("b", "c", "d", "e"), found)
    }

    @Test
    fun `the translation check finds extra keys and lost arguments`() {
        val base = mapOf("greet" to "Hi %1\$s", "rolls[one]" to "%1\$d roll", "rolls[other]" to "%1\$d rolls")
        val translation = mapOf(
            "greet" to "Salut",
            "rolls[one]" to "%1\$d lancer",
            "rolls[few]" to "%1\$d lancers",
            "stray" to "x",
        )

        val problems = StringChecks.translationProblems("values-fr", base, translation)

        assertEquals(
            listOf("values-fr: 'greet' is missing [%1\$s] from the base string", "values-fr: 'stray' isn't in the base strings"),
            problems,
        )
    }

    @Test
    fun `the plural check finds a missing other`() {
        val problems = StringChecks.pluralsWithoutOther(mapOf("a[one]" to "x", "a[other]" to "y", "b[one]" to "z", "c" to "w"))

        assertEquals(listOf("plural 'b' has no 'other' item"), problems)
    }
}
