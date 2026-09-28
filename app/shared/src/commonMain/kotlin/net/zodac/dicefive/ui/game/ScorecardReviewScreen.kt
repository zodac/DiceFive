package net.zodac.dicefive.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.zodac.dicefive.model.GameState
import net.zodac.dicefive.ui.common.BackHandler

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

    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "Scorecards",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

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

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) {
            Text(text = "Back to Results", style = MaterialTheme.typography.titleMedium)
        }
    }
}
