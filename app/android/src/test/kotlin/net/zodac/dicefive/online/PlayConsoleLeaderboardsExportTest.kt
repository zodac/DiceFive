package net.zodac.dicefive.online

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.ui.theme.DiceFiveTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Play Console's side of the leaderboards, one per [GameMode] in New Game order, which are made there by hand (it has
 * no import for them): the rules each keeps to there, checked on every run, and (with `-PexportPlayConsole`, which
 * `scripts/export-play-console.sh` passes) the sheet to make them from and their icons, written to the gitignored
 * `play-console/` folder.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w600dp-h900dp-mdpi")
class PlayConsoleLeaderboardsExportTest {

    @get:Rule
    val compose = createComposeRule()

    private val modes = GameMode.entries

    @Test
    fun `names fit Play's limit in every language - and keys are distinct`() {
        for (locale in LOCALES) {
            val strings = stringsOf(locale)
            for (mode in modes) {
                val name = strings.getValue(mode.displayName.key)
                assertTrue("$locale $mode", name.isNotBlank() && name.length <= MAX_NAME_LENGTH)
            }
        }
        assertEquals(modes.size, modes.map { it.onlineLeaderboardKey }.toSet().size)
    }

    @Test
    fun `every mode has an icon of its own`() {
        var shown by mutableStateOf(modes.first())
        compose.setContent { IconTile(shown, CHECK_SIZE) }
        val drawn = modes.associateWith { mode ->
            shown = mode
            compose.waitForIdle()
            val bitmap = compose.onNodeWithTag(ICON_TAG).captureToImage().asAndroidBitmap()
            IntArray(bitmap.width * bitmap.height).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }.toList()
        }
        val sameIcon = drawn.entries.groupBy({ it.value }, { it.key }).values.filter { it.size > 1 }
        assertEquals("Modes sharing an icon", emptyList<List<GameMode>>(), sameIcon)
    }

    /** A mode's Play icon: its glyph on the achievements' gold tile, [size] square. */
    @Composable
    private fun IconTile(mode: GameMode, size: Int) {
        DiceFiveTheme {
            Box(Modifier.size(size.dp).background(MaterialTheme.colorScheme.primaryContainer).testTag(ICON_TAG), contentAlignment = Alignment.Center) {
                Image(
                    painter = rememberVectorPainter(mode.leaderboardIcon),
                    contentDescription = null,
                    modifier = Modifier.size((size * GLYPH_SIZE / ICON_SIZE).dp),
                )
            }
        }
    }

    @Test
    fun `export the sheet and icons for Play Console`() {
        assumeTrue("Only with -PexportPlayConsole", exportingPlayConsole)
        val dir = playConsoleDir()
        val icons = dir.resolve("leaderboard-icons").apply { deleteRecursively(); mkdirs() }
        writeSheet(dir.resolve("leaderboards.csv"))

        var shown by mutableStateOf(modes.first())
        compose.setContent { IconTile(shown, ICON_SIZE) }
        for (mode in modes) {
            shown = mode
            compose.waitForIdle()
            val bitmap = compose.onNodeWithTag(ICON_TAG).captureToImage().asAndroidBitmap()
            assertEquals(ICON_SIZE, bitmap.width)
            icons.resolve("${mode.onlineLeaderboardKey}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    /**
     * One row per leaderboard, in New Game order (Play's list order), with everything Play Console asks for: a whole
     * number, larger is better, from 0 to the mode's perfect game, with tamper protection on.
     */
    private fun writeSheet(file: File) {
        val text = LOCALES.associateWith(::stringsOf)
        val header = listOf("list_order", "key", "format", "ordering", "lower_limit", "upper_limit", "tamper_protection", "icon") +
            LOCALES.map { "name_${it.code}" }
        val rows = modes.mapIndexed { index, mode ->
            listOf(
                (index + 1).toString(),
                mode.onlineLeaderboardKey,
                "Numeric, 0 decimal places",
                "Larger is better",
                "0",
                mode.maxPossibleScore.toString(),
                "On",
                "leaderboard-icons/${mode.onlineLeaderboardKey}.png",
            ) + LOCALES.map { text.getValue(it).getValue(mode.displayName.key) }
        }
        file.writeText(csvLines(listOf(header) + rows))
    }

    private companion object {
        const val MAX_NAME_LENGTH = 100

        /** Play's icons are 512px square: the glyph at the achievements' size, on their tile. */
        const val ICON_SIZE = 512
        const val GLYPH_SIZE = 320

        /** Big enough to tell any two glyphs apart. */
        const val CHECK_SIZE = 64
        const val ICON_TAG = "leaderboard-icon"
    }
}
