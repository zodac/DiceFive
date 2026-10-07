package net.zodac.dicefive.ui.common

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSListFormatter
import platform.Foundation.NSLocale
import platform.Foundation.NSLocaleLanguageDirectionRightToLeft
import platform.Foundation.characterDirectionForLanguage
import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterDecimalStyle
import platform.Foundation.NSNumberFormatterOrdinalStyle
import platform.Foundation.canonicalLocaleIdentifierFromString
import platform.Foundation.currentLocale
import platform.Foundation.languageCode
import platform.Foundation.localeIdentifier
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.localeWithLocaleIdentifier

// Kept until the pattern, the language or the device's locale changes. NSDateFormatter uses the device's time zone.
private var timestampFormatter: Pair<Triple<String, String, String>, NSDateFormatter>? = null

internal actual fun formatTimestamp(epochMillis: Long, pattern: String, languageTag: String): String {
    val device = NSLocale.currentLocale
    val key = Triple(pattern, languageTag, device.localeIdentifier)
    val formatter = timestampFormatter?.takeIf { it.first == key }?.second ?: NSDateFormatter().apply {
        val strings = NSLocale.localeWithLocaleIdentifier(NSLocale.canonicalLocaleIdentifierFromString(languageTag))
        // The device's own form of the strings' language where it has one (en-US's "Sep"), else the strings' ("Sept").
        locale = if (device.languageCode == strings.languageCode) device else strings
        dateFormat = pattern
    }.also { timestampFormatter = key to it }
    return formatter.stringFromDate(NSDate.dateWithTimeIntervalSince1970(epochMillis / 1000.0))
}

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

// Kept until the language changes, like the ordinal formatter: every spoken list on a page asked for a new one.
private var listFormatter: Pair<String, NSListFormatter>? = null

internal actual fun formatList(items: List<String>, languageTag: String): String {
    val formatter = listFormatter?.takeIf { it.first == languageTag }?.second
        ?: NSListFormatter().apply {
            locale = NSLocale.localeWithLocaleIdentifier(NSLocale.canonicalLocaleIdentifierFromString(languageTag))
        }.also { listFormatter = languageTag to it }
    return formatter.stringFromItems(items) ?: items.joinToString()
}

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

// Kept until the language changes: every string with an argument asks (see fill), many times a page.
private var rightToLeft: Pair<String, Boolean>? = null

internal actual fun isRightToLeft(languageTag: String): Boolean =
    rightToLeft?.takeIf { it.first == languageTag }?.second
        ?: (NSLocale.characterDirectionForLanguage(languageTag.substringBefore('-')) == NSLocaleLanguageDirectionRightToLeft)
            .also { rightToLeft = languageTag to it }
