package net.zodac.dicefive.i18n

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Guards over `strings.xml` in every locale. See `.claude/I18N.md`, Step 1. */
class StringResourcesTest {

    private val locales = StringResourceFiles.all()

    @Test
    fun `every locale's strings pass - no banned word, translations matching the base, and every base plural with a one and an other`() {
        assertTrue("values" in locales, "No values/strings.xml found - is the working directory the module?")
        // The wording the rules ban from anything a player sees: the trademark, and "three/four/five of a kind" (use 3x, 4x, 5x).
        for ((locale, strings) in locales) {
            assertEquals(emptyList(), StringChecks.bannedWords(strings), "$locale uses a banned word - see CLAUDE.md")
        }
        val base = locales.getValue("values")
        assertEquals(emptyList(), locales.filterKeys { it != "values" }.flatMap { (locale, strings) -> StringChecks.translationProblems(locale, base, strings) })
        assertEquals(emptyList(), StringChecks.pluralsWithoutOther(base))
        assertEquals(emptyList(), StringChecks.pluralsWithoutOne(base))
    }

    @Test
    fun `each check finds what it should - banned words, extra keys, lost arguments and a plural without an other`() {
        val found = StringChecks.bannedWords(
            mapOf(
                "a" to "Fine, 3x and 5x",
                "b" to "Roll a Three of a kind",
                "c" to "four-of-a-kind",
                "d" to "FIVE OF A KIND",
                "e" to "${StringChecks.TRADEMARK}!",
            ),
        )
        assertEquals(listOf("b", "c", "d", "e"), found)

        val base = mapOf("greet" to "Hi %1\$s", "rolls[one]" to "%1\$d roll", "rolls[other]" to "%1\$d rolls")
        val translation = mapOf("greet" to "Salut", "rolls[one]" to "%1\$d lancer", "rolls[few]" to "%1\$d lancers", "stray" to "x")
        assertEquals(
            listOf("values-fr: 'greet' is missing [%1\$s] from the base string", "values-fr: 'stray' isn't in the base strings"),
            StringChecks.translationProblems("values-fr", base, translation),
        )

        assertEquals(listOf("plural 'b' has no 'other' item"), StringChecks.pluralsWithoutOther(mapOf("a[one]" to "x", "a[other]" to "y", "b[one]" to "z", "c" to "w")))
    }
}
