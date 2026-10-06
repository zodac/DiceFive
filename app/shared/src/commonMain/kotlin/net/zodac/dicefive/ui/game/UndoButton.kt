package net.zodac.dicefive.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.zodac.dicefive.ui.theme.TileIconColor
import net.zodac.dicefive.ui.theme.TileTealBorder
import net.zodac.dicefive.ui.theme.TileTealBottom
import net.zodac.dicefive.ui.theme.TileTealTop

/**
 * A dark, rounded, icon-only button with the standard Material "undo" glyph ([BoardButtonIcon]), named
 * "Undo" for a screen reader. Uses the stock [Icons.AutoMirrored.Filled.Undo] rather than a
 * hand-drawn arrow - a bespoke Canvas path looked malformed at small sizes. It once had a small "Undo"
 * label under the glyph too; it was dropped, with [TotalsButton]'s, as the pair looked better without.
 */
@Composable
fun UndoButton(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, minSize: Dp = 48.dp) {
    val shape = RoundedCornerShape(10.dp)
    val iconColor = TileIconColor.copy(alpha = if (enabled) 1f else 0.35f)

    Box(
        modifier = modifier
            // M3's 48dp minimum touch target, wherever the board has the room for it.
            .sizeIn(minWidth = minSize, minHeight = minSize)
            .clip(shape)
            .background(Brush.linearGradient(listOf(TileTealTop, TileTealBottom)))
            .border(1.dp, TileTealBorder, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        // The glyph's description names the button: it merges into the clickable Box as one node.
        BoardButtonIcon(imageVector = Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", tint = iconColor)
    }
}
