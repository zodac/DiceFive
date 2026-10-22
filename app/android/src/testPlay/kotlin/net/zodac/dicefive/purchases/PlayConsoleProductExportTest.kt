package net.zodac.dicefive.purchases

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.zodac.dicefive.online.exportingPlayConsole
import net.zodac.dicefive.online.playConsoleDir
import net.zodac.dicefive.ui.styles.UnlockAllStylesIcon
import net.zodac.dicefive.ui.theme.DiceFiveTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The product's icon for Play Console (Monetise with Play > Products > One-time products), entered by hand: the rule it
 * keeps to, checked on every run, and (with `-PexportPlayConsole`, which `scripts/export-play-console.sh` passes) the
 * icon itself, written to the gitignored `assets/play-console/` folder.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w600dp-h900dp-mdpi")
class PlayConsoleProductExportTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the icon's artwork keeps clear of its edges - Play asks for it in the centre`() {
        var tile = 0
        compose.setContent { IconTile(onTile = { tile = it }) }
        val bitmap = icon()
        val edge = (0 until ICON_SIZE).flatMap { i ->
            (0 until MARGIN).flatMap { m ->
                listOf(bitmap.getPixel(i, m), bitmap.getPixel(i, ICON_SIZE - 1 - m), bitmap.getPixel(m, i), bitmap.getPixel(ICON_SIZE - 1 - m, i))
            }
        }
        assertTrue("Artwork within $MARGIN px of the edge", edge.all { it == tile })
    }

    @Test
    fun `export the icon for Play Console`() {
        assumeTrue("Only with -PexportPlayConsole", exportingPlayConsole)
        val icons = playConsoleDir().resolve("product-icons").apply { deleteRecursively(); mkdirs() }
        compose.setContent { IconTile() }
        icons.resolve("${ProductKey.UNLOCK_ALL_STYLES}.png").outputStream().use { icon().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun icon(): Bitmap {
        compose.waitForIdle()
        return compose.onNodeWithTag(ICON_TAG).captureToImage().asAndroidBitmap().also { assertEquals(ICON_SIZE, it.width) }
    }

    /** The product's Play icon: its mark on the achievements' gold tile, 512px square as theirs are. */
    @Composable
    private fun IconTile(onTile: (argb: Int) -> Unit = {}) {
        DiceFiveTheme {
            val colours = MaterialTheme.colorScheme
            onTile(colours.primaryContainer.toArgb())
            Box(Modifier.size(ICON_SIZE.dp).background(colours.primaryContainer).testTag(ICON_TAG)) {
                UnlockAllStylesIcon(size = ICON_SIZE.dp, tint = colours.onPrimaryContainer)
            }
        }
    }

    private companion object {
        const val ICON_SIZE = 512

        /** Clear on every side: the padlock's disc, the nearest, ends 47px from the bottom and 59px from the left. */
        const val MARGIN = 40
        const val ICON_TAG = "product-icon"
    }
}
