package net.zodac.dicefive.ui.common

import com.ibm.icu.text.ListFormatter
import com.ibm.icu.text.MessageFormat
import com.ibm.icu.text.NumberFormat
import com.ibm.icu.util.ULocale
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DecimalStyle
import java.util.Locale

// ICU4J, the library Android's android.icu repackages, so the JVM formats as Formatting.android.kt does.

// Kept until the pattern, the language or the device's locale changes.
private val timestampFormat = LastKeyCache<Triple<String, String, Locale>, DateTimeFormatter>()

internal actual fun formatTimestamp(epochMillis: Long, pattern: String, languageTag: String): String {
    val device = Locale.getDefault(Locale.Category.FORMAT)
    val key = Triple(pattern, languageTag, device)
    val format = timestampFormat.get(key) {
        val strings = Locale.forLanguageTag(languageTag)
        // The device's own form of the strings' language where it has one (en-US's "Sep"), else the strings' ("Sept").
        val locale = if (device.language == strings.language) device else strings
        DateTimeFormatter.ofPattern(pattern, locale).withDecimalStyle(DecimalStyle.of(strings))
    }
    return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(format)
}

// ICU's "ordinal" argument type: "1st", "2nd", "11th" in English. Building one parses ICU's rules, so it's kept until
// the language changes.
private val ordinalFormat = LastKeyCache<String, MessageFormat>()

internal actual fun formatOrdinal(number: Int, languageTag: String): String {
    val format = ordinalFormat.get(languageTag) { MessageFormat("{0,ordinal}", ULocale.forLanguageTag(it)) }
    return format.format(arrayOf(number))
}

// Kept until the language changes, like the ordinal format: building one loads the locale's data.
private val groupedFormat = LastKeyCache<String, NumberFormat>()

internal actual fun formatGrouped(number: Int, languageTag: String): String {
    val format = groupedFormat.get(languageTag) { NumberFormat.getIntegerInstance(ULocale.forLanguageTag(it)) }
    return format.format(number.toLong())
}

// Kept until the language changes, like the ordinal format.
private val listFormat = LastKeyCache<String, ListFormatter>()

internal actual fun formatList(items: List<String>, languageTag: String): String {
    val format = listFormat.get(languageTag) { ListFormatter.getInstance(ULocale.forLanguageTag(it)) }
    return format.format(items)
}

private val integerFormat = LastKeyCache<String, NumberFormat>()

internal actual fun formatInteger(number: Int, languageTag: String): String {
    val format = integerFormat.get(languageTag) {
        NumberFormat.getIntegerInstance(ULocale.forLanguageTag(it)).apply { isGroupingUsed = false }
    }
    return format.format(number.toLong())
}

// Kept until the language changes: every string with an argument asks (see fill), many times a page.
private val rightToLeft = LastKeyCache<String, Boolean>()

internal actual fun isRightToLeft(languageTag: String): Boolean =
    rightToLeft.get(languageTag) { ULocale.forLanguageTag(it).isRightToLeft }
