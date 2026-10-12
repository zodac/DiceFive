package net.zodac.dicefive.ui.game

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import net.zodac.dicefive.ui.theme.GoldAccent

/** The most a board button's glyph is drawn at - what it's drawn at whenever the board has room. */
private val BOARD_BUTTON_ICON_SIZE = 26.dp

/** A half of a board button's glyph that can be lit on its own - see [BoardButtonIcon]. */
internal enum class IconHalf { TOP, BOTTOM }

/**
 * The glyph of an icon-only board button ([UndoButton], [TotalsButton]): [BOARD_BUTTON_ICON_SIZE]
 * square, or smaller - still square - when the row the board gives the button is shorter than that
 * (a short screen). It's a glyph rather than text, so a large system font doesn't change it.
 *
 * Each of [litHalves] is drawn in [litColor] over the glyph's [tint] - one half lit, the other not, for something
 * that is half done (D20's two bonuses).
 */
@Composable
internal fun BoardButtonIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    tint: Color,
    litHalves: Set<IconHalf> = emptySet(),
    litColor: Color = GoldAccent,
) {
    val size = Modifier.heightIn(max = BOARD_BUTTON_ICON_SIZE).fillMaxHeight().aspectRatio(1f)
    if (litHalves.isEmpty()) {
        Icon(imageVector = imageVector, contentDescription = contentDescription, tint = tint, modifier = size)
        return
    }
    Box(modifier = size) {
        Icon(imageVector = imageVector, contentDescription = contentDescription, tint = tint, modifier = Modifier.fillMaxSize())
        for (half in litHalves) {
            Icon(
                imageVector = imageVector,
                contentDescription = null,
                tint = litColor,
                modifier = Modifier.fillMaxSize().drawWithContent {
                    val middle = this.size.height / 2
                    if (half == IconHalf.TOP) clipRect(bottom = middle) { this@drawWithContent.drawContent() }
                    else clipRect(top = middle) { this@drawWithContent.drawContent() }
                },
            )
        }
    }
}
