package net.zodac.dicefive.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.zodac.dicefive.i18n.StringResourceFiles
import net.zodac.dicefive.ui.common.isInSoraFont

/** The mode names and descriptions live in `strings.xml`, so these read every locale's file from disk. */
class GameModeTextTest {

    private val locales = StringResourceFiles.all()

    @Test
    fun `every mode has a name and a description in the base locale`() {
        val base = locales.getValue("values")
        val missing = GameMode.entries.flatMap { listOf("mode_${it.id}", "mode_${it.id}_description") }.filterNot { it in base }

        assertEquals(emptyList(), missing)
    }

    @Test
    fun `every mode name is in the brand font's character set`() {
        // The Leaderboard's mode cards set the name in Sora, which only holds ASCII and Latin-1.
        val outside = locales.flatMap { (locale, strings) ->
            GameMode.entries.mapNotNull { mode -> strings["mode_${mode.id}"]?.takeIf { name -> !name.all { it.isInSoraFont() } }?.let { "$locale $it" } }
        }

        assertTrue(outside.isEmpty(), "Mode names Sora can't draw: $outside")
    }
}
