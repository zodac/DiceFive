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
    return PLACEHOLDER.replace(template) { match ->
        when (val arg = args.getOrNull(match.groupValues[1].toInt() - 1)) {
            is Int -> formatInteger(arg, languageTag)
            null -> match.value
            else -> arg.toString()
        }
    }
}
