package net.zodac.dicefive.ui.achievements

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import net.zodac.dicefive.model.AchievementVisibility
import net.zodac.dicefive.ui.common.ScreenScaffold

/**
 * Date *and* time: several achievements can land in the same burst at the end of a game, so a date
 * alone wouldn't tell two rows in the same burst apart. Deliberately the same pattern the
 * Leaderboard uses for a score's timestamp.
 */
private val UNLOCKED_AT_FORMATTER = DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm")

/**
 * Every achievement the app tracks, in one list - grouped by theme and, within a theme,
 * easiest-first, exactly as the catalogue declares them. An unlocked achievement stays in its
 * ladder rather than jumping to a separate section; it's just highlighted (its own icon in place
 * of the generic question mark every locked row shows, plus a raised card) so what's already been
 * earned is still obvious at a glance. See [icon] (`AchievementIcons.kt`) for the per-achievement
 * mapping.
 *
 * `Achievement.visibility` gates how much of a locked row is shown: a secret achievement is
 * filtered out of [AchievementsViewModel]'s state entirely - and its unlocked/total counts -
 * while still locked, so it never appears here (or anywhere else) until it's already been
 * earned; a hidden one still appears, title and all, but its description reads "???" until then.
 *
 * Achievements are per device - there is no per-player breakdown here because there is no
 * per-player record. Resetting them lives in Settings, with the other destructive controls.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AchievementsScreen(
    viewModel: AchievementsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()

    ScreenScaffold(title = "Achievements", onBack = onBack, modifier = modifier) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "${state.unlockedCount} of ${state.totalCount} unlocked",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.groups.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = "Everything's unlocked. Nothing left to chase!",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                    )
                }
            } else {
                for (group in state.groups) {
                    stickyHeader(key = "group-${group.category.name}") { GroupHeader(group.category.label) }
                    items(group.items, key = { it.achievement.id }) { AchievementRow(it) }
                }
            }
        }
    }
}

/**
 * A theme's subheader - quiet, since the catalogue's own order is what does the real organising.
 * Pinned to the top of the list while its category scrolls by, so it's a plain [Card] rather than
 * the bare [Text] this would otherwise be - the same rounded, opaque shape every other surface on
 * this screen uses - since rows now scroll directly underneath it and would show through bare text.
 */
@Composable
private fun GroupHeader(text: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
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
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val tint = if (unlocked) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            Box(
                modifier = Modifier.size(40.dp).border(width = 1.dp, color = tint),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (unlocked) item.achievement.icon else LOCKED_ACHIEVEMENT_ICON,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(22.dp),
                )
            }

            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = item.achievement.title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = if (item.achievement.visibility == AchievementVisibility.HIDDEN && !unlocked) {
                        "???"
                    } else {
                        item.achievement.description
                    },
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
