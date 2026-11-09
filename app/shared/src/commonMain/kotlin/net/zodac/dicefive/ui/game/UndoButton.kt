package net.zodac.dicefive.ui.game

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.zodac.dicefive.resources.Res
import net.zodac.dicefive.resources.game_undo_cd
import net.zodac.dicefive.ui.common.stringResource
import net.zodac.dicefive.ui.icons.Symbols
import net.zodac.dicefive.ui.style.BoxTone
import net.zodac.dicefive.ui.style.LocalGameVisualTheme

/**
 * A rounded, icon-only button in the player's box style with the standard Material "undo" glyph ([BoardButtonIcon]), named
 * "Undo" for a screen reader. Uses the stock [Symbols.Undo] rather than a
 * hand-drawn arrow - a bespoke Canvas path looked malformed at small sizes. It once had a small "Undo"
 * label under the glyph too; it was dropped, with [TotalsButton]'s, as the pair looked better without.
 */
private val BUTTON_CORNER = 10.dp

@Composable
fun UndoButton(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, minSize: Dp = 48.dp) {
    // An open box of the player's box style, as the boxes beside it are.
    val boxStyle = LocalGameVisualTheme.current.box
    val shape = boxStyle.shape(BUTTON_CORNER)
    val iconColor = boxStyle.paint(BoxTone.OPEN).ink.copy(alpha = if (enabled) 1f else 0.35f)
    // In the tutorial, outlined at the step that asks for it.
    val tutorial = LocalTutorialGuide.current

    Box(
        modifier = modifier
            // Before the clip, which would cut off an outline drawn round the button.
            .then(if (tutorial?.undoes == true) Modifier.tutorialOutline(tutorial.pulse, cornerRadius = 10.dp) else Modifier)
            // M3's 48dp minimum touch target, wherever the board has the room for it.
            .sizeIn(minWidth = minSize, minHeight = minSize)
            .boxSurface(boxStyle, shape, BUTTON_CORNER)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        BoxSurfaceLights(boxStyle, BUTTON_CORNER)
        // The glyph's description names the button: it merges into the clickable Box as one node.
        BoardButtonIcon(imageVector = Symbols.Undo, contentDescription = stringResource(Res.string.game_undo_cd), tint = iconColor)
    }
}
