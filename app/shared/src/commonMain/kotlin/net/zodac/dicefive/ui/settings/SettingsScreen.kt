package net.zodac.dicefive.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.flowOf
import net.zodac.dicefive.app.LocalAppContainer
import net.zodac.dicefive.online.GameServicesState
import net.zodac.dicefive.online.LocalGameServices
import net.zodac.dicefive.online.LocalOnlineSyncStatus
import net.zodac.dicefive.platform.LocalPlatformServices
import net.zodac.dicefive.resources.Res
import net.zodac.dicefive.resources.about_title
import net.zodac.dicefive.resources.app_name
import net.zodac.dicefive.resources.common_cancel
import net.zodac.dicefive.resources.licences_title
import net.zodac.dicefive.resources.settings_already_developer
import net.zodac.dicefive.resources.settings_confirm_leaving
import net.zodac.dicefive.resources.settings_graphics
import net.zodac.dicefive.resources.settings_tricolour_marks
import net.zodac.dicefive.resources.settings_import_progress_confirm
import net.zodac.dicefive.resources.settings_import_progress_message
import net.zodac.dicefive.resources.settings_import_progress_title
import net.zodac.dicefive.resources.settings_reset_action
import net.zodac.dicefive.resources.settings_reset_leaderboard
import net.zodac.dicefive.resources.settings_reset_leaderboard_description
import net.zodac.dicefive.resources.settings_reset_leaderboard_message
import net.zodac.dicefive.resources.settings_reset_leaderboard_online_note
import net.zodac.dicefive.resources.settings_reset_leaderboard_title
import net.zodac.dicefive.resources.settings_sound
import net.zodac.dicefive.resources.settings_steps_from_developer
import net.zodac.dicefive.resources.settings_title
import net.zodac.dicefive.resources.settings_version
import net.zodac.dicefive.resources.settings_vibration
import net.zodac.dicefive.ui.common.DiceFiveDialog
import net.zodac.dicefive.ui.common.FooterPill
import net.zodac.dicefive.ui.common.HoldPageCover
import net.zodac.dicefive.ui.common.LocalLandscapePage
import net.zodac.dicefive.ui.common.ScreenScaffold
import net.zodac.dicefive.ui.common.ShrinkThenWrapText
import net.zodac.dicefive.ui.common.TwoPanes
import net.zodac.dicefive.ui.common.pluralStringResource
import net.zodac.dicefive.ui.common.stringResource
import net.zodac.dicefive.ui.icons.Symbols

/** How far the About and Licences row is lifted into the gap above it, and the version below it with it. */
private val LINKS_RAISE = (-8).dp

/** A setting's label: bodyLarge on one line when it fits, stepped down to [MIN_READABLE_FONT_SIZE] on a
 * narrow screen, and wrapped to a second line (the row grows) rather than shrunk further. */
@Composable
internal fun SettingLabel(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    ShrinkThenWrapText(text = text, style = MaterialTheme.typography.bodyLarge, modifier = modifier, color = color)
}

/** The least height of a [SettingRow]: the touch target's minimum, under M3's 56dp list item. */
private val SETTING_ROW_MIN_HEIGHT = 48.dp

/** How far a [SettingRow]'s content sits from its top and bottom, and a compact card's rows from the card's edges. */
internal val SETTING_ROW_VERTICAL_PADDING = 4.dp

/**
 * One row of a compact settings card, in an M3 [androidx.compose.material3.ListItem]'s layout (icon, label over an optional line, optional end
 * content) but tighter: a one-line row is [SETTING_ROW_MIN_HEIGHT] tall rather than 56dp, a two-line one 52dp rather
 * than 72dp. It grows with its text at a large font. Semantics are the [modifier]'s; the pieces carry none of their own.
 */
@Composable
internal fun SettingRow(
    icon: ImageVector,
    headline: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    iconColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = SETTING_ROW_MIN_HEIGHT)
            .padding(horizontal = 16.dp, vertical = SETTING_ROW_VERTICAL_PADDING),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = iconColor)
        Column(modifier = Modifier.weight(1f)) {
            headline()
            if (supporting != null) {
                Text(supporting, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing?.invoke()
    }
}

/** A Card of [SettingRow]s, with [SETTING_ROW_VERTICAL_PADDING] above the first and below the last. */
@Composable
internal fun CompactSettingsCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = SETTING_ROW_VERTICAL_PADDING)) { content() }
    }
}

/**
 * One on/off setting: the whole row is the toggle, not just the switch at its end - tapping the
 * label flips it too, with the ripple across the row, and a screen reader hears one "Sound effects,
 * switch, on" rather than the label and an unnamed switch separately. The [Switch] itself takes no
 * clicks of its own ([Switch]'s `onCheckedChange = null`), so there's one target, not two.
 */
@Composable
internal fun SwitchSetting(icon: ImageVector, label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    SettingRow(
        icon = icon,
        headline = { SettingLabel(label) },
        trailing = { Switch(checked = checked, onCheckedChange = null) },
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
    )
}

