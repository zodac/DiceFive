package net.zodac.dicefive.ui.common

import androidx.compose.runtime.Composable
import kotlin.math.absoluteValue
import net.zodac.dicefive.resources.Res
import net.zodac.dicefive.resources.common_locale
import org.jetbrains.compose.resources.stringResource

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

/**
 * [number] as an ordinal in the strings' own language - "1st", "2nd", "11th", "21st" in English. Each
 * platform's own number formatter does it, because the plural rules in Compose resources are cardinal
 * only ("1 roll", "2 rolls") and can't say "1st". The words around it ("tied 2nd place") are string
 * templates, not part of this.
 *
 * The language is `common_locale` from the string resources, not the device's: until the app is
 * translated into the device's language, its text falls back to English, and the ordinals have to
 * fall back with it. See .claude/I18N.md.
 */
@Composable
internal fun ordinal(number: Int): String = formatOrdinal(number, stringResource(Res.string.common_locale))

/** [number] as an ordinal in the language [languageTag] (BCP 47, "en-GB") names. Use [ordinal] in the UI. */
internal expect fun formatOrdinal(number: Int, languageTag: String): String

/** Thousand separators, so "34,521 of 100,000" doesn't have to be counted digit by digit. */
internal fun Int.grouped(): String {
    val digits = toLong().absoluteValue.toString()
    val grouped = digits.reversed().chunked(3).joinToString(",").reversed()
    return if (this < 0) "-$grouped" else grouped
}
