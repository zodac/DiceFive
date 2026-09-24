package net.zodac.dicefive.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import net.zodac.dicefive.BuildConfig
import net.zodac.dicefive.data.settings.Theme
import net.zodac.dicefive.ui.common.DiceFiveDialog
import net.zodac.dicefive.ui.common.ScreenScaffold
import net.zodac.dicefive.ui.common.SegmentedChoiceRow

private const val GITHUB_URL = "https://github.com/zodac/DiceFive"

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme by viewModel.theme.collectAsState()
    val confirmBeforeLeavingGame by viewModel.confirmBeforeLeavingGame.collectAsState()
    val userName by viewModel.userName.collectAsState()
    val uriHandler = LocalUriHandler.current
    // Saveable: a rotation mid-confirmation shouldn't silently drop the question.
    var showResetAchievementsConfirmation by rememberSaveable { mutableStateOf(false) }
    var showResetScoresConfirmation by rememberSaveable { mutableStateOf(false) }

    // Opening the GitHub link backgrounds the app (a browser takes over), and Compose's own frame
    // clock - which every banner's fade-in/hold/fade-out animation runs on - keeps ticking through
    // that background stretch, so unlocking immediately on tap has the banner play out its entire
    // lifetime off-screen: gone by the time the user switches back. Instead, tapping just arms
    // this flag, and the actual unlock (and the banner it raises) waits for the ON_RESUME that
    // fires when the user returns to the app - the first frame the banner could actually be seen.
    var pendingGithubUnlockCheck by remember { mutableStateOf(false) }
    val onGithubLinkOpened by rememberUpdatedState(viewModel::onGithubLinkOpened)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && pendingGithubUnlockCheck) {
                pendingGithubUnlockCheck = false
                onGithubLinkOpened()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    ScreenScaffold(title = "Settings", onBack = onBack, modifier = modifier, scrollable = true) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Profile",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
            )
            OutlinedTextField(
                value = userName,
                onValueChange = viewModel::setUserName,
                label = { Text("Your name") },
                placeholder = { Text("Player 1") },
                singleLine = true,
                // Same reasoning as CompactNameField in GameSetupScreen: names read as
                // Capitalized Words, and this keeps the keyboard's shift state matching that
                // even after the field is cleared back to empty.
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Appearance",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
            )
            ThemeSelector(selected = theme, onSelect = viewModel::setTheme)
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Gameplay",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
            )
            ListItem(
                headlineContent = { Text("Confirm before leaving") },
                supportingContent = { Text("Ask first when backing out of a game in progress") },
                trailingContent = {
                    Switch(checked = confirmBeforeLeavingGame, onCheckedChange = viewModel::setConfirmBeforeLeavingGame)
                },
                // The Card already supplies the surface; an opaque ListItem container would paint a
                // second, slightly different one on top of it.
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Reset",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
            )
            ListItem(
                headlineContent = { Text("Reset achievements") },
                supportingContent = { Text("Clear every unlock and all progress on this device") },
                trailingContent = {
                    TextButton(
                        onClick = { showResetAchievementsConfirmation = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Text("Reset")
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            ListItem(
                headlineContent = { Text("Reset leaderboard & statistics") },
                supportingContent = { Text("Clear every recorded score on this device") },
                trailingContent = {
                    TextButton(
                        onClick = { showResetScoresConfirmation = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Text("Reset")
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }

        // Quiet footer, not a Card section: version and the project link aren't settings, just
        // where the standalone About screen's content moved once it was folded in here.
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Version ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = {
                    uriHandler.openUri(GITHUB_URL)
                    pendingGithubUnlockCheck = true
                },
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text("View on GitHub")
            }
        }
    }

    if (showResetAchievementsConfirmation) {
        DiceFiveDialog(
            icon = Icons.Filled.RestartAlt,
            title = "Reset achievements?",
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

    if (showResetScoresConfirmation) {
        DiceFiveDialog(
            icon = Icons.Filled.RestartAlt,
            title = "Reset leaderboard & statistics?",
            message = "Every recorded score will be deleted, clearing the Leaderboard and Statistics screens. " +
                "This can't be undone. Achievements and settings are not affected, though any achievement " +
                "progress measured against the leaderboard will start over.",
            confirmLabel = "Reset",
            onConfirm = {
                viewModel.resetScores()
                showResetScoresConfirmation = false
            },
            dismissLabel = "Cancel",
            onDismiss = { showResetScoresConfirmation = false },
            onDismissRequest = { showResetScoresConfirmation = false },
        )
    }
}

/**
 * Three mutually exclusive options that each fit in a word: M3 points at a segmented button for
 * exactly this, and it puts the whole choice on one line instead of a three-row radio group.
 */
@Composable
private fun ThemeSelector(selected: Theme, onSelect: (Theme) -> Unit) {
    Column(modifier = Modifier.padding(16.dp)) {
        SegmentedChoiceRow(
            options = Theme.entries,
            selected = selected,
            onSelect = onSelect,
            label = { it.name.lowercase().replaceFirstChar(Char::uppercase) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
