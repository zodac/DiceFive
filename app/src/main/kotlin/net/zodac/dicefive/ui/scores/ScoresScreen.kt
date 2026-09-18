package net.zodac.dicefive.ui.scores

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import net.zodac.dicefive.data.scores.SCORES_PAGE_SIZE
import net.zodac.dicefive.data.scores.ScoreEntry

private val DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a")

@Composable
fun ScoresScreen(
    viewModel: ScoresViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "Scores", style = MaterialTheme.typography.headlineMedium)

        if (state.entries.isEmpty()) {
            Text(text = "No scores yet - play a game!")
        } else {
            HeaderRow()
            HorizontalDivider()
            LazyColumn(modifier = Modifier.weight(1f)) {
                itemsIndexed(state.entries, key = { _, entry -> entry.id }) { index, entry ->
                    val rank = state.pageIndex * SCORES_PAGE_SIZE + index + 1
                    ScoreRow(rank = rank, entry = entry)
                }
            }
            PaginationControls(
                pageIndex = state.pageIndex,
                totalPages = state.totalPages,
                hasPrevious = state.hasPreviousPage,
                hasNext = state.hasNextPage,
                onPrevious = viewModel::previousPage,
                onNext = viewModel::nextPage,
            )
        }
    }
}

@Composable
private fun HeaderRow() {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(text = "#", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
        Text(text = "Player", modifier = Modifier.weight(3f), style = MaterialTheme.typography.labelLarge)
        Text(text = "Score", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScoreRow(rank: Int, entry: ScoreEntry) {
    val tooltipState = rememberTooltipState()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(text = formatDate(entry.timestampEpochMillis)) } },
        state = tooltipState,
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text(text = rank.toString(), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(text = entry.playerName, modifier = Modifier.weight(3f), style = MaterialTheme.typography.bodyMedium)
            Text(text = entry.score.toString(), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun PaginationControls(
    pageIndex: Int,
    totalPages: Int,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        OutlinedButton(onClick = onPrevious, enabled = hasPrevious) { Text("Previous") }
        Text(text = "Page ${pageIndex + 1} of $totalPages", style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = onNext, enabled = hasNext) { Text("Next") }
    }
}

private fun formatDate(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(DATE_FORMATTER)
