package net.zodac.dicefive.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MotionPhotosOff
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.zodac.dicefive.app.LocalAppContainer
import net.zodac.dicefive.data.settings.AnimationLevel
import net.zodac.dicefive.resources.Res
import net.zodac.dicefive.resources.about_title
import net.zodac.dicefive.resources.common_cancel
import net.zodac.dicefive.resources.licences_title
import net.zodac.dicefive.resources.settings_animations
import net.zodac.dicefive.resources.settings_animations_high
import net.zodac.dicefive.resources.settings_animations_high_short
import net.zodac.dicefive.resources.settings_animations_low
import net.zodac.dicefive.resources.settings_animations_low_short
import net.zodac.dicefive.resources.settings_animations_medium
import net.zodac.dicefive.resources.settings_animations_medium_short
import net.zodac.dicefive.resources.settings_animations_off
import net.zodac.dicefive.resources.settings_confirm_leaving
import net.zodac.dicefive.resources.settings_reset_achievements
import net.zodac.dicefive.resources.settings_reset_achievements_description
import net.zodac.dicefive.resources.settings_reset_achievements_message
import net.zodac.dicefive.resources.settings_reset_achievements_title
import net.zodac.dicefive.resources.settings_reset_confirm
import net.zodac.dicefive.resources.settings_reset_leaderboard
import net.zodac.dicefive.resources.settings_reset_leaderboard_description
import net.zodac.dicefive.resources.settings_reset_leaderboard_message
import net.zodac.dicefive.resources.settings_reset_leaderboard_title
import net.zodac.dicefive.resources.settings_sound
import net.zodac.dicefive.resources.settings_title
import net.zodac.dicefive.resources.settings_version
import net.zodac.dicefive.resources.settings_vibration
import net.zodac.dicefive.ui.common.FittedSegmentedChoiceRow
import net.zodac.dicefive.ui.common.DiceFiveDialog
import net.zodac.dicefive.ui.common.FooterPill
import net.zodac.dicefive.ui.common.ScreenScaffold
import net.zodac.dicefive.ui.common.ShrinkThenWrapText
import net.zodac.dicefive.ui.common.stringResource

/** A setting's label: bodyLarge on one line when it fits, stepped down to [MIN_READABLE_FONT_SIZE] on a
 * narrow screen, and wrapped to a second line (the row grows) rather than shrunk further. */
@Composable
private fun SettingLabel(text: String, modifier: Modifier = Modifier) {
    ShrinkThenWrapText(text = text, style = MaterialTheme.typography.bodyLarge, modifier = modifier)
}

/**
 * One on/off setting: the whole row is the toggle, not just the switch at its end - tapping the
 * label flips it too, with the ripple across the row, and a screen reader hears one "Sound effects,
 * switch, on" rather than the label and an unnamed switch separately. The [Switch] itself takes no
 * clicks of its own ([Switch]'s `onCheckedChange = null`), so there's one target, not two.
 */
