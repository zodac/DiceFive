package net.zodac.dicefive.ui.common

// Not done yet: Compose reads the current language from NSLocale.preferredLanguages, the device's own list, not the
// languages the app has. So on an iPhone in a language with no translation, the English text's plural forms would follow
// that language's rules. The layout direction and dates already follow the strings (StringsLanguage, formatTimestamp).
// To do with the iOS app (IOS_SUPPORT.md Phase 5), where it can be run - see .claude/I18N.md.
internal actual fun alignPlatformLanguage(languageTag: String) = Unit
