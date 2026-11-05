package net.zodac.dicefive.ui.common

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import kotlin.math.abs

/** The shortest and longest an arrow's scroll takes. */
private const val SMOOTH_SCROLL_MIN_MILLIS = 350
private const val SMOOTH_SCROLL_MAX_MILLIS = 800

/** The extra time an arrow's scroll takes for each viewport of list it crosses. */
private const val SMOOTH_SCROLL_MILLIS_PER_VIEWPORT = 90

/**
 * Where the item at [target] lies in the list, or null if that isn't known yet. A sticky header is laid out from the moment the
 * list reaches its category, but pinned at the top, so its own offset says it has already arrived while it's still far above.
 * So it's read from the item after it, which is drawn where it lies; with that item not in view but later ones, it's pinned. With
 * no later items in view, [target] has only just come in at the bottom, and is where it's drawn.
 */
internal fun offsetWhereItLies(visible: List<LazyListItemInfo>, target: Int, spacing: Int): Int? {
    val item = visible.firstOrNull { it.index == target } ?: return null
    val next = visible.firstOrNull { it.index == target + 1 }
    return when {
        next != null -> next.offset - spacing - item.size
        visible.any { it.index > target } -> null
        else -> item.offset
    }
}

/**
 * Scrolls to the item at [target] with one eased animation, slower the further it goes: [LazyListState.animateScrollToItem] is a
 * fixed spring that is over in a quarter of a second, which across several screens of rows reads as a snap. With [toEnd], [target]
 * is the list's last item and the scroll stops with it at the bottom, as far as the list goes, rather than heading on for the top.
 *
 * Items between here and [target] haven't been measured, and going up [target] is only seen as it arrives. So each frame moves
 * its share of what's left, from where [target] lies once that's known ([offsetWhereItLies]) and until then from the sizes of the
 * items seen so far: the estimate's error shrinks with the distance, and a better one changes the pace rather than making a jump.
 * A last [LazyListState.scrollToItem] absorbs the little still wrong.
 */
internal suspend fun LazyListState.smoothScrollToItem(target: Int, toEnd: Boolean = false) {
    val visible = layoutInfo.visibleItemsInfo
    if (visible.isEmpty()) return scrollToItem(target)
    val viewport = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset).coerceAtLeast(1)
    val spacing = layoutInfo.mainAxisItemSpacing
    val seen = visible.associateTo(mutableMapOf()) { it.index to it.size }

    /** How far is left to scroll, from the [items] on screen. */
    fun distanceLeft(items: List<LazyListItemInfo>): Float {
        val toTop = offsetWhereItLies(items, target, spacing)?.toFloat() ?: estimatedOffset(items, target, seen, spacing)
        return if (toEnd) toTop + (seen[target]?.toFloat() ?: (seen.values.sum().toFloat() / seen.size)) - viewport else toTop
    }

    val duration = (SMOOTH_SCROLL_MIN_MILLIS + abs(distanceLeft(visible)) / viewport * SMOOTH_SCROLL_MILLIS_PER_VIEWPORT)
        .toInt().coerceIn(SMOOTH_SCROLL_MIN_MILLIS, SMOOTH_SCROLL_MAX_MILLIS)
    scroll {
        var previous = 0f
        animate(0f, 1f, animationSpec = tween(duration, easing = FastOutSlowInEasing)) { progress, _ ->
            val now = layoutInfo.visibleItemsInfo
            now.forEach { seen[it.index] = it.size }
            val left = distanceLeft(now)
            scrollBy(if (previous < 1f) left * (progress - previous) / (1f - previous) else left)
            previous = progress
        }
    }
    scrollToItem(target)
}

/** Where the item at [target] should lie, in the [visible] items' terms: from one drawn where it lies, at the average of the sizes [seen] so far. */
private fun estimatedOffset(visible: List<LazyListItemInfo>, target: Int, seen: Map<Int, Int>, spacing: Int): Float {
    val anchor = anchorItem(visible)
    return (target - anchor.index) * (seen.values.sum().toFloat() / seen.size + spacing) + anchor.offset
}
