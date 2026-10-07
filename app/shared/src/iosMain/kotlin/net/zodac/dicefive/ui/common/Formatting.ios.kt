package net.zodac.dicefive.ui.common

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSListFormatter
import platform.Foundation.NSLocale
import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterDecimalStyle
import platform.Foundation.NSNumberFormatterOrdinalStyle
import platform.Foundation.canonicalLocaleIdentifierFromString
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.localeWithLocaleIdentifier

// NSDateFormatter defaults to the device's locale and time zone, as java.time's does on Android.
private val TIMESTAMP_FORMATTER = NSDateFormatter().apply { dateFormat = "MMM dd, yyyy HH:mm" }

internal actual fun formatTimestamp(epochMillis: Long): String =
    TIMESTAMP_FORMATTER.stringFromDate(NSDate.dateWithTimeIntervalSince1970(epochMillis / 1000.0))

// "1st" in English, "1er" in French. Kept until the language changes.
private var ordinalFormatter: Pair<String, NSNumberFormatter>? = null

internal actual fun formatOrdinal(number: Int, languageTag: String): String {
    val formatter = ordinalFormatter?.takeIf { it.first == languageTag }?.second
        ?: NSNumberFormatter().apply {
            numberStyle = NSNumberFormatterOrdinalStyle
            locale = NSLocale.localeWithLocaleIdentifier(NSLocale.canonicalLocaleIdentifierFromString(languageTag))
        }.also { ordinalFormatter = languageTag to it }
    return formatter.stringFromNumber(NSNumber(int = number)) ?: number.toString()
}

// Kept until the language changes, like the ordinal formatter.
private var groupedFormatter: Pair<String, NSNumberFormatter>? = null

internal actual fun formatGrouped(number: Int, languageTag: String): String {
    val formatter = groupedFormatter?.takeIf { it.first == languageTag }?.second
        ?: NSNumberFormatter().apply {
            numberStyle = NSNumberFormatterDecimalStyle
            locale = NSLocale.localeWithLocaleIdentifier(NSLocale.canonicalLocaleIdentifierFromString(languageTag))
        }.also { groupedFormatter = languageTag to it }
    return formatter.stringFromNumber(NSNumber(int = number)) ?: number.toString()
}

internal actual fun formatList(items: List<String>, languageTag: String): String =
    NSListFormatter().apply {
        locale = NSLocale.localeWithLocaleIdentifier(NSLocale.canonicalLocaleIdentifierFromString(languageTag))
    }.stringFromItems(items) ?: items.joinToString()

private var integerFormatter: Pair<String, NSNumberFormatter>? = null

internal actual fun formatInteger(number: Int, languageTag: String): String {
    val formatter = integerFormatter?.takeIf { it.first == languageTag }?.second
        ?: NSNumberFormatter().apply {
            numberStyle = NSNumberFormatterDecimalStyle
            usesGroupingSeparator = false
            locale = NSLocale.localeWithLocaleIdentifier(NSLocale.canonicalLocaleIdentifierFromString(languageTag))
        }.also { integerFormatter = languageTag to it }
    return formatter.stringFromNumber(NSNumber(int = number)) ?: number.toString()
}
