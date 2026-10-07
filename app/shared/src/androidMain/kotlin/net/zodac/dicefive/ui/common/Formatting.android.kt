package net.zodac.dicefive.ui.common

import android.icu.text.ListFormatter
import android.icu.text.MessageFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.text.NumberFormat
import java.util.Locale

private val TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm")

internal actual fun formatTimestamp(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(TIMESTAMP_FORMATTER)

// ICU's "ordinal" argument type: "1st", "2nd", "11th" in English, "1er" in French. (Its own ordinal
// formatter, RuleBasedNumberFormat, isn't in the public SDK.) Building one parses ICU's rules, so it's
// kept until the language changes.
private var ordinalFormat: Pair<String, MessageFormat>? = null

internal actual fun formatOrdinal(number: Int, languageTag: String): String {
    val format = ordinalFormat?.takeIf { it.first == languageTag }?.second
        ?: MessageFormat("{0,ordinal}", Locale.forLanguageTag(languageTag)).also { ordinalFormat = languageTag to it }
    return format.format(arrayOf(number))
}

// Kept until the language changes, like the ordinal format: building one loads the locale's data.
private var groupedFormat: Pair<String, NumberFormat>? = null

internal actual fun formatGrouped(number: Int, languageTag: String): String {
    val format = groupedFormat?.takeIf { it.first == languageTag }?.second
        ?: NumberFormat.getIntegerInstance(Locale.forLanguageTag(languageTag)).also { groupedFormat = languageTag to it }
    return format.format(number)
}

internal actual fun formatList(items: List<String>, languageTag: String): String =
    ListFormatter.getInstance(Locale.forLanguageTag(languageTag)).format(items)
