package net.zodac.dicefive.ui.game

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.zodac.dicefive.model.GameState
import net.zodac.dicefive.resources.Res
import net.zodac.dicefive.resources.game_review_title
import net.zodac.dicefive.ui.common.BackHandler
import net.zodac.dicefive.ui.common.ScreenScaffold
import net.zodac.dicefive.ui.common.stringResource

/**
 * Read-only view of every player's finished scorecard, reached from [GameOverScreen]'s "Review
 * Scorecards" button. Reuses [PlayerHeaderBar] purely as a player switcher (there's no "current
 * turn" any more, so the tapped tab is just passed through as [PlayerHeaderBar]'s active tab) and
 * [ReadOnlyScoreboard] for the card itself, same as tapping another player's tab mid-game.
 */
@Composable
fun ScorecardReviewScreen(
    state: GameState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)

    var reviewedPlayerIndex by remember { mutableIntStateOf(0) }
    val reviewedPlayer = state.players.getOrElse(reviewedPlayerIndex) { state.players.first() }

    // The same frame as every other page: the title in the app bar, and its back arrow (rather than a
    // button at the foot of the page) returning to the results. The game screen's narrower margin, so
    // the board's pieces come out the size they were in play.
    BoxWithConstraints(modifier = modifier) {
        // A tall card (D20's) with the tray under it can overrun the screen, which would scroll a page the other modes fit on: the
        // tray's mat is made shallower, as far as MIN_SCATTER_HEIGHT, to take up the shortfall.
        val density = LocalDensity.current
        val insets = with(density) { WindowInsets.safeDrawing.getTop(this).toDp() + WindowInsets.safeDrawing.getBottom(this).toDp() }
        val trayWidth = maxWidth - REVIEW_PADDING * 2
        val needed = insets + TopAppBarDefaults.TopAppBarExpandedHeight + REVIEW_HEADER_HEIGHT + gameBoardHeight(reviewedPlayer.categories) +
            (if (reviewedPlayer.lastRoll != null) diceTrayHeight(trayWidth) else 0.dp) + REVIEW_SECTION_GAP * 2
        val shortfall = (needed - maxHeight).coerceIn(0.dp, SCATTER_AREA_HEIGHT - MIN_SCATTER_HEIGHT)
        CompositionLocalProvider(LocalScatterHeight provides SCATTER_AREA_HEIGHT - shortfall) {
            ScreenScaffold(
                title = stringResource(Res.string.game_review_title),
                onBack = onBack,
                scrollable = true,
                horizontalPadding = REVIEW_PADDING,
            ) {
                PlayerHeaderBar(
                    players = state.players,
                    currentPlayerIndex = reviewedPlayerIndex,
                    viewedPlayerIndex = null,
                    enabled = true,
                    onPlayerTap = { reviewedPlayerIndex = it },
                )

                ReadOnlyScoreboard(player = reviewedPlayer)

                // Same DiceTray call GameScreen's own read-only view uses - see its comment on why this
                // isn't folded into ReadOnlyScoreboard itself.
                reviewedPlayer.lastRoll?.let { dice ->
                    DiceTray(
                        dice = dice,
                        gameMode = reviewedPlayer.gameMode,
                        enabled = false,
                        showDice = true,
                        rolling = false,
                        onToggleHold = {},
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

private val REVIEW_PADDING = 16.dp
private val REVIEW_SECTION_GAP = 16.dp
private val REVIEW_HEADER_HEIGHT = 82.dp
