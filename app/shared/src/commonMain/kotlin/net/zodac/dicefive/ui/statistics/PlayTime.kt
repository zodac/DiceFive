package net.zodac.dicefive.ui.statistics

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import net.zodac.dicefive.resources.Res
import net.zodac.dicefive.resources.stats_play_time
import net.zodac.dicefive.resources.stats_play_time_days_hours_minutes
import net.zodac.dicefive.resources.stats_play_time_days_spoken
import net.zodac.dicefive.resources.stats_play_time_hours_minutes
import net.zodac.dicefive.resources.stats_play_time_hours_spoken
import net.zodac.dicefive.resources.stats_play_time_minutes
import net.zodac.dicefive.resources.stats_play_time_minutes_spoken
import net.zodac.dicefive.resources.stats_play_time_seconds
import net.zodac.dicefive.resources.stats_play_time_seconds_spoken
import net.zodac.dicefive.ui.common.SoraFontFamily
import net.zodac.dicefive.ui.common.HoldPageCover
import net.zodac.dicefive.ui.common.delayWhileResumed
import net.zodac.dicefive.ui.common.grouped
import net.zodac.dicefive.ui.common.pluralStringResource
import net.zodac.dicefive.ui.common.spokenList
import net.zodac.dicefive.ui.common.stringResource

private const val SECONDS_PER_MINUTE = 60
private const val SECONDS_PER_HOUR = 3_600
private const val SECONDS_PER_DAY = 86_400

/** How long until [seconds] of play time would show differently: seconds count up for the first minute, then minutes do. */
internal fun untilPlayTimeChanges(seconds: Int): Duration =
    (if (seconds < SECONDS_PER_MINUTE) 1 else SECONDS_PER_MINUTE - seconds % SECONDS_PER_MINUTE).seconds

/** Where [shown], a time like "1,234d 4h 12m", has its numbers: each whole, with its grouping, in any language's digits. */
internal fun playTimeNumbers(shown: String): List<IntRange> = Regex("\\p{Nd}+(?:[.,\u066C]\\p{Nd}+)*").findAll(shown).map { it.range }.toList()

/**
 * [PlayTimeLine] for the total [read] gives, read again when the line would next change, and only while resumed. Its state
 * is read here, so an update recomposes this line and not the page around it. Never going back: a save moves seconds from
 * what's counted to what's stored. The page cover stays up until the first read.
 */
@Composable
internal fun LivePlayTimeLine(read: suspend () -> Int, modifier: Modifier = Modifier) {
    var seconds by remember { mutableStateOf<Int?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(read, lifecycle) {
        while (true) {
            val shown = maxOf(seconds ?: 0, read())
            seconds = shown
            lifecycle.delayWhileResumed(untilPlayTimeChanges(shown).inWholeMilliseconds)
        }
    }
    seconds?.let { PlayTimeLine(it, modifier) } ?: HoldPageCover()
}

/**
 * The time played on every device, whatever the Statistics page: seconds for the first minute, then minutes, then hours
 * and minutes, then from 24 hours days, hours, and minutes, however many days, with the numbers gold in the brand font. Spoken in words, without a part that's zero, and only
 * when it's reached: it isn't a live region, so the count isn't read out as it goes.
 */
@Composable
internal fun PlayTimeLine(seconds: Int, modifier: Modifier = Modifier) {
    val days = seconds / SECONDS_PER_DAY
    val hours = seconds % SECONDS_PER_DAY / SECONDS_PER_HOUR
    val minutes = seconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
    val shown = when {
        days > 0 -> stringResource(Res.string.stats_play_time_days_hours_minutes, days.grouped(), hours, minutes)
        hours > 0 -> stringResource(Res.string.stats_play_time_hours_minutes, hours, minutes)
        minutes > 0 -> stringResource(Res.string.stats_play_time_minutes, minutes)
        else -> stringResource(Res.string.stats_play_time_seconds, seconds)
    }
    val daysSpoken = pluralStringResource(Res.plurals.stats_play_time_days_spoken, days, days)
    val hoursSpoken = pluralStringResource(Res.plurals.stats_play_time_hours_spoken, hours, hours)
    val minutesSpoken = pluralStringResource(Res.plurals.stats_play_time_minutes_spoken, minutes, minutes)
    val secondsSpoken = pluralStringResource(Res.plurals.stats_play_time_seconds_spoken, seconds, seconds)
    val spoken = when {
        days == 0 && hours == 0 && minutes == 0 -> secondsSpoken
        else -> spokenList(
            listOfNotNull(daysSpoken.takeIf { days > 0 }, hoursSpoken.takeIf { hours > 0 }, minutesSpoken.takeIf { minutes > 0 }),
        )
    }
    val description = stringResource(Res.string.stats_play_time, spoken)
    val line = stringResource(Res.string.stats_play_time, shown)
    val numberStyle = SpanStyle(color = MaterialTheme.colorScheme.primary, fontFamily = SoraFontFamily, fontWeight = FontWeight.Bold)
    Text(
        text = buildAnnotatedString {
            append(line)
            val offset = line.lastIndexOf(shown)
            for (number in playTimeNumbers(shown)) addStyle(numberStyle, offset + number.first, offset + number.last + 1)
        },
        modifier = modifier.fillMaxWidth().semantics { contentDescription = description },
        // Without the style's added letter spacing, which spreads the digits apart at this size.
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}
