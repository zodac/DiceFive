package net.zodac.dicefive.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import net.zodac.dicefive.resources.Res
import net.zodac.dicefive.resources.common_locale
import org.jetbrains.compose.resources.stringResource as resourcesStringResource

/**
 * Makes everything under it follow the language the strings resolved to (`common_locale`), not the device's - the
 * same rule the number, ordinal and list formatters already keep. Without it, a phone in a language the app doesn't
 * have shows English text in that language's layout and grammar:
 * - **Layout direction.** An Arabic, Persian or Hebrew phone mirrored the whole app and set its English paragraphs
 *   right to left ("1 of 11" drawn as "of 11 1"). The direction is now the strings' language's.
 * - **Plural forms.** Compose resources picks a plural's form by the platform's current language, so a Russian phone
 *   read the English "21 rolls" as "21 roll", and a Persian or French one (where 0 is "one") "0 roll". The platform's
 *   language is aligned with the strings' first ([alignPlatformLanguage]), so the form is picked by English's rules.
 *
 * Which strings resolve isn't affected: the strings' language is the one the device's language already resolved to.
 * [net.zodac.dicefive.ui.theme.DiceFiveTheme] applies this, so every screen, preview and test gets it. See .claude/I18N.md.
 */
@Composable
internal fun StringsLanguage(content: @Composable () -> Unit) {
    val languageTag = resourcesStringResource(Res.string.common_locale)
    // Before anything below reads a plural. Cheap when already aligned, and re-checked each time this recomposes, since
    // the platform resets its language when the device's changes.
    alignPlatformLanguage(languageTag)
    CompositionLocalProvider(
        LocalLayoutDirection provides if (isRightToLeft(languageTag)) LayoutDirection.Rtl else LayoutDirection.Ltr,
        content = content,
    )
}

/**
 * Puts the strings' language ([languageTag], BCP 47) first in the platform's current languages, if the device's first
 * language is a different one - which is what Compose resources reads to pick a plural's form (its own environment is
 * internal, so it can't be set for one composition). A device already in the strings' language (en-US for en-GB) is
 * left alone, keeping its regional formats.
 */
internal expect fun alignPlatformLanguage(languageTag: String)
