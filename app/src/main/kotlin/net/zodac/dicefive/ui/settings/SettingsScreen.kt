package net.zodac.dicefive.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import net.zodac.dicefive.BuildConfig
import net.zodac.dicefive.ui.common.DiceFiveDialog
import net.zodac.dicefive.ui.common.ScreenScaffold

private const val GITHUB_URL = "https://github.com/zodac/DiceFive"

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val confirmBeforeLeavingGame by viewModel.confirmBeforeLeavingGame.collectAsState()
    val soundEnabled by viewModel.soundEnabled.collectAsState()
    val vibrationEnabled by viewModel.vibrationEnabled.collectAsState()
    val uriHandler = LocalUriHandler.current
    // Saveable: a rotation mid-confirmation shouldn't silently drop the question.
    var showResetAchievementsConfirmation by rememberSaveable { mutableStateOf(false) }
    var showResetLeaderboardConfirmation by rememberSaveable { mutableStateOf(false) }

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
            ListItem(
                leadingContent = {
                    Icon(imageVector = Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
                },
                headlineContent = { Text("Sound effects") },
                trailingContent = {
                    Switch(checked = soundEnabled, onCheckedChange = viewModel::setSoundEnabled)
                },
                // The Card already supplies the surface; an opaque ListItem container would paint a
                // second, slightly different one on top of it.
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            ListItem(
                leadingContent = {
                    Icon(imageVector = Icons.Filled.Vibration, contentDescription = null)
                },
                headlineContent = { Text("Vibration") },
                trailingContent = {
                    Switch(checked = vibrationEnabled, onCheckedChange = viewModel::setVibrationEnabled)
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            ListItem(
                leadingContent = {
                    Icon(imageVector = Icons.Filled.CheckCircle, contentDescription = null)
                },
                headlineContent = {
                    Text(
                        text = "Confirm before leaving game?",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        softWrap = false,
                    )
                },
                trailingContent = {
                    Switch(checked = confirmBeforeLeavingGame, onCheckedChange = viewModel::setConfirmBeforeLeavingGame)
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = { showResetAchievementsConfirmation = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = "Reset Achievements",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            Button(
                onClick = { showResetLeaderboardConfirmation = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = "Reset Leaderboard",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    softWrap = false,
                )
            }
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
                Text("View source on GitHub")
            }
        }
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
