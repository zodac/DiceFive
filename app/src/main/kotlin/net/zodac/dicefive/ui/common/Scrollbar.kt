package net.zodac.dicefive.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp

/** How much of the list's right edge the scrollbar track occupies. */
private val SCROLLBAR_WIDTH = 4.dp

/** The thumb never shrinks below this fraction of the track, however long the list gets. */
private const val MIN_THUMB_FRACTION = 0.08f

/** Everything [LazyListScrollbar] needs to remember between draws, for one [LazyListState]. */
private class ScrollbarMemory {
    /** -1 so the very first draw always looks like a "shape change" and seeds these fresh. */
    var totalItemsAtLastCheck = -1
    val knownItemSizes = mutableMapOf<Int, Int>()
    var frozenContentSize: Float? = null
}

/**
 * A minimal drawn scrollbar for a [androidx.compose.foundation.lazy.LazyColumn] - stock Compose
 * has no built-in one for Android. Shared by every page long enough to scroll (the Leaderboard,
 * Achievements and Statistics), so the affordance looks and behaves the same everywhere rather
 * than being reinvented per screen.
 *
 * Call from inside the `Box` that also holds the `LazyColumn`, with the same [listState], so the
 * bar overlays as a sibling pinned to the viewport edge instead of scrolling away with the list's
 * items - an extension on [BoxScope] rather than taking a `Modifier.align` from the caller, so it
 * can never be attached outside the real `Box` it needs (see .claude/UI.md's `Modifier.align`
 * gotcha).
 *
 * Shown/hidden via [LazyListState.canScrollForward]/`canScrollBackward`, not by comparing
 * `layoutInfo.visibleItemsInfo.size` against `totalItemsCount`: on a shortish list, Compose can
 * end up reporting every item as "visible" at once once scrolling has prefetched past the
 * viewport edge, which would make an item-count-based check hide the bar partway through a
 * scroll and never bring it back.
 *
 * The thumb's size and position are pixel-based (measured item sizes and scroll offset), not
 * item-count-based, for the same reason: counting items only changes the thumb once per whole
 * item scrolled past, which on a short list of tall cards (a handful of items, most of them
 * bigger than the viewport) makes the thumb balloon to nearly the full track and barely move -
 * chunky rather than tracking the finger.
 *
 * Sizes are remembered per item index rather than re-averaged from whatever's on screen each
 * frame, which matters on a mixed-height list like Achievements (section headers, group headers
 * and cards - some with a progress bar, some without, and every card's own height varies further
 * still with how many lines its description wraps to - all as differently-sized items in the same
 * `LazyColumn`). Re-averaging only the *currently visible* window made the estimate swing every
 * time the mix of item sizes on screen changed, which showed up as the thumb visibly resizing
 * while scrolling, not just moving.
 *
 * Because real item heights on a list like that vary continuously rather than settling into a
 * handful of fixed sizes, even the running average never quite stops moving - every newly-seen
 * card nudges it a little. An earlier version froze the *size* estimate once a sample of items had
 * been seen, but on a page where only a handful of items fit on screen at rest (Achievements:
 * a header and a couple of cards), that sample was only reached partway into the *first* scroll -
 * so all the convergence that was meant to be spread out happened at once, right as the user
 * started scrolling, and read as a single jarring resize rather than the gradual settling it was
 * before.
 *
 * So the *size* estimate is instead frozen from the very first layout, before any scrolling has
 * happened at all: whatever's on screen at rest is what the thumb's size is based on, for the rest
 * of the session. That trades a bit of accuracy in the thumb's proportions (the first screenful
 * might not be perfectly representative of the whole list) for there being nothing left to
 * converge once the user actually starts scrolling. The thumb's *position* is unaffected - it's
 * still computed from the exact remembered size of every item actually scrolled past, live, for
 * the whole list.
 *
 * All of that memory resets whenever [totalItemsCount][androidx.compose.foundation.lazy.LazyListLayoutInfo.totalItemsCount]
 * changes, not just when [listState] does - the same `LazyListState` instance persists across
 * Achievements' "Hide unlocked" toggle and across the Leaderboard's page navigation, but the item
 * at a given index is a different one afterwards, so a size recorded under the old shape would be
 * silently wrong (or, on Achievements, just never re-examined, since the frozen size estimate
 * doesn't know anything changed). That check runs as plain imperative code inside the `Canvas`
 * draw phase, not as a `remember` key at the composable level - `LazyListScrollbar`'s own
 * parameters (`listState`, `modifier`) never change across a toggle, only what it reads from
 * `listState` internally does, which left a `remember(listState, totalItems)` key exposed to
 * however reliably Compose happens to recompose a stable-parameter composable. The draw phase read
 * every item's live pixel `offset` already has to run on every frame regardless of that, for the
 * *position* math below - piggybacking the shape check on the same guaranteed-fresh path removes
 * the question entirely rather than trying to win it.
 *
 * The estimate is still just that - an estimate, extrapolated from a small early sample on a list
 * whose real item heights vary continuously - so it can end up a little larger than the list's true
 * content size. Left alone, that shows up as the thumb never quite reaching the bottom of the
 * track (or the top) even once the list itself has: the calculated `scrollFraction` tops out below
 * 1, permanently. `canScrollForward`/`canScrollBackward` are exact, not estimated - whenever
 * Compose says there's genuinely nothing more to scroll in a direction, that overrides the pixel
 * math and snaps the thumb the rest of the way to that edge. The estimate still governs everything
 * in between; only the two true endpoints are pinned.
 */
