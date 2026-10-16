package net.zodac.dicefive.platform

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import net.zodac.dicefive.resources.Res
import net.zodac.dicefive.resources.licences_count_font
import net.zodac.dicefive.resources.licences_count_image
import net.zodac.dicefive.resources.licences_count_library
import net.zodac.dicefive.resources.licences_count_sound
import org.jetbrains.compose.resources.PluralStringResource

/**
 * What a licensed item is, so a licence's heading can say "Used by 4 sounds" rather than calling a
 * recording a library. Everything the plugin discovers from Gradle is a [LIBRARY]; a hand-written
 * app/licensing/libraries/ entry for an asset says which kind it is in its `tag` field. [count] is the
 * kind's name with a number in front, in the singular or plural the number needs.
 */
enum class ComponentKind(val tag: String?, val count: PluralStringResource) {
    LIBRARY(tag = null, count = Res.plurals.licences_count_library),
    FONT(tag = "font", count = Res.plurals.licences_count_font),
    SOUND(tag = "sound", count = Res.plurals.licences_count_sound),
    IMAGE(tag = "image", count = Res.plurals.licences_count_image),
    ;

    companion object {
        fun fromTag(tag: String?): ComponentKind = entries.firstOrNull { it.tag == tag } ?: LIBRARY
    }
}

/** One licensed item as the dialog shows it. [copyright] is only set where the licence requires the
 * notice (or a credit) itself to be reproduced - see app/licensing/README.md. */
data class LicensedComponent(
    val name: String,
    val version: String?,
    val kind: ComponentKind,
    val website: String?,
    val copyright: String?,
)

/** One licence, its full text shown once, and every item the app ships under it. */
data class LicenseGroup(val name: String, val text: String, val components: List<LicensedComponent>) {
    /**
     * How many items of each kind this licence covers, most common first - "Used by 84 libraries", "Used by 1
     * library and 1 font", "Used by 2 libraries, 1 font and 1 sound" once [LicenceLabels] has put it into words.
     */
    val kindCounts: List<Pair<ComponentKind, Int>>
        get() = components.groupingBy { it.kind }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<ComponentKind, Int>> { it.value }.thenBy { it.key.ordinal })
            .map { it.key to it.value }
}

/** A dependency's Apache-2.0 NOTICE file, which must be passed on alongside the licence itself. */
data class ThirdPartyNotice(val library: String, val text: String)

data class LicenseReport(val groups: List<LicenseGroup>, val notices: List<ThirdPartyNotice>)

/**
 * How far the licence document is scrolled, in pixels, for the dialog's scrollbar: the
 * document keeps [position] and [maxPosition] up to date (both snapshot state, so the bar follows
 * them), and sets [scrollBy] to scroll itself by a pixel delta, for a drag on the bar.
 */
class LicenceScroll {
    var position by mutableIntStateOf(0)
    var maxPosition by mutableIntStateOf(0)
    var scrollBy: (Float) -> Unit = {}
}

/**
 * Drops whatever text is selected in one Licences dialog when a tap lands anywhere in it - the way
 * selection behaves in a browser. Each platform's licence document supplies its own (it's the one
 * that knows what "selected" means for its text view); the dialog provides it via
 * [LocalSelectionClearer] and wires the taps with `clearSelectionsOnTap`.
 */
interface SelectionClearer {

    fun clearAll()

    /** Whether the tap in progress was used up dismissing a selection (and resets it) - so that
     * same tap doesn't also follow a link. */
    fun consumeDismissedTap(): Boolean
}

val LocalSelectionClearer = staticCompositionLocalOf<SelectionClearer?> { null }

/** A [SelectionClearer] with nothing to clear - each [ComposeLicenceDocument] section keeps its own selection. */
internal object NoSelectionClearer : SelectionClearer {
    override fun clearAll() = Unit

    override fun consumeDismissedTap(): Boolean = false
}
