package net.zodac.dicefive.ui.game

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** The most a board button's glyph is drawn at - what it's drawn at whenever the board has room. */
private val BOARD_BUTTON_ICON_SIZE = 26.dp

/**
 * The glyph of an icon-only board button ([UndoButton], [TotalsButton]): [BOARD_BUTTON_ICON_SIZE]
 * square, or smaller - still square - when the row the board gives the button is shorter than that
 * (a short screen). It's a glyph rather than text, so a large system font doesn't change it.
 */
@Composable
internal fun BoardButtonIcon(imageVector: ImageVector, contentDescription: String?, tint: Color) {
    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        tint = tint,
        modifier = Modifier.heightIn(max = BOARD_BUTTON_ICON_SIZE).fillMaxHeight().aspectRatio(1f),
    )
}
