package net.zodac.dicefive.online

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.model.AchievementCategory
import net.zodac.dicefive.ui.achievements.icon
import net.zodac.dicefive.ui.achievements.iconTintOrUnspecified
import net.zodac.dicefive.ui.theme.DiceFiveTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Play Console's side of the achievements: the rules every one must keep to there, checked on every run, and (with
 * `-PexportPlayConsole`, which `scripts/export-play-console.sh` passes) the sheet and icons to enter them from,
 * written to `achievements/` in the gitignored `assets/play-console/` folder.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w600dp-h900dp-mdpi")
class PlayConsoleExportTest {

    @get:Rule
    val compose = createComposeRule()

    private val onPlay = Achievement.entries

    @Test
    fun `points - every achievement has some - Easter eggs the minimum - and all of Play's 2000 spent`() {
        assertEquals(onPlay.toSet(), playPointsTiers.keys)
        for ((achievement, tier) in playPointsTiers) {
            assertTrue("$achievement: ${tier.points}", tier.points in MIN_POINTS..MAX_POINTS_EACH && tier.points % 5 == 0)
            assertEquals(achievement.toString(), achievement.category == AchievementCategory.EASTER_EGGS, tier == PlayPointsTier.EASTER_EGG)
        }
        assertEquals(MIN_POINTS, PlayPointsTier.EASTER_EGG.points)
        assertEquals(MAX_POINTS_TOTAL, playPointsTiers.values.sumOf { it.points })
        // Harder is worth more.
        val byDifficulty = PlayPointsTier.entries - PlayPointsTier.EASTER_EGG
        assertEquals(byDifficulty.map { it.points }.sorted().distinct(), byDifficulty.map { it.points })
    }

    @Test
    fun `text and steps fit Play's limits in every language`() {
        for (locale in LOCALES) {
            val strings = stringsOf(locale)
            for (achievement in onPlay) {
                val name = strings.getValue(achievement.title.key)
                val description = strings.getValue(achievement.description.key)
                assertTrue("$locale $achievement name", name.isNotBlank() && name.length <= MAX_NAME_LENGTH)
                assertTrue("$locale $achievement description", description.isNotBlank() && description.length <= MAX_DESCRIPTION_LENGTH)
            }
        }
        for (achievement in onPlay) {
            val type = achievement.playType as? PlayAchievementType.Incremental ?: continue
            assertTrue("$achievement: $type", type.steps in 2..PLAY_MAX_STEPS && type.steps * type.perStep >= achievement.target)
        }
        assertEquals(PlayAchievementType.Incremental(steps = 10_000, perStep = 10), Achievement.PROFESSIONAL_ROLLER.playType)
        assertEquals(PlayAchievementType.Standard, Achievement.UNTOUCHABLE.playType)
    }

