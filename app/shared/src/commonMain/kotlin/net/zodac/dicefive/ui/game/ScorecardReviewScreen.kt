package net.zodac.dicefive.ui.game

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.zodac.dicefive.model.GameState
import net.zodac.dicefive.ui.common.BackHandler
import net.zodac.dicefive.ui.common.ScreenScaffold

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
    ScreenScaffold(
        title = "Scorecards",
        onBack = onBack,
        modifier = modifier,
        scrollable = true,
        horizontalPadding = 16.dp,
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
