package net.zodac.dicefive.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import net.zodac.dicefive.model.PlayerColour
import net.zodac.dicefive.resources.Res
import net.zodac.dicefive.resources.setup_colour_cd
import net.zodac.dicefive.resources.setup_colour_choose_action
import net.zodac.dicefive.resources.setup_colour_select_action
import net.zodac.dicefive.resources.setup_colour_swaps_cd
import net.zodac.dicefive.ui.common.stringResource
import net.zodac.dicefive.ui.theme.color

/** The coloured dot on a player's row and in the pop-up: [SWATCH_SIZE] drawn inside a 48dp touch target. */
private val SWATCH_SIZE = 28.dp
private val SWATCH_TARGET = 48.dp
private const val SWATCHES_PER_ROW = 4

/**
 * The circle left of a player's name: their colour, and a tap opens a pop-up of every [PlayerColour] to
 * change it. A curated grid rather than a free colour picker - every choice reads on the dark table and
 * none can be mistaken for another player's, which an arbitrary colour can't promise.
 *
 * Choosing a colour another player holds swaps the two (see `GameViewModel.setPlayerColour`), so
 * [holders] names who - by their seat - for the spoken description.
 *
 * TalkBack: the circle is a button, "Player 2 colour, Pink", with a "Choose colour" action; the pop-up's
 * swatches are radio buttons named by colour, the current one selected, and one another player holds adds
 * "swaps with player 3" - the drawn circle has no other cue. The tap is the only action, so nothing
 * needs a custom gesture. Not heard on a device.
 */
@Composable
fun PlayerColourPicker(
    slot: Int,
    colour: PlayerColour,
    holders: Map<PlayerColour, Int>,
    onColourChange: (PlayerColour) -> Unit,
    modifier: Modifier = Modifier,
) {
    var choosing by remember { mutableStateOf(false) }
    val colourName = stringResource(colour.label)
    val pickerDescription = stringResource(Res.string.setup_colour_cd, slot, colourName)
    val chooseLabel = stringResource(Res.string.setup_colour_choose_action)
    val selectLabel = stringResource(Res.string.setup_colour_select_action)
    // DropdownMenu positions itself against its parent, so the swatch and the pop-up share this Box.
    Box(modifier = modifier.size(SWATCH_TARGET), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(SWATCH_TARGET)
                .clearAndSetSemantics {
                    contentDescription = pickerDescription
                    role = Role.Button
                    onClick(label = chooseLabel) {
                        choosing = true
                        true
                    }
                }
                .clip(CircleShape)
                .clickable(onClickLabel = chooseLabel, role = Role.Button) { choosing = true },
            contentAlignment = Alignment.Center,
        ) {
            Swatch(colour = colour)
        }
        DropdownMenu(expanded = choosing, onDismissRequest = { choosing = false }) {
            Column(
                modifier = Modifier.padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                for (rowColours in PlayerColour.entries.chunked(SWATCHES_PER_ROW)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (option in rowColours) {
                            val swapsWith = holders[option]?.takeIf { it != slot }
                            val selected = option == colour
                            val optionName = stringResource(option.label)
                            val optionDescription = if (swapsWith != null) stringResource(Res.string.setup_colour_swaps_cd, optionName, swapsWith) else optionName
                            Box(
                                modifier = Modifier
                                    .size(SWATCH_TARGET)
                                    // No visible name - a screen reader still needs to say which is which.
                                    .clearAndSetSemantics {
                                        contentDescription = optionDescription
                                        role = Role.RadioButton
                                        this.selected = selected
                                        onClick(label = selectLabel) {
                                            onColourChange(option)
                                            choosing = false
                                            true
                                        }
                                    }
                                    .clip(CircleShape)
                                    .selectable(selected = selected, role = Role.RadioButton) {
                                        onColourChange(option)
                                        choosing = false
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Swatch(colour = option, checked = selected)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One colour as a disc, with a tick on it when [checked]; the tick is dark, as every colour here is light. */
@Composable
private fun Swatch(colour: PlayerColour, checked: Boolean = false) {
    Box(
        modifier = Modifier
            .size(SWATCH_SIZE)
            .clip(CircleShape)
            .background(colour.color)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(imageVector = Icons.Filled.Check, contentDescription = null, tint = Color.Black.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
        }
    }
}
