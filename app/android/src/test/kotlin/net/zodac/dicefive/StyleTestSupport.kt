package net.zodac.dicefive

import java.io.File
import net.zodac.dicefive.data.achievements.UnlockedStyle
import net.zodac.dicefive.ui.game.style.StyleCatalog
import net.zodac.dicefive.ui.game.style.StyleFamily
import net.zodac.dicefive.ui.game.style.TableArt
import org.jetbrains.compose.resources.StringResource

/**
 * The English text of [resource], as strings.xml has it. `Res` is internal to :app:shared, so a test here can't
 * resolve a resource itself; the file is read from disk, as ScoresScreenTest does for a mode's name.
 */
internal fun englishText(resource: StringResource): String {
    val xml = File("../shared/src/commonMain/composeResources/values/strings.xml").readText()
    return Regex("""<string name="${resource.key}">([^<]*)</string>""").find(xml)!!.groupValues[1]
}

/** The family of this catalogue called [name] in English. */
internal fun <T : TableArt> StyleCatalog<T>.familyNamed(name: String): StyleFamily<T> = families.first { englishText(it.name) == name }

/** A banner's entry for the style [name] of this catalogue. */
internal fun StyleCatalog<*>.unlockedStyle(name: String): UnlockedStyle = UnlockedStyle(familyNamed(name).name, noun)
