package net.zodac.dicefive.ui.styles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

/**
 * The "Unlock all styles" purchase's mark ([net.zodac.dicefive.purchases.ProductKey.UNLOCK_ALL_STYLES]): a palette,
 * centred in the [size] square, with an open padlock on a disc at its lower left, cut out of the palette by a ring of
 * [background]. Everything else is drawn in [tint]. Play Console's product icon is this on the achievements' gold tile.
 *
 * The padlock stays at the lower left in a right-to-left language too, clear of the palette's thumb hole. Decorative:
 * whatever shows it names the purchase itself.
 */
@Composable
fun UnlockAllStylesIcon(size: Dp, tint: Color, background: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.Palette, contentDescription = null, tint = tint, modifier = Modifier.size(size * PALETTE / UNITS))
        val disc = size * DISC / UNITS
        val ring = size * RING / UNITS
        Box(
            Modifier
                .absoluteOffset(x = size * DISC_X / UNITS, y = size * DISC_Y / UNITS)
                .size(disc + ring * 2)
                .background(background, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(disc).background(tint, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.LockOpen, contentDescription = null, tint = background, modifier = Modifier.size(disc * PADLOCK))
            }
        }
    }
}

// In 512ths of the square, so the Play icon's pixels.
private const val UNITS = 512f
private const val PALETTE = 320f
private const val DISC = 150f
private const val RING = 22f

/** The disc's centre, from the square's. */
private const val DISC_X = -100f
private const val DISC_Y = 112f

/** Of the disc. */
private const val PADLOCK = 0.62f