@Composable
private fun SwitchSetting(icon: ImageVector, label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    ListItem(
        leadingContent = { Icon(imageVector = icon, contentDescription = null) },
        headlineContent = { SettingLabel(label) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        // The Card already supplies the surface; an opaque ListItem container would paint a
        // second, slightly different one on top of it.
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

/**
 * One destructive action as a row in the same shape as [SwitchSetting]: error-tinted icon and label (no
 * filled background - a red slab is the loudest thing on the page), a line saying what it does, and the whole
 * row as the tap target. It only opens a confirmation; nothing is deleted by the tap itself. A screen reader
 * hears one "Reset achievements, Lock every achievement again, button".
 */
@Composable
private fun ResetSetting(icon: ImageVector, label: String, description: String, onClick: () -> Unit) {
    ListItem(
        leadingContent = { Icon(imageVector = icon, contentDescription = null) },
        headlineContent = { SettingLabel(label) },
        supportingContent = { Text(description) },
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            leadingIconColor = MaterialTheme.colorScheme.error,
            headlineColor = MaterialTheme.colorScheme.error,
        ),
    )
}

/**
 * One pick-one setting from a few options: icon and label as a [SwitchSetting] has them, with a segmented row of the
 * options under the label (as the New Game screen's AI difficulty is chosen - [FittedSegmentedChoiceRow], so the
 * labels shrink together and then go [compactLabel]/[compactGlyph] on a narrow screen). TalkBack hears the label, then
 * each option as a selectable button ("Medium, selected").
 */
@Composable
private fun <T> SegmentedSetting(
    icon: ImageVector,
    label: String,
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    optionLabel: @Composable (T) -> String,
    compactLabel: @Composable (T) -> String,
    compactGlyph: (T) -> ImageVector?,
) {
    // The icon and label in a ListItem, so they sit exactly as the switch rows' do; the options below it, not in its
    // supporting slot, which ListItem measures intrinsically - and the fitted row (a BoxWithConstraints) can't be.
    Column {
        ListItem(
            leadingContent = { Icon(imageVector = icon, contentDescription = null) },
            headlineContent = { SettingLabel(label) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
        FittedSegmentedChoiceRow(
            options = options,
            selected = selected,
            onSelect = onSelect,
            label = optionLabel,
            // Lined up under the label: ListItem's 16dp start, the 24dp icon and the 16dp gap after it; its 24dp end.
            modifier = Modifier.padding(start = SETTING_TEXT_START, end = 24.dp, bottom = 16.dp),
            compactLabel = compactLabel,
            compactGlyph = compactGlyph,
        )
    }
}

private val SETTING_TEXT_START = 56.dp

@Composable
private fun animationLevelLabel(level: AnimationLevel): String = stringResource(
    when (level) {
        AnimationLevel.HIGH -> Res.string.settings_animations_high
        AnimationLevel.MEDIUM -> Res.string.settings_animations_medium
        AnimationLevel.LOW -> Res.string.settings_animations_low
        AnimationLevel.OFF -> Res.string.settings_animations_off
    },
)

/** The level's initial for a narrow screen - its own string, as a translation's initials can collide. Off is a glyph. */
@Composable
private fun animationLevelShortLabel(level: AnimationLevel): String = when (level) {
    AnimationLevel.HIGH -> stringResource(Res.string.settings_animations_high_short)
    AnimationLevel.MEDIUM -> stringResource(Res.string.settings_animations_medium_short)
    AnimationLevel.LOW -> stringResource(Res.string.settings_animations_low_short)
    AnimationLevel.OFF -> animationLevelLabel(level)
}

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val loadedToggles by viewModel.toggles.collectAsStateWithLifecycle()
    // Saveable: a rotation mid-confirmation shouldn't silently drop the question.
    var showResetAchievementsConfirmation by rememberSaveable { mutableStateOf(false) }
    var showResetLeaderboardConfirmation by rememberSaveable { mutableStateOf(false) }
    var showLicenses by rememberSaveable { mutableStateOf(false) }
    var showAbout by rememberSaveable { mutableStateOf(false) }

    ScreenScaffold(
        title = stringResource(Res.string.settings_title),
        onBack = onBack,
        modifier = modifier,
        scrollable = true,
        footer = { FooterPill(stringResource(Res.string.settings_version, LocalAppContainer.current.buildInfo.versionName)) },
    ) {
        // Nothing but the title bar until the saved switches are back, rather than drawing them in
        // their default positions and then flipping the ones the player has changed.
        val toggles = loadedToggles ?: return@ScreenScaffold
        Card(modifier = Modifier.fillMaxWidth()) {
            SwitchSetting(Icons.AutoMirrored.Filled.VolumeUp, stringResource(Res.string.settings_sound), toggles.soundEnabled, viewModel::setSoundEnabled)
            SwitchSetting(Icons.Filled.Vibration, stringResource(Res.string.settings_vibration), toggles.vibrationEnabled, viewModel::setVibrationEnabled)
            SwitchSetting(
                Icons.Filled.CheckCircle,
                stringResource(Res.string.settings_confirm_leaving),
                toggles.confirmBeforeLeavingGame,
                viewModel::setConfirmBeforeLeavingGame,
            )
        }

        // A card of its own: a row of choices under its label isn't the same shape as the switch rows above.
        Card(modifier = Modifier.fillMaxWidth()) {
            SegmentedSetting(
                icon = Icons.Filled.Animation,
                label = stringResource(Res.string.settings_animations),
                // Least to most, Off first: from the start edge, so left to right here and mirrored in a right-to-left language.
                options = AnimationLevel.entries.reversed(),
                selected = toggles.animationLevel,
                onSelect = viewModel::setAnimationLevel,
                optionLabel = { animationLevelLabel(it) },
                compactLabel = { animationLevelShortLabel(it) },
                // "Off" as a symbol when there's no room for words: an initial would be "O", or a translation's own.
                compactGlyph = { if (it == AnimationLevel.OFF) Icons.Filled.MotionPhotosOff else null },
            )
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            ResetSetting(
                icon = Icons.Filled.RestartAlt,
                label = stringResource(Res.string.settings_reset_achievements),
                description = stringResource(Res.string.settings_reset_achievements_description),
                onClick = { showResetAchievementsConfirmation = true },
            )
            ResetSetting(
                icon = Icons.Filled.DeleteSweep,
                label = stringResource(Res.string.settings_reset_leaderboard),
                description = stringResource(Res.string.settings_reset_leaderboard_description),
                onClick = { showResetLeaderboardConfirmation = true },
            )
        }

        // Quiet links, not a Card section: these aren't settings, just where the standalone About
        // screen's content moved once it was folded in here. The version is the scaffold's footer.
        // Side by side, each taking half the width, so a label that wraps at a large font stays inside its half.
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = {
                    showAbout = true
                    viewModel.onAboutViewed()
                },
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(stringResource(Res.string.about_title))
            }
            TextButton(onClick = { showLicenses = true }, modifier = Modifier.weight(1f)) {
                Icon(
                    imageVector = Icons.Filled.Gavel,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(stringResource(Res.string.licences_title))
            }
        }
    }

    if (showLicenses) {
        LicensesDialog(onDismissRequest = { showLicenses = false })
    }

    if (showAbout) {
        AboutDialog(onDismissRequest = { showAbout = false })
    }

    if (showResetAchievementsConfirmation) {
        DiceFiveDialog(
            icon = Icons.Filled.RestartAlt,
            title = stringResource(Res.string.settings_reset_achievements_title),
            message = stringResource(Res.string.settings_reset_achievements_message),
            confirmLabel = stringResource(Res.string.settings_reset_confirm),
            onConfirm = {
                viewModel.resetAchievements()
                showResetAchievementsConfirmation = false
            },
            dismissLabel = stringResource(Res.string.common_cancel),
            onDismiss = { showResetAchievementsConfirmation = false },
            onDismissRequest = { showResetAchievementsConfirmation = false },
        )
    }

    if (showResetLeaderboardConfirmation) {
        DiceFiveDialog(
            icon = Icons.Filled.RestartAlt,
            title = stringResource(Res.string.settings_reset_leaderboard_title),
            message = stringResource(Res.string.settings_reset_leaderboard_message),
            confirmLabel = stringResource(Res.string.settings_reset_confirm),
            onConfirm = {
                viewModel.resetLeaderboard()
                showResetLeaderboardConfirmation = false
            },
            dismissLabel = stringResource(Res.string.common_cancel),
            onDismiss = { showResetLeaderboardConfirmation = false },
            onDismissRequest = { showResetLeaderboardConfirmation = false },
        )
    }
}
