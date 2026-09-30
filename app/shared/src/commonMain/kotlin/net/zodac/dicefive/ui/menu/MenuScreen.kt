package net.zodac.dicefive.ui.menu

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.zodac.dicefive.ui.common.AppLogo
import net.zodac.dicefive.ui.common.BrandBackdrop
import net.zodac.dicefive.ui.common.PageColumn
import net.zodac.dicefive.ui.game.style.DiceCupStyles
import net.zodac.dicefive.ui.game.style.DiceStyles
import net.zodac.dicefive.ui.theme.DiceFiveTheme

/**
 * Taller than a default M3 button (40dp), which reads as a form control rather than a menu
 * destination at this size. Still a stock `Button` in every other respect - shape, colour roles,
 * ripple, state layers and typography all come from the theme.
 */
private val MENU_BUTTON_HEIGHT = 56.dp

/**
 * The split Play button's dividing line: the page showing through between its two halves, the same
 * 2dp M3's connected button groups leave between their buttons.
 */
private val PLAY_SPLIT_GAP = 2.dp

// Each half keeps the pill's rounded ends on its outer side and meets the other along a straight edge.
private val NEW_GAME_HALF_SHAPE = RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50)
private val CONTINUE_HALF_SHAPE = RoundedCornerShape(topEndPercent = 50, bottomEndPercent = 50)

@Composable
fun MenuScreen(
    hasInProgressGame: Boolean?,
    onContinue: () -> Unit,
    onNewGame: () -> Unit,
    onScores: () -> Unit,
    onStatistics: () -> Unit,
    onAchievements: () -> Unit,
    onStyles: () -> Unit,
    onRules: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    logoStyles: LogoStyles? = LogoStyles(DiceStyles.default, DiceCupStyles.default),
    onDiceTap: () -> Unit = {},
) {
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

            // Held invisible (still taking its space) until the saved-game check has answered, so the
            // buttons arrive together rather than Play flashing before it splits, or a gap popping in.
            val ready = hasInProgressGame != null
            val stackAlpha by animateFloatAsState(
                targetValue = if (ready) 1f else 0f,
                animationSpec = tween(durationMillis = 200),
                label = "menuButtonsAlpha",
            )
            Column(
                modifier = Modifier.fillMaxWidth()
                    .alpha(stackAlpha)
                    .then(if (ready) Modifier else Modifier.pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } }),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // One filled button for the primary action and tonal buttons for the rest: M3's
                // emphasis hierarchy, which also stops five identical slabs competing for the eye.
                PlayButton(hasInProgressGame = hasInProgressGame == true, onContinue = onContinue, onNewGame = onNewGame)
                MenuDestinationButton(label = "Achievements", onClick = onAchievements)
                MenuDestinationButton(label = "Leaderboard", onClick = onScores)
                MenuDestinationButton(label = "Statistics", onClick = onStatistics)
                MenuDestinationButton(label = "Styles", onClick = onStyles)
                MenuDestinationButton(label = "Rules", onClick = onRules)
                MenuDestinationButton(label = "Settings", onClick = onSettings)
            }

            Spacer(modifier = Modifier.weight(0.38f))
        }
    }
}

/**
 * The menu's primary action. With no saved game it's a single Play button; with one it's split down
 * the middle into New Game and Continue - the same width, height and pill outline, so nothing else on
 * the menu moves, and no dialog to ask which. Each half is its own stock `Button`, so each has its own
 * ripple, state layer and touch target, clipped to its own shape. Both stay filled: neither is
 * destructive (New Game only opens the setup form - the saved game is replaced only once a new one
 * actually starts), so neither needs to be played down against the other.
 */
@Composable
fun PlayButton(hasInProgressGame: Boolean, onContinue: () -> Unit, onNewGame: () -> Unit) {
    if (!hasInProgressGame) {
        Button(
            onClick = onNewGame,
            modifier = Modifier.fillMaxWidth().heightIn(min = MENU_BUTTON_HEIGHT),
        ) {
            Text(text = "Play", style = MaterialTheme.typography.titleMedium)
        }
        return
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(PLAY_SPLIT_GAP),
    ) {
        Button(
            onClick = onNewGame,
            modifier = Modifier.weight(1f).heightIn(min = MENU_BUTTON_HEIGHT),
            shape = NEW_GAME_HALF_SHAPE,
        ) {
            Text(text = "New Game", style = MaterialTheme.typography.titleMedium, maxLines = 1)
        }
        Button(
            onClick = onContinue,
            modifier = Modifier.weight(1f).heightIn(min = MENU_BUTTON_HEIGHT),
            shape = CONTINUE_HALF_SHAPE,
        ) {
            Text(text = "Continue", style = MaterialTheme.typography.titleMedium, maxLines = 1)
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
            onRules = {},
            onSettings = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MenuScreenInProgressPreview() {
    DiceFiveTheme {
        MenuScreen(
            hasInProgressGame = true,
            onContinue = {},
            onNewGame = {},
            onScores = {},
            onStatistics = {},
            onAchievements = {},
            onStyles = {},
            onRules = {},
            onSettings = {},
        )
    }
}
