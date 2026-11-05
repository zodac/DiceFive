package net.zodac.dicefive.ui.common

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import net.zodac.dicefive.ui.icons.Symbols

/** The share of a disabled [UpDownButtons] arrow's colour that shows: Material's own for a disabled control. */
private const val DISABLED_ARROW_ALPHA = 0.38f

private val ARROW_SIZE = 20.dp

/** How wide [UpDownButtons] is: two stock icon buttons. */
internal val UP_DOWN_BUTTONS_WIDTH = 96.dp

/**
 * A pair of arrows that move a long list up or down: Achievements' category headers and the Leaderboard's pages that scroll, each
 * in its own [color]. An arrow with nowhere to go is faded and disabled, not removed, so the pair always sits in the same
 * place. [upDescription] and [downDescription] are what a screen reader says for each.
 */
@Composable
internal fun UpDownButtons(
    onUp: () -> Unit,
    onDown: () -> Unit,
    upEnabled: Boolean,
    downEnabled: Boolean,
    upDescription: String,
    downDescription: String,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
) {
    val colors = IconButtonDefaults.iconButtonColors(contentColor = color, disabledContentColor = color.copy(alpha = DISABLED_ARROW_ALPHA))
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onUp, enabled = upEnabled, colors = colors) {
            Icon(imageVector = Symbols.ArrowUpward, contentDescription = upDescription, modifier = Modifier.size(ARROW_SIZE))
        }
        IconButton(onClick = onDown, enabled = downEnabled, colors = colors) {
            Icon(imageVector = Symbols.ArrowDownward, contentDescription = downDescription, modifier = Modifier.size(ARROW_SIZE))
        }
    }
}
