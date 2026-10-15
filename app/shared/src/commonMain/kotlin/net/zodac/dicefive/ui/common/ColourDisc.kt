package net.zodac.dicefive.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A colour as a disc [size] across, in a thin [ring], with [content] centred on it - a swatch to pick or to show. */
@Composable
fun ColourDisc(colour: Color, size: Dp, ring: Color, content: @Composable BoxScope.() -> Unit = {}) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(colour)
            .border(1.dp, ring, CircleShape),
        contentAlignment = Alignment.Center,
        content = content,
    )
}
