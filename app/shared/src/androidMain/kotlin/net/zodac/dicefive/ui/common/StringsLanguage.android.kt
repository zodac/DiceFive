package net.zodac.dicefive.ui.common

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale
import kotlinx.coroutines.runBlocking
import net.zodac.dicefive.resources.Res
import net.zodac.dicefive.resources.common_locale
import org.jetbrains.compose.resources.getString

// Compose's Locale.current, and so Compose resources' plural rules, read LocaleList.getDefault(). It's the process's
// default, which Android sets again from the configuration when the device's language changes (and the Activity is
// recreated, as it doesn't handle that change itself) - so this runs on every composition of StringsLanguage, as well
// as from stringsLanguageConfiguration when the Activity starts.
internal actual fun alignPlatformLanguage(languageTag: String) {
    val strings = Locale.forLanguageTag(languageTag)
    val device = LocaleList.getDefault()
    if (device.isEmpty || device[0].language == strings.language) return
    val others = (0 until device.size()).map { device[it] }.filter { it.language != strings.language }
    LocaleList.setDefault(LocaleList(strings, *others.toTypedArray()))
}

/**
 * The configuration an Activity should apply over its own (with `applyOverrideConfiguration`, from `attachBaseContext`) so
 * that what Android itself shows follows the strings' language too - or null when the device's first language already is
 * it. The app's own text comes from Compose resources, but some comes from Android's: Material's built-in labels and state
 * descriptions, and the system's copy-and-paste menu. Without this, a phone in a language the app has no translation for
 * would show those in its own language beside the English, and lay the window out in its own direction. With it, the app
 * is either wholly in a language it has, or wholly in English, left to right.
 *
 * Also aligns the process's default languages ([alignPlatformLanguage]), which Compose resources and the date and number
 * formatters read. The strings' language is found the way Compose resources finds it, by reading `common_locale`, so
 * the two can't disagree.
 */
// Lint warns that a language set at runtime may be missing from a Play bundle split by language. This one never is: it
// only applies when the device's language has no translation, so it is always the base language, unqualified values/.
@SuppressLint("AppBundleLocaleChanges")
fun stringsLanguageConfiguration(): Configuration? {
    val device = LocaleList.getDefault()
    val languageTag = runBlocking { getString(Res.string.common_locale) }
    alignPlatformLanguage(languageTag)
    if (!device.isEmpty && device[0].language == Locale.forLanguageTag(languageTag).language) return null
    return Configuration().apply { setLocales(LocaleList.getDefault()) }
}
