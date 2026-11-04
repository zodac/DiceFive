package net.zodac.dicefive

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.zodac.dicefive.ui.style.MidnightFeltBackground
import net.zodac.dicefive.ui.style.TrayBlueMat
import net.zodac.dicefive.ui.theme.FeltNavyBottom
import net.zodac.dicefive.ui.theme.FeltNavyGlow
import net.zodac.dicefive.ui.theme.FeltNavyTop
import net.zodac.dicefive.ui.theme.TrayBlueRing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The default background and mat match the launcher icon's table: its felt's light, and its mat's gold ring. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class ClassicTableArtTest {

    @get:Rule
    val compose = createComposeRule()

    private fun assertClose(expected: Color, actual: Int) {
        val wanted = expected.toArgb()
        listOf(AndroidColor::red, AndroidColor::green, AndroidColor::blue).forEach { channel ->
            assertEquals("#${Integer.toHexString(actual)}", channel(wanted).toDouble(), channel(actual).toDouble(), COLOUR_TOLERANCE)
        }
    }

    @Test
    fun `the felt is lit from above its middle and falls to the brand navy`() {
        compose.setContent { Box(Modifier.fillMaxSize().background(MidnightFeltBackground.scoreAreaBrush)) }
        val felt = compose.onRoot().captureToImage().asAndroidBitmap()
        val middle = felt.width / 2
        assertClose(FeltNavyGlow, felt.getPixel(middle, felt.height * LIGHT_HEIGHT_PERCENT / 100))
        // On the way down, at the gradient's middle stop: 0.55 of a reach of 0.95 of the height, below the light.
        assertClose(FeltNavyTop, felt.getPixel(middle, felt.height * (LIGHT_HEIGHT_PERCENT + MIDDLE_STOP_PERCENT) / 100))
        assertClose(FeltNavyBottom, felt.getPixel(0, felt.height - 1))
    }

    @Test
    fun `the mat has a gold ring along its edge, and none inside it`() {
        compose.setContent {
            Box(Modifier.size(300.dp, 150.dp).background(TrayBlueMat.diceTrayBrush)) {
                TrayBlueMat.DiceTrayDecoration(Modifier.fillMaxSize())
            }
        }
        val mat = compose.onRoot().captureToImage().asAndroidBitmap()
        val middleX = mat.width / 2
        val middleY = mat.height / 2
        assertClose(TrayBlueRing, mat.getPixel(middleX, 1))
        assertClose(TrayBlueRing, mat.getPixel(middleX, mat.height - 2))
        assertClose(TrayBlueRing, mat.getPixel(1, middleY))
        assertClose(TrayBlueRing, mat.getPixel(mat.width - 2, middleY))
        val inside = mat.getPixel(middleX, middleY)
        assertTrue("#${Integer.toHexString(inside)}", AndroidColor.blue(inside) > AndroidColor.red(inside))
    }

    private companion object {
        const val COLOUR_TOLERANCE = 4.0
        const val LIGHT_HEIGHT_PERCENT = 8
        const val MIDDLE_STOP_PERCENT = 52
    }
}
