package net.zodac.dicefive.ui.achievements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import net.zodac.dicefive.ui.common.LazyListScrollbar
import net.zodac.dicefive.ui.common.ScreenScaffold

/**
 * Date *and* time: the unlocked half sorts newest-first, and several achievements usually land in
 * the same burst at the end of a game, so a date alone can't explain the order they're listed in.
 * Deliberately the same pattern the Leaderboard uses for a score's timestamp.
 */
private val UNLOCKED_AT_FORMATTER = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a")

/**
 * Every achievement the app tracks, locked ones first: those are the ones there's still something
 * to do about, and burying them under a growing list of trophies would be the wrong way round.
 *
 * The locked half is grouped by theme and runs easiest-first within each - the catalogue's own
 * order - so a ladder reads as a ladder. The unlocked half is a history instead, so it's flat and
 * newest-first. The chip hides it entirely.
 *
 * Achievements are per device - there is no per-player breakdown here because there is no
 * per-player record. Resetting them lives in Settings, with the other destructive controls.
 */
@Composable
fun AchievementsScreen(
    viewModel: AchievementsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()

    ScreenScaffold(title = "Achievements", onBack = onBack, modifier = modifier) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "${state.unlocked.size} of ${state.totalCount} unlocked",
                    style = MaterialTheme.typography.titleMedium,
                )
                FilterChip(
                    selected = state.hideUnlocked,
                    onClick = { viewModel.setHideUnlocked(!state.hideUnlocked) },
                    label = { Text("Hide unlocked") },
                    leadingIcon = if (state.hideUnlocked) {
                        { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else {
                        null
                    },
                )
            }
        }

        val listState = rememberLazyListState()
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                // Room on the right for the scrollbar so it doesn't sit on top of a card's edge.
                contentPadding = PaddingValues(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.lockedGroups.isNotEmpty()) {
                    item(key = "locked-header") { SectionHeader("Locked (${state.lockedCount})") }
                    for (group in state.lockedGroups) {
                        item(key = "group-${group.category.name}") { GroupHeader(group.category.label) }
                        items(group.items, key = { it.achievement.id }) { AchievementRow(it) }
                    }
                }

                if (!state.hideUnlocked && state.unlocked.isNotEmpty()) {
                    item(key = "unlocked-header") { SectionHeader("Unlocked (${state.unlocked.size})") }
                    items(state.unlocked, key = { it.achievement.id }) { AchievementRow(it) }
                }

                if (state.lockedGroups.isEmpty() && (state.hideUnlocked || state.unlocked.isEmpty())) {
                    item(key = "empty") {
                        Text(
                            text = if (state.hideUnlocked) {
                                "Everything's unlocked. Nothing left to chase!"
                            } else {
                                "No achievements yet - play a game!"
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                        )
                    }
                }
            }
            LazyListScrollbar(listState = listState)
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

/** A theme within the locked half - quieter than the Locked/Unlocked split it sits under. */
@Composable
private fun GroupHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 2.dp),
    )
}

@Composable
private fun AchievementRow(item: AchievementItem) {
    val unlocked = item.unlockedAt != null

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            // Earned ones are lifted off the page; the rest stay at the page's own level.
            containerColor = if (unlocked) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                imageVector = if (unlocked) Icons.Filled.EmojiEvents else Icons.Filled.Lock,
                contentDescription = null,
                tint = if (unlocked) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(26.dp).padding(top = 2.dp),
            )

            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = item.achievement.title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = item.achievement.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                when {
                    item.unlockedAt != null -> Text(
                        text = "Unlocked ${formatUnlockedAt(item.unlockedAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )

                    item.achievement.hasProgressBar -> ProgressRow(item)
                }
            }
        }
    }
}

/** The progress bar on a locked, countable achievement - a win streak, or a running total. */
@Composable
private fun ProgressRow(item: AchievementItem) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
        LinearProgressIndicator(
            progress = { item.progressFraction },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "${item.progress.grouped()} of ${item.achievement.target.grouped()}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** Thousand separators, so "34,521 of 100,000" doesn't have to be counted digit by digit. */
internal fun Int.grouped(): String = NumberFormat.getIntegerInstance().format(this)

private fun formatUnlockedAt(epochMillis: Long): String =
    UNLOCKED_AT_FORMATTER.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
