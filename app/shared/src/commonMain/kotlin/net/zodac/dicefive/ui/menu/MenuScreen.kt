package net.zodac.dicefive.ui.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.zodac.dicefive.ui.common.AppLogo
import net.zodac.dicefive.ui.common.BrandBackdrop
import net.zodac.dicefive.ui.common.DiceFiveDialog
import net.zodac.dicefive.ui.common.PageColumn
import net.zodac.dicefive.ui.common.RulesDialog
import net.zodac.dicefive.ui.game.style.DiceCupStyles
import net.zodac.dicefive.ui.game.style.DiceStyles
import net.zodac.dicefive.ui.theme.DiceFiveTheme

/**
 * Taller than a default M3 button (40dp), which reads as a form control rather than a menu
 * destination at this size. Still a stock `Button` in every other respect - shape, colour roles,
 * ripple, state layers and typography all come from the theme.
 */
private val MENU_BUTTON_HEIGHT = 56.dp

@Composable
fun MenuScreen(
    hasInProgressGame: Boolean,
    onContinue: () -> Unit,
    onNewGame: () -> Unit,
    onScores: () -> Unit,
    onStatistics: () -> Unit,
    onAchievements: () -> Unit,
    onStyles: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    logoStyles: LogoStyles? = LogoStyles(DiceStyles.default, DiceCupStyles.default),
    onDiceTap: () -> Unit = {},
) {
    var showResumeDialog by remember { mutableStateOf(false) }
    var showRulesDialog by remember { mutableStateOf(false) }

    if (showRulesDialog) {
        RulesDialog(onDismissRequest = { showRulesDialog = false })
    }

    if (showResumeDialog) {
        DiceFiveDialog(
            icon = Icons.Filled.RestartAlt,
            title = "Resume game?",
            message = "You have a game in progress. Continue where you left off, or start fresh?",
            confirmLabel = "Continue",
            onConfirm = { showResumeDialog = false; onContinue() },
            dismissLabel = "New Game",
            onDismiss = { showResumeDialog = false; onNewGame() },
            // Backing out must not silently discard the saved game by starting a new one.
            onDismissRequest = { showResumeDialog = false },
        )
    }

    // The menu alone has its backdrop's dice drifting about; every other screen keeps them still.
    BrandBackdrop(modifier = modifier, driftingDice = true) {
        PageColumn(horizontalPadding = 28.dp) {
            // Weighted spacers rather than fixed padding: the logo sits in the lit upper third and
            // the button stack just below the middle, on a tall phone and a short one alike.
            Spacer(modifier = Modifier.weight(0.22f))

            // Drawn in the player's own dice and cup. Held invisible (still taking its space, so
            // nothing below moves) until their picks have loaded, rather than flashing the defaults.
            AppLogo(
                modifier = Modifier.alpha(if (logoStyles == null) 0f else 1f),
                diceStyle = logoStyles?.dice ?: DiceStyles.default,
                cupStyle = logoStyles?.cup ?: DiceCupStyles.default,
                // Googly eyes only: the one screen whose dice follow the phone's own tilt and shake.
                pupilsFollowDevice = true,
                onDiceTap = onDiceTap,
            )

            Spacer(modifier = Modifier.height(48.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // One filled button for the primary action and tonal buttons for the rest: M3's
                // emphasis hierarchy, which also stops five identical slabs competing for the eye.
                Button(
                    onClick = { if (hasInProgressGame) showResumeDialog = true else onNewGame() },
                    modifier = Modifier.fillMaxWidth().heightIn(min = MENU_BUTTON_HEIGHT),
                ) {
                    Text(text = "Play", style = MaterialTheme.typography.titleMedium)
                }
                MenuDestinationButton(label = "Achievements", onClick = onAchievements)
                MenuDestinationButton(label = "Leaderboard", onClick = onScores)
                MenuDestinationButton(label = "Statistics", onClick = onStatistics)
                MenuDestinationButton(label = "Styles", onClick = onStyles)
                MenuDestinationButton(label = "Rules", onClick = { showRulesDialog = true })
                MenuDestinationButton(label = "Settings", onClick = onSettings)
            }

            Spacer(modifier = Modifier.weight(0.38f))
        }
    }
}

@Composable
private fun MenuDestinationButton(label: String, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = MENU_BUTTON_HEIGHT),
    ) {
        Text(text = label, style = MaterialTheme.typography.titleMedium)
    }
}

@Preview(showBackground = true)
@Composable
private fun MenuScreenPreview() {
    DiceFiveTheme {
        MenuScreen(
            hasInProgressGame = false,
            onContinue = {},
            onNewGame = {},
            onScores = {},
            onStatistics = {},
            onAchievements = {},
            onStyles = {},
            onSettings = {},
        )
    }
}
