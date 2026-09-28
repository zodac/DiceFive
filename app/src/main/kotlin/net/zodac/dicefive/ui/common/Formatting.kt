package net.zodac.dicefive.ui.common

import kotlin.math.absoluteValue
import kotlin.time.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime

/**
 * "Sep 28, 2026 14:05": date *and* time, because several achievements can land in the same burst at
 * the end of a game, and a date alone wouldn't tell two rows in that burst apart. The Leaderboard,
 * Statistics and Achievements screens all show a timestamp in this one format.
 *
 * English month names whatever the device's language - the rest of the app's text is English too.
 */
private val TIMESTAMP_FORMAT = LocalDateTime.Format {
    monthName(MonthNames.ENGLISH_ABBREVIATED)
    char(' ')
    day()
    chars(", ")
    year()
    char(' ')
    hour()
    char(':')
    minute()
}

/** [epochMillis] in [TIMESTAMP_FORMAT], in the device's own time zone. */
internal fun formatTimestamp(epochMillis: Long): String =
    Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(TimeZone.currentSystemDefault()).format(TIMESTAMP_FORMAT)

/** Thousand separators, so "34,521 of 100,000" doesn't have to be counted digit by digit. */
internal fun Int.grouped(): String {
    val digits = toLong().absoluteValue.toString()
    val grouped = digits.reversed().chunked(3).joinToString(",").reversed()
    return if (this < 0) "-$grouped" else grouped
}
