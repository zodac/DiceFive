package net.zodac.dicefive.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import net.zodac.dicefive.online.ImportStep
import net.zodac.dicefive.online.MergeStep
import net.zodac.dicefive.resources.Res
import net.zodac.dicefive.resources.app_name
import net.zodac.dicefive.resources.settings_export_progress
import net.zodac.dicefive.resources.settings_export_progress_action
import net.zodac.dicefive.resources.settings_import_progress
import net.zodac.dicefive.resources.settings_import_progress_action
import net.zodac.dicefive.resources.settings_progress_export_failed
import net.zodac.dicefive.resources.settings_progress_exported
import net.zodac.dicefive.resources.settings_progress_import_failed
import net.zodac.dicefive.resources.settings_progress_imported
import net.zodac.dicefive.resources.settings_progress_importing_cd
import net.zodac.dicefive.resources.settings_progress_newer
import net.zodac.dicefive.resources.settings_progress_not_a_file
import net.zodac.dicefive.resources.settings_progress_step_opening
import net.zodac.dicefive.ui.common.delayWhileResumed
import net.zodac.dicefive.ui.common.stringResource
import net.zodac.dicefive.ui.icons.Symbols
import org.jetbrains.compose.resources.StringResource

/**
 * The Settings card for progress files, in a build that has them ([net.zodac.dicefive.platform.PlatformServices.progressFiles]):
 * Export progress, which opens the platform's "save as" picker, and Import progress, which asks first ([onImport]
 * opens the confirmation). It sits where the online service's card would, in a build that has none.
 *
 * Each row is one compact button row, its label alone, as [SwitchSetting]'s are one switch: TalkBack hears "Export
 * progress, button, double-tap to export".
 *
 * Under the rows, [status]: while an import runs, its step and how far through ("Merging scores (5/5)") after a spinner
 * and above a bar, as the online card shows a sync ([StepProgress]); then how the last export or import ended, in red
 * when it went wrong. It is a polite live region, spoken "Importing progress" while an import runs (not each step,
 * which come and go too fast to hear) and then its outcome.
 */
@Composable
fun ProgressFilesCard(onExport: () -> Unit, onImport: () -> Unit, modifier: Modifier = Modifier, status: ProgressFilesStatus? = null) {
    CompactSettingsCard(modifier = modifier) {
        ProgressFileSetting(
            icon = Symbols.Download,
            label = stringResource(Res.string.settings_export_progress),
            actionLabel = stringResource(Res.string.settings_export_progress_action),
            onClick = onExport,
        )
        ProgressFileSetting(
            icon = Symbols.FileOpen,
            label = stringResource(Res.string.settings_import_progress),
            actionLabel = stringResource(Res.string.settings_import_progress_action),
            onClick = onImport,
        )
        rememberPacedStatus(status)?.let { StatusLines(it) }
    }
}

/** The least an import's progress shows for, the bar gliding through its steps: shared out between them. */
internal const val IMPORT_SHOWN_MILLIS = 1_000L

/** Each step's share of [IMPORT_SHOWN_MILLIS], however many steps an import has: rounded up, so they never add up to less. */
internal val IMPORT_STEP_MILLIS: Long = (IMPORT_SHOWN_MILLIS + ImportStep.entries.size - 1) / ImportStep.entries.size

/**
 * [status] as the card shows it: a local import is over in a moment, and a bar that only flashes doesn't look like it
 * did anything, so each step shows for at least [IMPORT_STEP_MILLIS] ([nextShownStatus]), counted only while the app
 * is in front ([delayWhileResumed]). The import itself isn't slowed: the card catches up with it. A status there when
 * the card first composes (the screen turned) is shown as it is.
 */
@Composable
private fun rememberPacedStatus(status: ProgressFilesStatus?): ProgressFilesStatus? {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val shown = remember { mutableStateOf(status) }
    val latest = rememberUpdatedState(status)
    LaunchedEffect(lifecycle) {
        snapshotFlow { latest.value }.collect {
            while (true) {
                val next = nextShownStatus(shown.value, latest.value) ?: break
                shown.value = next.status
                if (next.status is ProgressFilesStatus.Importing) lifecycle.delayWhileResumed(IMPORT_STEP_MILLIS)
            }
        }
    }
    return shown.value
}

