package net.zodac.dicefive.ui.statistics

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import net.zodac.dicefive.resources.Res
import net.zodac.dicefive.resources.stats_play_time
import net.zodac.dicefive.resources.stats_play_time_hours_minutes
import net.zodac.dicefive.resources.stats_play_time_hours_spoken
import net.zodac.dicefive.resources.stats_play_time_minutes
import net.zodac.dicefive.resources.stats_play_time_minutes_spoken
import net.zodac.dicefive.resources.stats_play_time_seconds
import net.zodac.dicefive.resources.stats_play_time_seconds_spoken
import net.zodac.dicefive.ui.common.grouped
import net.zodac.dicefive.ui.common.pluralStringResource
import net.zodac.dicefive.ui.common.spokenList
import net.zodac.dicefive.ui.common.stringResource

private const val SECONDS_PER_MINUTE = 60
private const val MINUTES_PER_HOUR = 60

/** How long until [seconds] of play time would show differently: seconds count up for the first minute, then minutes do. */
internal fun untilPlayTimeChanges(seconds: Int): Duration =
    (if (seconds < SECONDS_PER_MINUTE) 1 else SECONDS_PER_MINUTE - seconds % SECONDS_PER_MINUTE).seconds

/**
 * The time played on every device, whatever the Statistics page: seconds for the first minute, then hours and minutes,
 * however many hours, with the seconds dropped. Spoken in words, without a part that's zero, and only when it's reached:
 * it isn't a live region, so the count isn't read out as it goes.
 */
@Composable
internal fun PlayTimeLine(seconds: Int, modifier: Modifier = Modifier) {
    val totalMinutes = seconds / SECONDS_PER_MINUTE
    val hours = totalMinutes / MINUTES_PER_HOUR
    val minutes = totalMinutes % MINUTES_PER_HOUR
    val shown = when {
        hours > 0 -> stringResource(Res.string.stats_play_time_hours_minutes, hours.grouped(), minutes)
        minutes > 0 -> stringResource(Res.string.stats_play_time_minutes, minutes)
        else -> stringResource(Res.string.stats_play_time_seconds, seconds)
    }
    val hoursSpoken = pluralStringResource(Res.plurals.stats_play_time_hours_spoken, hours, hours)
    val minutesSpoken = pluralStringResource(Res.plurals.stats_play_time_minutes_spoken, minutes, minutes)
    val secondsSpoken = pluralStringResource(Res.plurals.stats_play_time_seconds_spoken, seconds, seconds)
    val spoken = when {
        hours > 0 && minutes > 0 -> spokenList(listOf(hoursSpoken, minutesSpoken))
        hours > 0 -> hoursSpoken
        minutes > 0 -> minutesSpoken
        else -> secondsSpoken
    }
    val description = stringResource(Res.string.stats_play_time, spoken)
    Text(
        text = stringResource(Res.string.stats_play_time, shown),
        modifier = modifier.fillMaxWidth().semantics { contentDescription = description },
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}
