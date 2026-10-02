package net.zodac.dicefive.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Info
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
import net.zodac.dicefive.ui.common.DiceFiveDialog
import net.zodac.dicefive.ui.common.FooterPill
import net.zodac.dicefive.ui.common.ShrinkThenWrapText
import net.zodac.dicefive.ui.common.ScreenScaffold

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
        title = "Settings",
        onBack = onBack,
        modifier = modifier,
        scrollable = true,
        footer = { FooterPill("Version ${LocalAppContainer.current.buildInfo.versionName}") },
    ) {
        // Nothing but the title bar until the saved switches are back, rather than drawing them in
        // their default positions and then flipping the ones the player has changed.
        val toggles = loadedToggles ?: return@ScreenScaffold
        Card(modifier = Modifier.fillMaxWidth()) {
            SwitchSetting(Icons.AutoMirrored.Filled.VolumeUp, "Sound effects", toggles.soundEnabled, viewModel::setSoundEnabled)
            SwitchSetting(Icons.Filled.Vibration, "Vibration", toggles.vibrationEnabled, viewModel::setVibrationEnabled)
            SwitchSetting(Icons.Filled.Casino, "Simple dice roll", toggles.simpleDiceRoll, viewModel::setSimpleDiceRoll)
            SwitchSetting(
                Icons.Filled.CheckCircle,
                "Confirm leaving game",
                toggles.confirmBeforeLeavingGame,
                viewModel::setConfirmBeforeLeavingGame,
            )
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            ResetSetting(
                icon = Icons.Filled.RestartAlt,
                label = "Reset achievements",
                description = "Lock every achievement again",
                onClick = { showResetAchievementsConfirmation = true },
            )
            ResetSetting(
                icon = Icons.Filled.DeleteSweep,
                label = "Reset leaderboard",
                description = "Delete all recorded scores",
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
                Text("About")
            }
            TextButton(onClick = { showLicenses = true }, modifier = Modifier.weight(1f)) {
                Icon(
                    imageVector = Icons.Filled.Gavel,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text("Licences")
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
            title = "Reset Achievements?",
            message = "Every achievement will be locked again and all progress towards them lost. " +
                "This can't be undone. Your scores and settings are not affected.",
            confirmLabel = "Reset",
            onConfirm = {
                viewModel.resetAchievements()
                showResetAchievementsConfirmation = false
            },
            dismissLabel = "Cancel",
            onDismiss = { showResetAchievementsConfirmation = false },
            onDismissRequest = { showResetAchievementsConfirmation = false },
        )
    }

    if (showResetLeaderboardConfirmation) {
        DiceFiveDialog(
            icon = Icons.Filled.RestartAlt,
            title = "Reset Leaderboard?",
            message = "Every recorded score will be deleted, clearing the Leaderboard screen - and Statistics with " +
                "it, since it's calculated from the same scores. This can't be undone. Achievements and settings " +
                "are not affected, though any achievement progress measured against the leaderboard will start over.",
            confirmLabel = "Reset",
            onConfirm = {
                viewModel.resetLeaderboard()
                showResetLeaderboardConfirmation = false
            },
            dismissLabel = "Cancel",
            onDismiss = { showResetLeaderboardConfirmation = false },
            onDismissRequest = { showResetLeaderboardConfirmation = false },
        )
    }
}
