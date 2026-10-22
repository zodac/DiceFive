package net.zodac.dicefive.ui.common

import java.util.Locale

// Compose's Locale.current, and so Compose resources' plural rules, read the JVM's default locale. Make its language the
// strings' when the two differ, keeping the region where the language already matches - as StringsLanguage.android.kt
// does with Android's LocaleList.
internal actual fun alignPlatformLanguage(languageTag: String) {
    val strings = Locale.forLanguageTag(languageTag)
    if (Locale.getDefault().language == strings.language) return
    Locale.setDefault(strings)
}
