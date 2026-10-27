package net.zodac.dicefive.game.style

import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.resources.Res
import net.zodac.dicefive.resources.style_variant_colour
import org.jetbrains.compose.resources.StringResource

/**
 * One variant of a [StyleGroup] - a colour, or for a frame a design - by the [id] its pick is saved as (a stored id:
 * never renamed) and its [name].
 */
data class StyleVariant(
    val id: String,
    val name: StringResource,
    /**
     * A secret achievement that alone unlocks this variant, on top of its group's own lock: until it's earned (or every
     * style is owned) the variant isn't offered on the Styles screen at all, and a saved pick of it draws the default.
     */
    val secretAchievement: Achievement? = null,
) {
    fun isAvailable(access: StyleAccess): Boolean =
        secretAchievement == null || access.ownsAllStyles || access.achievements.isUnlocked(secretAchievement)
}

/**
 * One style - a shape or pattern - offered on the Styles screen as a single tile, in one or more [variants]. The first
 * variant is the one the tile shows until the player picks another. [unlock] is what it takes to use any of them.
 */
data class StyleGroup(
    val name: StringResource,
    val variants: List<StyleVariant>,
    val unlock: StyleUnlock = StyleUnlock.Free,
) {
    init {
        require(variants.isNotEmpty()) { "A style needs at least one variant" }
    }

    fun variantOf(id: String): StyleVariant? = variants.firstOrNull { it.id == id }

    /** The [variants] to offer: all but any secret one not yet earned. */
    fun availableVariants(access: StyleAccess): List<StyleVariant> = variants.filter { it.isAvailable(access) }
}

/**
 * Every [StyleGroup] in one category of table art, in the order they're offered on the Styles screen, and what unlocks
 * each: which picks a player may use, with no art in it (that's `ui.style.StyleCatalog`'s, joined by id). Every variant
 * is a separately saved pick, by its own id. [noun] is what one of the category is called in a sentence - "the 'Irish'
 * dice style" - and [variantNoun] what one of a group's variants is: a colour, except where they're designs.
 */
open class StyleRegistry(
    val noun: StringResource,
    val groups: List<StyleGroup>,
    val variantNoun: StringResource = Res.string.style_variant_colour,
) {
    /** Every variant's id, in order. */
    val ids: List<String> = groups.flatMap { group -> group.variants.map { it.id } }

    /** The first variant of the first group: what a pick nothing recognises, or one still locked, draws. */
    val defaultId: String = ids.first()

    init {
        require(ids.toSet().size == ids.size) { "Style ids must be unique within a category" }
    }

    /** [id], or [defaultId] for an id nothing recognises. */
    fun idOrDefault(id: String): String = if (id in ids) id else defaultId

    /** The group [id] belongs to - or the default's, for an id nothing recognises. */
    fun groupOf(id: String): StyleGroup = idOrDefault(id).let { known -> groups.first { it.variantOf(known) != null } }

    /** Whether the style [id] belongs to, and [id] itself if it's a secret variant, are unlocked. */
    fun isUnlocked(id: String, access: StyleAccess): Boolean {
        val group = groupOf(id)
        return group.unlock.isMet(access) && group.variantOf(idOrDefault(id))?.isAvailable(access) != false
    }

    /** What the saved pick [id] draws: itself, unless it's still locked (or unknown), when the [defaultId]. The pick itself is left alone. */
    fun unlockedId(id: String, access: StyleAccess): String = if (isUnlocked(id, access)) idOrDefault(id) else defaultId

    /**
     * How many of the variants the Styles screen offers in this category are unlocked, out of how many it offers: every
     * variant of every group it shows, less a secret one ([StyleVariant.secretAchievement]) not yet earned, which isn't
     * counted until it is.
     */
    fun unlockCount(access: StyleAccess): UnlockCount {
        val shown = groups.filter { it.unlock.isMet(access) || !it.unlock.hiddenWhileLocked }
        val (unlocked, locked) = shown.partition { it.unlock.isMet(access) }
        val unlockedVariants = unlocked.sumOf { it.availableVariants(access).size }
        return UnlockCount(unlocked = unlockedVariants, total = unlockedVariants + locked.sumOf { it.availableVariants(access).size })
    }
}

/** [unlocked] of [total] styles, counting each variant of a style as one. */
data class UnlockCount(val unlocked: Int, val total: Int) {
    /** Whether every one counted is unlocked. */
    val complete: Boolean get() = unlocked == total

    operator fun plus(other: UnlockCount): UnlockCount = UnlockCount(unlocked + other.unlocked, total + other.total)
}
