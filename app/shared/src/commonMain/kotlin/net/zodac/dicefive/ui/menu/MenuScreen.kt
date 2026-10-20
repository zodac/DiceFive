package net.zodac.dicefive.ui.menu

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.zodac.dicefive.resources.Res
import net.zodac.dicefive.resources.menu_achievements
import net.zodac.dicefive.resources.menu_continue
import net.zodac.dicefive.resources.menu_leaderboard
import net.zodac.dicefive.resources.menu_new_game
import net.zodac.dicefive.resources.menu_play
import net.zodac.dicefive.resources.menu_rules
import net.zodac.dicefive.resources.menu_settings
import net.zodac.dicefive.resources.menu_statistics
import net.zodac.dicefive.resources.menu_styles
import net.zodac.dicefive.ui.common.AppLogo
import net.zodac.dicefive.ui.common.BrandBackdrop
import net.zodac.dicefive.ui.common.LocalLandscapePage
import net.zodac.dicefive.ui.common.PageColumn
import net.zodac.dicefive.ui.common.stringResource
import net.zodac.dicefive.ui.style.DiceCupStyles
import net.zodac.dicefive.ui.style.DiceStyles
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
        PageColumn(horizontalPadding = 28.dp, wide = true) {
            // Drawn in the player's own dice and cup. Held invisible (still taking its space, so
            // nothing below moves) until their picks have loaded, rather than flashing the defaults.
            val logo = @Composable {
                AppLogo(
                    modifier = Modifier.alpha(if (logoStyles == null) 0f else 1f),
                    diceStyle = logoStyles?.dice ?: DiceStyles.default,
                    cupStyle = logoStyles?.cup ?: DiceCupStyles.default,
                    // Googly eyes only: the one screen whose dice follow the phone's own tilt and shake.
                    pupilsFollowDevice = true,
                    // Tapping the cup itself shakes it, as in a game.
                    shakeCupOnTap = true,
                    onDiceTap = onDiceTap,
                )
            }

            // Held invisible (still taking its space) until the saved-game check has answered, so the
            // buttons arrive together rather than Play flashing before it splits, or a gap popping in.
            val ready = hasInProgressGame != null
            val stackAlpha by animateFloatAsState(
                targetValue = if (ready) 1f else 0f,
                animationSpec = tween(durationMillis = 200),
                label = "menuButtonsAlpha", // i18n: not translated - an animation label, not shown
            )
            val destinations = listOf(
                stringResource(Res.string.menu_achievements) to onAchievements,
                stringResource(Res.string.menu_styles) to onStyles,
                stringResource(Res.string.menu_leaderboard) to onScores,
                stringResource(Res.string.menu_statistics) to onStatistics,
                stringResource(Res.string.menu_rules) to onRules,
                stringResource(Res.string.menu_settings) to onSettings,
            )
            // [perRow] destinations a row: one, under each other, or two side by side in landscape, where seven in a column
            // don't fit the height.
            val buttons = @Composable { perRow: Int ->
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .alpha(stackAlpha)
                        .then(if (ready) Modifier else Modifier.pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } }),
                    verticalArrangement = Arrangement.spacedBy(MENU_BUTTON_GAP),
                ) {
                    // One filled button for the primary action and tonal buttons for the rest: M3's
                    // emphasis hierarchy, which also stops five identical slabs competing for the eye.
                    PlayButton(hasInProgressGame = hasInProgressGame == true, onContinue = onContinue, onNewGame = onNewGame)
                    for (row in destinations.chunked(perRow)) {
                        // Height set by the taller button, so a label that wraps at a large font doesn't leave its neighbour short.
                        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(MENU_BUTTON_GAP)) {
                            for ((label, onClick) in row) {
                                MenuDestinationButton(label = label, onClick = onClick, modifier = Modifier.weight(1f).fillMaxHeight())
                            }
                        }
                    }
                }
            }

            if (LocalLandscapePage.current) {
                // Side by side: the logo, then the buttons, each centred in its half of the height.
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) { logo() }
                    Box(modifier = Modifier.weight(1f)) { buttons(2) }
                }
                return@PageColumn
            }

            // Weighted spacers rather than fixed padding: the logo sits in the lit upper third and
            // the button stack just below the middle, on a tall phone and a short one alike.
            Spacer(modifier = Modifier.weight(0.22f))
            logo()
            Spacer(modifier = Modifier.height(48.dp))
            buttons(1)
            Spacer(modifier = Modifier.weight(0.38f))
        }
    }
}

/** The gap between the menu's buttons. */
private val MENU_BUTTON_GAP = 12.dp

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
            Text(text = stringResource(Res.string.menu_play), style = MaterialTheme.typography.titleMedium)
        }
        return
    }

    // Height set by the taller half, so a label that wraps at a large font doesn't leave the other short.
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(PLAY_SPLIT_GAP),
    ) {
        Button(
            onClick = onNewGame,
            modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = MENU_BUTTON_HEIGHT),
            shape = NEW_GAME_HALF_SHAPE,
        ) {
            Text(text = stringResource(Res.string.menu_new_game), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        }
        Button(
            onClick = onContinue,
            modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = MENU_BUTTON_HEIGHT),
            shape = CONTINUE_HALF_SHAPE,
        ) {
            Text(text = stringResource(Res.string.menu_continue), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun MenuDestinationButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = MENU_BUTTON_HEIGHT),
    ) {
        Text(text = label, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
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
