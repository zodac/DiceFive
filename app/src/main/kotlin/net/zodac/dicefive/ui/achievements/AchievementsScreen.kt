package net.zodac.dicefive.ui.achievements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.zodac.dicefive.ui.common.ScreenScaffold
import net.zodac.dicefive.ui.game.style.IvoryDiceStyle

/** Placeholder screen - the achievement list and trigger rules are TBC. See .claude/DESIGN.md. */
@Composable
fun AchievementsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    ScreenScaffold(title = "Achievements", onBack = onBack, modifier = modifier, scrollable = true) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Faded dice standing in for the locked-achievement art that will go here.
                        IvoryDiceStyle.Die(value = 5, held = false, modifier = Modifier.size(38.dp).rotate(-10f).alpha(0.35f))
                        IvoryDiceStyle.Die(value = 5, held = false, modifier = Modifier.size(38.dp).alpha(0.35f))
                        IvoryDiceStyle.Die(value = 5, held = false, modifier = Modifier.size(38.dp).rotate(10f).alpha(0.35f))
                    }
                }

                Text(text = "Coming soon", style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = "Trophies for big Yahtzees, perfect upper sections and long win streaks will be tracked here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
