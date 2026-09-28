package net.zodac.dicefive.ui.common

import kotlin.math.absoluteValue

/**
 * [epochMillis] as "Sep 28, 2026 14:05", in the device's own time zone: date *and* time, because
 * several achievements can land in the same burst at the end of a game, and a date alone wouldn't
 * tell two rows in that burst apart. The Leaderboard, Statistics and Achievements screens all show a
 * timestamp in this one format.
 *
 * Each platform formats it with its own date APIs (month names in the device's language), rather
 * than the shared code pulling in a date-time library for one pattern - kotlinx-datetime alone was
 * ~180 classes in the release APK.
 */
internal expect fun formatTimestamp(epochMillis: Long): String

/** Thousand separators, so "34,521 of 100,000" doesn't have to be counted digit by digit. */
internal fun Int.grouped(): String {
    val digits = toLong().absoluteValue.toString()
    val grouped = digits.reversed().chunked(3).joinToString(",").reversed()
    return if (this < 0) "-$grouped" else grouped
}