/**
 * One destructive action as a row in the same shape as [SwitchSetting]: error-tinted icon and label (no
 * filled background - a red slab is the loudest thing on the page), a line saying what it does, and the whole
 * row as the tap target. It only opens a confirmation; nothing is deleted by the tap itself. A screen reader
 * hears one "Reset leaderboard, Delete all recorded scores, button, double-tap to reset".
 */
@Composable
private fun ResetSetting(icon: ImageVector, label: String, description: String, onClick: () -> Unit) {
    SettingRow(
        icon = icon,
        headline = { SettingLabel(label, color = MaterialTheme.colorScheme.error) },
        supporting = description,
        modifier = Modifier.clickable(role = Role.Button, onClickLabel = stringResource(Res.string.settings_reset_action), onClick = onClick),
        iconColor = MaterialTheme.colorScheme.error,
    )
}

/**
 * A row that opens a page of its own - the Graphics page: its icon and label, and a gold arrow pointing onward
 * (mirrored right to left), as a picker field has. The whole row is the button, named by its label.
 */
@Composable
private fun PageLinkSetting(icon: ImageVector, label: String, onClick: () -> Unit) {
    SettingRow(
        icon = icon,
        headline = { SettingLabel(label) },
        trailing = { Icon(imageVector = Symbols.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
    )
}

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onGraphics: () -> Unit = {},
) {
    val loadedToggles by viewModel.toggles.collectAsStateWithLifecycle()
    // Saveable: a rotation mid-confirmation shouldn't silently drop the question.
    var showResetLeaderboardConfirmation by rememberSaveable { mutableStateOf(false) }
    var showLicenses by rememberSaveable { mutableStateOf(false) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var showImportConfirmation by rememberSaveable { mutableStateOf(false) }

    val platform = LocalPlatformServices.current
    val alreadyDeveloper = stringResource(Res.string.settings_already_developer)
    // Index 0 is steps-away 1: resolved in composition, as the plural form and numerals need it.
    val stepsAway = (1 until SettingsViewModel.VERSION_TAP_TARGET).map { pluralStringResource(Res.plurals.settings_steps_from_developer, it, it) }
    LaunchedEffect(viewModel) {
        viewModel.versionTapMessages.collect { steps -> platform.showTransientMessage(if (steps == 0) alreadyDeveloper else stepsAway[steps - 1]) }
    }
    val progressFiles = platform.progressFiles
    val progressFilesStatus by viewModel.progressFilesStatus.collectAsStateWithLifecycle()
    val appName = stringResource(Res.string.app_name)

    ScreenScaffold(
        title = stringResource(Res.string.settings_title),
        onBack = onBack,
        modifier = modifier,
        scrollable = true,
        wide = true,
    ) {
        // Nothing but the title bar until the saved switches are back, rather than drawing them in
        // their default positions and then flipping the ones the player has changed. The page cover is
        // kept up over it meanwhile.
        val toggles = loadedToggles ?: return@ScreenScaffold HoldPageCover()
        val services = LocalGameServices.current
        val accountKey = (services.state.collectAsStateWithLifecycle().value as? GameServicesState.SignedIn)?.account?.accountKey
        val online = LocalAppContainer.current.onlineSettingsRepository
        val lastSaved by remember(accountKey) { accountKey?.let(online::cloudSavedAt) ?: flowOf(null) }.collectAsStateWithLifecycle(initialValue = null)
        val syncStatus = LocalOnlineSyncStatus.current
        val syncProgress by syncStatus.progress.collectAsStateWithLifecycle()
        val unreachable by syncStatus.unreachable.collectAsStateWithLifecycle()
        val signInSynced = syncStatus.signInSynced?.collectAsStateWithLifecycle()?.value.let { synced -> syncStatus.signInSynced == null || synced == accountKey }
        val onlineCard = @Composable {
            OnlineAccountCard(
                services = services,
                lastSavedEpochMillis = lastSaved,
                syncProgress = syncProgress,
                unreachable = unreachable,
                signInSynced = signInSynced,
            )
        }
        // In the online card's place: a build has one or the other, never both (PlatformServices.progressFiles).
        val progressCard = @Composable {
            if (progressFiles != null) {
                ProgressFilesCard(
                    onExport = { viewModel.exportProgress(progressFiles) },
                    onImport = { showImportConfirmation = true },
                    status = progressFilesStatus,
                )
            }
        }
        val switchesCard = @Composable {
            CompactSettingsCard {
                SwitchSetting(Symbols.VolumeUp, stringResource(Res.string.settings_sound), toggles.soundEnabled, viewModel::setSoundEnabled)
                SwitchSetting(Symbols.Vibration, stringResource(Res.string.settings_vibration), toggles.vibrationEnabled, viewModel::setVibrationEnabled)
                SwitchSetting(
                    Symbols.CheckCircle,
                    stringResource(Res.string.settings_confirm_leaving),
                    toggles.confirmBeforeLeavingGame,
                    viewModel::setConfirmBeforeLeavingGame,
                )
                SwitchSetting(
                    Symbols.Palette,
                    stringResource(Res.string.settings_tricolour_marks),
                    toggles.tricolourMarksEnabled,
                    viewModel::setTricolourMarksEnabled,
                )
            }
        }
        // The Orientation, the Animations level and its Advanced settings, on a page of their own.
        val graphicsCard = @Composable {
            CompactSettingsCard {
                PageLinkSetting(Symbols.AutoAwesome, stringResource(Res.string.settings_graphics), onGraphics)
            }
        }
        val resetsCard = @Composable {
            CompactSettingsCard {
                ResetSetting(
                    icon = Symbols.DeleteSweep,
                    label = stringResource(Res.string.settings_reset_leaderboard),
                    description = stringResource(Res.string.settings_reset_leaderboard_description),
                    onClick = { showResetLeaderboardConfirmation = true },
                )
            }
        }
        // Quiet links, not a Card section: these aren't settings, just where the standalone About
        // screen's content moved once it was folded in here. The version is the scaffold's footer.
        val aboutLink = @Composable { linkModifier: Modifier ->
            TextButton(
                onClick = {
                    showAbout = true
                    viewModel.onAboutViewed()
                },
                modifier = linkModifier,
            ) {
                Icon(
                    imageVector = Symbols.Info,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(stringResource(Res.string.about_title))
            }
        }
        val licencesLink = @Composable { linkModifier: Modifier ->
            TextButton(onClick = { showLicenses = true }, modifier = linkModifier) {
                Icon(
                    imageVector = Symbols.Gavel,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(stringResource(Res.string.licences_title))
            }
        }

        if (LocalLandscapePage.current) {
            // Side by side: the account, the switches, the resets, and About; then the Graphics row and Licences.
            TwoPanes(
                start = {
                    onlineCard()
                    progressCard()
                    switchesCard()
                    resetsCard()
                    aboutLink(Modifier.fillMaxWidth())
                },
                end = {
                    graphicsCard()
                    licencesLink(Modifier.fillMaxWidth())
                },
            )
        } else {
            onlineCard()
            progressCard()
            switchesCard()
            graphicsCard()
            resetsCard()
            // Side by side, each taking half the width, so a label that wraps at a large font stays inside its half.
            // Raised into the page's gap above it: a button's 48dp touch target has air round its text, which the gap
            // would otherwise double. The target stays whole, and inside the gap.
            Row(
                modifier = Modifier.fillMaxWidth().offset(y = LINKS_RAISE),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                aboutLink(Modifier.weight(1f))
                licencesLink(Modifier.weight(1f))
            }
        }

        // The version, last on the page, just below the links.
        FooterPill(
            stringResource(Res.string.settings_version, LocalAppContainer.current.buildInfo.versionName),
            // Raw taps, not clickable, and no ripple: the "Are You A Developer?" easter egg. A clickable would give
            // TalkBack a "double tap to activate" on a label that does nothing anyone could use - as AppLogo's dice.
            modifier = Modifier
                .offset(y = if (LocalLandscapePage.current) 0.dp else LINKS_RAISE * 2)
                .pointerInput(Unit) { detectTapGestures { viewModel.onVersionTapped() } },
        )
    }

    if (showLicenses) {
        LicensesDialog(onDismissRequest = { showLicenses = false })
    }

    if (showAbout) {
        AboutDialog(onDismissRequest = { showAbout = false }, debugCrashes = LocalAppContainer.current.buildInfo.isDebug)
    }

    if (showImportConfirmation && progressFiles != null) {
        DiceFiveDialog(
            icon = Symbols.FileOpen,
            title = stringResource(Res.string.settings_import_progress_title),
            message = stringResource(Res.string.settings_import_progress_message, appName),
            confirmLabel = stringResource(Res.string.settings_import_progress_confirm),
            onConfirm = {
                viewModel.importProgress(progressFiles)
                showImportConfirmation = false
            },
            dismissLabel = stringResource(Res.string.common_cancel),
            onDismiss = { showImportConfirmation = false },
            onDismissRequest = { showImportConfirmation = false },
        )
    }

    if (showResetLeaderboardConfirmation) {
        // The service's leaderboards can't be reset: say so while signed in to one.
        val gameServices = LocalGameServices.current
        val signedIn = gameServices.state.collectAsStateWithLifecycle().value is GameServicesState.SignedIn
        val message = stringResource(Res.string.settings_reset_leaderboard_message)
        DiceFiveDialog(
            icon = Symbols.RestartAlt,
            title = stringResource(Res.string.settings_reset_leaderboard_title),
            message = if (signedIn) "$message ${stringResource(Res.string.settings_reset_leaderboard_online_note, gameServices.serviceName)}" else message,
            confirmLabel = stringResource(Res.string.settings_reset_action),
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
