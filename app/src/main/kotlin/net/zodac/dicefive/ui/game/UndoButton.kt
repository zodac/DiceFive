package net.zodac.dicefive.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.zodac.dicefive.ui.theme.TileIconColor
import net.zodac.dicefive.ui.theme.TileTealBorder
import net.zodac.dicefive.ui.theme.TileTealBottom
import net.zodac.dicefive.ui.theme.TileTealTop

/**
 * A dark, rounded button with the standard Material "undo" glyph plus a small text label, since
 * the icon alone read as unclear at this size. Uses the stock [Icons.AutoMirrored.Filled.Undo]
 * rather than a hand-drawn arrow - a bespoke Canvas path looked malformed at small sizes.
 */
@Composable
fun UndoButton(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(10.dp)
    val iconColor = TileIconColor.copy(alpha = if (enabled) 1f else 0.35f)

    Column(
        modifier = modifier
            .clip(shape)
            .background(Brush.linearGradient(listOf(TileTealTop, TileTealBottom)))
            .border(1.dp, TileTealBorder, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Undo,
            // Decorative: the "Undo" text label right below already names this control for
            // screen readers, and the pair reads as one control since both are inside the same
            // clickable Column.
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(26.dp),
        )
        Text(
            text = "Undo",
            color = iconColor,
            fontSize = 10.sp,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