@Composable
fun BoxScope.LazyListScrollbar(listState: LazyListState, modifier: Modifier = Modifier) {
    val showScrollbar by remember { derivedStateOf { listState.canScrollForward || listState.canScrollBackward } }
    if (!showScrollbar) return

    val thumbColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val memory = remember(listState) { ScrollbarMemory() }

    Canvas(
        modifier = modifier
            .align(Alignment.CenterEnd)
            .fillMaxHeight()
            .width(SCROLLBAR_WIDTH),
    ) {
        drawRoundRect(color = trackColor, cornerRadius = CornerRadius(size.width / 2))

        val layoutInfo = listState.layoutInfo
        val totalItems = layoutInfo.totalItemsCount
        val visibleItems = layoutInfo.visibleItemsInfo
        if (totalItems == 0 || visibleItems.isEmpty()) return@Canvas

        if (memory.totalItemsAtLastCheck != totalItems) {
            memory.totalItemsAtLastCheck = totalItems
            memory.knownItemSizes.clear()
            memory.frozenContentSize = null
        }

        for (item in visibleItems) memory.knownItemSizes[item.index] = item.size

        val knownTotalSize = memory.knownItemSizes.values.sum()
        // Unseen items (below the fold, never yet scrolled to) are estimated at the average of
        // what's actually been measured so far.
        val averageItemSize = knownTotalSize.toFloat() / memory.knownItemSizes.size
        val unseenItems = totalItems - memory.knownItemSizes.size
        val liveContentSize = knownTotalSize + averageItemSize * unseenItems
        val viewportSize = layoutInfo.viewportSize.height.toFloat()

        val estimatedContentSize = memory.frozenContentSize ?: liveContentSize.also { memory.frozenContentSize = it }

        val thumbFraction = (viewportSize / estimatedContentSize).coerceIn(MIN_THUMB_FRACTION, 1f)
        val thumbHeight = size.height * thumbFraction

        val firstVisible = visibleItems.first()
        // How far the list has scrolled, in pixels: every earlier item's known (or, if not yet
        // seen, estimated) size, plus however far into the first still-visible one - `offset` is
        // negative once its top has scrolled above the viewport.
        var scrolledPastSize = 0f
        for (index in 0 until firstVisible.index) {
            scrolledPastSize += memory.knownItemSizes[index]?.toFloat() ?: averageItemSize
        }
        scrolledPastSize -= firstVisible.offset

        val scrollableSize = (estimatedContentSize - viewportSize).coerceAtLeast(1f)
        // The pixel math is an estimate and can overshoot the list's true size, which would
        // otherwise leave this short of 1 (or above 0) even once the list has genuinely hit an
        // end - canScrollForward/Backward are exact, so they override the estimate right at the
        // two edges rather than leaving the thumb visibly short of the track's edge.
        val scrollFraction = when {
            !listState.canScrollForward -> 1f
            !listState.canScrollBackward -> 0f
            else -> (scrolledPastSize / scrollableSize).coerceIn(0f, 1f)
        }
        val thumbOffsetY = (size.height - thumbHeight) * scrollFraction

        drawRoundRect(
            color = thumbColor,
            topLeft = Offset(0f, thumbOffsetY),
            size = Size(size.width, thumbHeight),
            cornerRadius = CornerRadius(size.width / 2),
        )
    }
}
