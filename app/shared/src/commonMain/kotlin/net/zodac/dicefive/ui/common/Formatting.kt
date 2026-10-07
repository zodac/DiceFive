package net.zodac.dicefive.ui.common

import androidx.compose.runtime.Composable
import net.zodac.dicefive.resources.Res
import net.zodac.dicefive.resources.common_clauses_join
import net.zodac.dicefive.resources.common_locale
import net.zodac.dicefive.resources.common_sentences_join
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

/**
 * [this] with its thousands grouped in the strings' own language - "34,521" in English - so "34,521 of
 * 100,000" doesn't have to be counted digit by digit. Like [ordinal], it follows `common_locale`, not
 * the device's language.
 */
@Composable
internal fun Int.grouped(): String = formatGrouped(this, stringResource(Res.string.common_locale))

/** [number] with its thousands grouped the way the language [languageTag] (BCP 47) writes them. Use [grouped] in the UI. */
internal expect fun formatGrouped(number: Int, languageTag: String): String

/**
 * [this] in the strings' own numerals, ungrouped - "3" in English, "٣" where the language writes Arabic-Indic digits - for a
 * number that is drawn or said on its own ("x3", a score). Numbers inside a string resource are done for you by this
 * package's [stringResource] and [pluralStringResource]; use this for the ones that aren't, and say which numerals a
 * language uses by its `common_locale` tag ("ar-u-nu-arab"). See .claude/I18N.md.
 */
@Composable
internal fun Int.localised(): String = formatInteger(this, stringResource(Res.string.common_locale))

/** [number] with no grouping, in the numerals the language [languageTag] (BCP 47, may carry a "-u-nu-" extension) writes. Use [localised] in the UI. */
internal expect fun formatInteger(number: Int, languageTag: String): String

/**
 * [items] as a spoken list in the strings' own language - "6, 6 and 6" in English. The platform's list
 * formatter does the joining, because the last separator and its spacing differ by language.
 */
@Composable
internal fun spokenList(items: List<String>): String = formatList(items, stringResource(Res.string.common_locale))

/** [items] joined as a list in the language [languageTag] (BCP 47) names. Use [spokenList] in the UI. */
internal expect fun formatList(items: List<String>, languageTag: String): String

/**
 * [parts] joined into one run of clauses - "Scored 12, would score 15, 1 more open" in English - for a
 * sentence whose pieces come and go. Each piece is its own translatable string; the template that
 * joins two of them (`common_clauses_join`) is one more, so a language can change the separator.
 */
@Composable
internal fun joinClauses(parts: List<String>): String = joinWith(parts, stringResource(Res.string.common_clauses_join))

/** [parts] joined into running text, one sentence after another ("`common_sentences_join`"). */
@Composable
internal fun joinSentences(parts: List<String>): String = joinWith(parts, stringResource(Res.string.common_sentences_join))

/** Folds [parts] together with a two-argument [template] ("%1$s, %2$s"). */
private fun joinWith(parts: List<String>, template: String): String =
    parts.reduceOrNull { first, second -> template.replace("%1\$s", first).replace("%2\$s", second) }.orEmpty()
