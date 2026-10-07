package net.zodac.dicefive.ui.common

import androidx.compose.runtime.Composable
import net.zodac.dicefive.resources.Res
import net.zodac.dicefive.resources.common_locale
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource as resourcesPluralStringResource
import org.jetbrains.compose.resources.stringResource as resourcesStringResource

/*
 * The UI's own stringResource and pluralStringResource: Compose resources' ones, except that a whole number given as an
 * argument is written in the numerals of the strings' language ("x٣" for Arabic-Indic digits) rather than always 0-9.
 * The string is fetched with its "%1$d" / "%2$s" placeholders still in it, and filled here, so the numerals are ours.
 * Every file under ui/ imports these instead of the library's, so no number reaches the screen unformatted.
 *
 * A text argument written the other way from the strings ("علي", a player's name typed in Arabic, in an English
 * sentence - or the game's "5x" in a right-to-left one) is set apart with Unicode's first-strong isolates, so the
 * numbers and punctuation beside it stay where the sentence put them instead of joining its run.
 */

/** [resourcesStringResource], with each [Int] in [formatArgs] in the strings' own numerals. */
@Composable
fun stringResource(resource: StringResource, vararg formatArgs: Any): String =
    if (formatArgs.isEmpty()) resourcesStringResource(resource) else fill(resourcesStringResource(resource), formatArgs)

/** [resourcesPluralStringResource], with each [Int] in [formatArgs] - but not [quantity], which picks the form - in the strings' own numerals. */
@Composable
fun pluralStringResource(resource: PluralStringResource, quantity: Int, vararg formatArgs: Any): String =
    if (formatArgs.isEmpty()) resourcesPluralStringResource(resource, quantity) else fill(resourcesPluralStringResource(resource, quantity), formatArgs)

private val PLACEHOLDER = Regex("""%(\d+)\$[sd]""")

/** [template] with each "%N$s" or "%N$d" replaced by the Nth of [args], whole numbers in the strings' numerals. */
@Composable
private fun fill(template: String, args: Array<out Any>): String {
    val languageTag = resourcesStringResource(Res.string.common_locale)
    val rightToLeft = isRightToLeft(languageTag)
    return PLACEHOLDER.replace(template) { match ->
        when (val arg = args.getOrNull(match.groupValues[1].toInt() - 1)) {
            is Int -> formatInteger(arg, languageTag)
            null -> match.value
            else -> arg.toString().isolatedIfOpposite(rightToLeft)
        }
    }
}

/**
 * [this] between FIRST STRONG ISOLATE and POP DIRECTIONAL ISOLATE when its direction - its first letter's - is not
 * the strings' ([rightToLeft]); as it is otherwise, so text in the strings' own direction is unchanged. The marks
 * are invisible, and screen readers skip them.
 */
internal fun String.isolatedIfOpposite(rightToLeft: Boolean): String {
    val first = firstOrNull { it.isLetter() } ?: return this
    return if (first.isInRightToLeftScript() != rightToLeft) "\u2068$this\u2069" else this
}

/** Hebrew, Arabic, Syriac, Thaana, N'Ko and the other right-to-left scripts of the Basic Multilingual Plane, with their presentation forms. */
private fun Char.isInRightToLeftScript(): Boolean = code in 0x0590..0x08FF || code in 0xFB1D..0xFDFF || code in 0xFE70..0xFEFF