/** What [nextShownStatus] moves the card on to: [status], null to clear it. */
internal data class ShownStatus(val status: ProgressFilesStatus?)

/**
 * What the card shows after [shown], heading for [target] (the view model's status), one step at a time: or null to
 * stay as it is.
 * - A new attempt (no status) clears it at once.
 * - An import walks its steps in order, each shown in turn even if the import has passed it: from the first, unless
 *   one is already showing, and never ahead of the import.
 * - A successful import walks the steps it has left, then says so; any other ending is said straight after the step
 *   showing.
 */
internal fun nextShownStatus(shown: ProgressFilesStatus?, target: ProgressFilesStatus?): ShownStatus? {
    if (shown == target) return null
    if (target == null) return ShownStatus(null)
    val step = (shown as? ProgressFilesStatus.Importing)?.step
    val next = step?.let { ImportStep.entries.getOrNull(it.ordinal + 1) } ?: ImportStep.entries.first().takeIf { step == null }
    return when (target) {
        is ProgressFilesStatus.Importing -> next?.takeIf { it <= target.step }?.let { ShownStatus(ProgressFilesStatus.Importing(it)) }
        is ProgressFilesStatus.Finished ->
            if (target.outcome == ProgressOutcome.IMPORTED && next != null) ShownStatus(ProgressFilesStatus.Importing(next)) else ShownStatus(target)
    }
}

/** [ProgressFilesCard]'s [status], under the rows' text. */
@Composable
private fun StatusLines(status: ProgressFilesStatus) {
    val spoken: String
    val content: @Composable () -> Unit
    when (status) {
        is ProgressFilesStatus.Importing -> {
            val number = status.step.ordinal + 1
            val total = ImportStep.entries.size
            val step = stringResource(status.step.label)
            spoken = stringResource(Res.string.settings_progress_importing_cd)
            content = { StepProgress(step, number, total) }
        }
        is ProgressFilesStatus.Finished -> {
            val text = status.outcome.message()
            spoken = text
            content = {
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (status.outcome.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    Box(
        // 16dp above the card's edge, with the card's own padding under the rows.
        modifier = Modifier.padding(start = TEXT_START, end = 16.dp, bottom = 16.dp - SETTING_ROW_VERTICAL_PADDING).clearAndSetSemantics {
            liveRegion = LiveRegionMode.Polite
            contentDescription = spoken
        },
    ) {
        content()
    }
}

/** What the card says once an export or import has ended. */
@Composable
private fun ProgressOutcome.message(): String {
    val appName = stringResource(Res.string.app_name)
    return when (this) {
        ProgressOutcome.EXPORTED -> stringResource(Res.string.settings_progress_exported)
        ProgressOutcome.IMPORTED -> stringResource(Res.string.settings_progress_imported)
        ProgressOutcome.NOT_A_PROGRESS_FILE -> stringResource(Res.string.settings_progress_not_a_file, appName)
        ProgressOutcome.FROM_NEWER_APP -> stringResource(Res.string.settings_progress_newer, appName)
        ProgressOutcome.EXPORT_FAILED -> stringResource(Res.string.settings_progress_export_failed)
        ProgressOutcome.IMPORT_FAILED -> stringResource(Res.string.settings_progress_import_failed)
    }
}

/** What the card calls each step: a merge as every merge's progress names it ([MergeStep.label]). */
private val ImportStep.label: StringResource
    get() = when (this) {
        ImportStep.OPENING -> Res.string.settings_progress_step_opening
        ImportStep.SETTINGS, ImportStep.GAME, ImportStep.ACHIEVEMENTS, ImportStep.SCORES -> checkNotNull(merge).label
    }

/** One row of [ProgressFilesCard], in the shape of Settings' other action rows: the whole row is the button. */
@Composable
private fun ProgressFileSetting(icon: ImageVector, label: String, actionLabel: String, onClick: () -> Unit) {
    SettingRow(
        icon = icon,
        headline = { SettingLabel(label) },
        modifier = Modifier.clickable(role = Role.Button, onClickLabel = actionLabel, onClick = onClick),
    )
}
