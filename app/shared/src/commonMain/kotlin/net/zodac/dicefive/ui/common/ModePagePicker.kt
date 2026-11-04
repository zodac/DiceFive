package net.zodac.dicefive.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.zodac.dicefive.model.ModePage
import net.zodac.dicefive.resources.Res
import net.zodac.dicefive.resources.common_combined

/** A page's name as its drop-down and title say it: its mode's, or Combined. */
@Composable
fun modePageName(page: ModePage): String = when (page) {
    is ModePage.Mode -> stringResource(page.mode.displayName)
    ModePage.Combined -> stringResource(Res.string.common_combined)
}

/**
 * The drop-down at the top of the Leaderboard and Statistics screens: every [ModePage], with a divider after Standard and
 * before Combined. [label] names the field for a screen reader. TalkBack: as [DropdownChoiceField].
 */
@Composable
fun ModePagePicker(selected: ModePage, onSelect: (ModePage) -> Unit, label: String, modifier: Modifier = Modifier) {
    DropdownChoiceField(
        options = ModePage.all,
        selected = selected,
        onSelect = onSelect,
        label = label,
        optionText = { modePageName(it) },
        modifier = modifier,
        dividerAfterFirst = true,
        dividerBeforeLast = true,
    )
}
