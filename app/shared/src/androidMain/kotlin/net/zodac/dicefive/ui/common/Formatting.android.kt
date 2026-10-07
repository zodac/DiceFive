package net.zodac.dicefive.ui.common

import android.icu.text.ListFormatter
import android.icu.text.MessageFormat
import android.text.TextUtils
import android.view.View
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DecimalStyle
import java.text.NumberFormat
import java.util.Locale

// Kept until the pattern, the language or the device's locale changes.
private var timestampFormat: Pair<Triple<String, String, Locale>, DateTimeFormatter>? = null

internal actual fun formatTimestamp(epochMillis: Long, pattern: String, languageTag: String): String {
    val device = Locale.getDefault(Locale.Category.FORMAT)
    val key = Triple(pattern, languageTag, device)
    val format = timestampFormat?.takeIf { it.first == key }?.second ?: run {
        val strings = Locale.forLanguageTag(languageTag)
        // The device's own form of the strings' language where it has one (en-US's "Sep"), else the strings' ("Sept").
        val locale = if (device.language == strings.language) device else strings
        DateTimeFormatter.ofPattern(pattern, locale).withDecimalStyle(DecimalStyle.of(strings))
    }.also { timestampFormat = key to it }
    return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(format)
}

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

private var integerFormat: Pair<String, NumberFormat>? = null

internal actual fun formatInteger(number: Int, languageTag: String): String {
    val format = integerFormat?.takeIf { it.first == languageTag }?.second
        ?: NumberFormat.getIntegerInstance(Locale.forLanguageTag(languageTag)).apply { isGroupingUsed = false }.also { integerFormat = languageTag to it }
    return format.format(number)
}

internal actual fun isRightToLeft(languageTag: String): Boolean =
    TextUtils.getLayoutDirectionFromLocale(Locale.forLanguageTag(languageTag)) == View.LAYOUT_DIRECTION_RTL