    @Test
    fun `every achievement has an icon of its own - Play rejects two the same`() {
        var shown by mutableStateOf(onPlay.first())
        compose.setContent { IconTile(shown, CHECK_SIZE) }
        val drawn = onPlay.associateWith { achievement ->
            shown = achievement
            compose.waitForIdle()
            val bitmap = compose.onNodeWithTag(ICON_TAG).captureToImage().asAndroidBitmap()
            IntArray(bitmap.width * bitmap.height).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }.toList()
        }
        val sameIcon = drawn.entries.groupBy({ it.value }, { it.key }).values.filter { it.size > 1 }
        assertEquals("Achievements sharing an icon", emptyList<List<Achievement>>(), sameIcon)
    }

    /** An achievement's Play icon: its glyph on its unlock banner's colour, [size] square. */
    @Composable
    private fun IconTile(achievement: Achievement, size: Int) {
        DiceFiveTheme {
            val colours = MaterialTheme.colorScheme
            Box(Modifier.size(size.dp).background(colours.primaryContainer).testTag(ICON_TAG), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = achievement.icon,
                    contentDescription = null,
                    tint = achievement.iconTintOrUnspecified(colours.onPrimaryContainer),
                    modifier = Modifier.size((size * GLYPH_SIZE / ICON_SIZE).dp),
                )
            }
        }
    }

    @Test
    fun `export the sheet and icons for Play Console`() {
        assumeTrue("Only with -PexportPlayConsole", exportingPlayConsole)
        val dir = playConsoleDir().resolve("achievements")
        val icons = dir.resolve("achievement-icons").apply { deleteRecursively(); mkdirs() }
        writeSheet(dir.resolve("achievements.csv"))

        var shown by mutableStateOf(onPlay.first())
        compose.setContent { IconTile(shown, ICON_SIZE) }
        for (achievement in onPlay) {
            shown = achievement
            compose.waitForIdle()
            val bitmap = compose.onNodeWithTag(ICON_TAG).captureToImage().asAndroidBitmap()
            assertEquals(ICON_SIZE, bitmap.width)
            icons.resolve("${achievement.id}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        writeImportZip(dir.resolve("achievements-import.zip"), icons)
    }

    /**
     * Play Console's "Import achievements" zip: AchievementsMetadata.csv (English as the default locale),
     * AchievementsLocalizations.csv (the translations, keyed by the English name), AchievementsIconsMappings.csv and
     * the icons, flat and with no header rows. Play's docs say names and descriptions shouldn't hold commas, but a
     * quoted field (standard CSV) imports fine.
     */
    private fun writeImportZip(file: File, icons: File) {
        val text = LOCALES.associateWith(::stringsOf)
        fun name(achievement: Achievement, locale: Locale) = text.getValue(locale).getValue(achievement.title.key)
        fun description(achievement: Achievement, locale: Locale) = text.getValue(locale).getValue(achievement.description.key)

        val metadata = onPlay.mapIndexed { index, achievement ->
            val type = achievement.playType
            listOf(
                name(achievement, Locale.ENGLISH),
                description(achievement, Locale.ENGLISH),
                if (type is PlayAchievementType.Incremental) "True" else "False",
                (type as? PlayAchievementType.Incremental)?.steps?.toString().orEmpty(),
                if (achievement.playHidden) "Hidden" else "Revealed",
                playPointsTiers.getValue(achievement).points.toString(),
                (index + 1).toString(),
            )
        }
        val localizations = onPlay.flatMap { achievement ->
            LOCALES.flatMap { locale ->
                locale.playCodes.map { code -> listOf(name(achievement, Locale.ENGLISH), name(achievement, locale), description(achievement, locale), code) }
            }
        }
        val iconMappings = onPlay.map { listOf(name(it, Locale.ENGLISH), "${it.id}.png") }

        ZipOutputStream(file.outputStream()).use { zip ->
            fun add(entry: String, bytes: ByteArray) {
                zip.putNextEntry(ZipEntry(entry))
                zip.write(bytes)
                zip.closeEntry()
            }
            add("AchievementsMetadata.csv", csvLines(metadata).toByteArray())
            add("AchievementsLocalizations.csv", csvLines(localizations).toByteArray())
            add("AchievementsIconsMappings.csv", csvLines(iconMappings).toByteArray())
            onPlay.forEach { add("${it.id}.png", icons.resolve("${it.id}.png").readBytes()) }
        }
    }

    /** One row per achievement, in the app's order (Play's sort rank), with everything Play Console asks for. */
    private fun writeSheet(file: File) {
        val text = LOCALES.associateWith(::stringsOf)
        val header = listOf("sort_rank", "id", "points", "tier", "type", "steps", "initial_state", "icon") +
            LOCALES.flatMap { listOf("name_${it.code}", "description_${it.code}") }
        val rows = onPlay.mapIndexed { index, achievement ->
            val type = achievement.playType
            val tier = playPointsTiers.getValue(achievement)
            listOf(
                (index + 1).toString(),
                achievement.id,
                tier.points.toString(),
                tier.name.lowercase(),
                if (type is PlayAchievementType.Incremental) "Incremental" else "Standard",
                (type as? PlayAchievementType.Incremental)?.steps?.toString().orEmpty(),
                if (achievement.playHidden) "Hidden" else "Revealed",
                "achievement-icons/${achievement.id}.png",
            ) + LOCALES.flatMap { listOf(text.getValue(it).getValue(achievement.title.key), text.getValue(it).getValue(achievement.description.key)) }
        }
        file.writeText(csvLines(listOf(header) + rows))
    }

    private companion object {
        const val MIN_POINTS = 5
        const val MAX_POINTS_EACH = 200
        const val MAX_POINTS_TOTAL = 2_000
        const val MAX_NAME_LENGTH = 100
        const val MAX_DESCRIPTION_LENGTH = 500

        /** Play's icons are 512px square: the achievement's glyph on its unlock banner's colour. */
        const val ICON_SIZE = 512
        const val GLYPH_SIZE = 320

        /** Big enough to tell any two glyphs apart, small enough to draw all 110 on every run. */
        const val CHECK_SIZE = 64
        const val ICON_TAG = "play-icon"
    }
}
