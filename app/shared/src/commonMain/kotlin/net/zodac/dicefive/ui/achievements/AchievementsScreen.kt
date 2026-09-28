package net.zodac.dicefive.ui.achievements

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.zodac.dicefive.data.achievements.AchievementScrollRequests
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.model.AchievementVisibility
import net.zodac.dicefive.platform.LocalPlatformServices
import net.zodac.dicefive.ui.common.ScreenScaffold
import net.zodac.dicefive.ui.common.formatTimestamp
import net.zodac.dicefive.ui.common.grouped
import net.zodac.dicefive.ui.theme.GoldAccent

// contentType tags, so hiddenUnderPinnedHeader can tell a category header from a row by the list's
// own layout info rather than by parsing keys.
private const val HEADER_CONTENT_TYPE = "header"
private const val ROW_CONTENT_TYPE = "achievement"

/** How long a row scrolled to from [AchievementScrollRequests] stays flashed gold before fading
 * back to its normal colour - long enough to register as "here it is", short enough to still read
 * as a flash rather than a new steady state. */
private const val ROW_FLASH_HOLD_MILLIS = 900L
private const val ROW_FLASH_TRANSITION_MILLIS = 400

/** The flat `LazyColumn` index of the achievement with [achievementId], mirroring exactly how the
 * list itself is built below - one header item per group, then its rows - or null if it isn't in
 * [groups] at all (a still-locked secret achievement, say - see the class doc above). */
private fun flatIndexOf(groups: List<AchievementGroup>, achievementId: String): Int? {
    var index = 0
    for (group in groups) {
        index++ // the group's own sticky header
        val itemIndex = group.items.indexOfFirst { it.achievement.id == achievementId }
        if (itemIndex != -1) return index + itemIndex
        index += group.items.size
    }
    return null
}

/** A rough stand-in for an achievement row's real height, close enough to centre a long press's
 * target without ever needing to actually measure it first. An earlier version scrolled the item
 * to the top of the viewport, measured its real height once that landed, then scrolled again to
 * correct - which reads as two separate movements, sometimes in opposite directions (overshooting
 * past centre on the way to the top, then correcting back). A single scroll straight to an
 * estimated position is one continuous motion instead; being off by a few dp against the row's
 * actual height doesn't undermine "roughly centred" the way a visible direction reversal
 * undermines "smooth". Sized for a typical unlocked row (icon, title, description, "Unlocked at");
 * a locked row with a progress bar instead is a little taller, but not by enough to matter here. */
private val ESTIMATED_ROW_HEIGHT_DP = 88.dp

/** Where [index] should end up, in px from the top of the viewport, so it lands roughly centred
 * rather than pinned to the very top - clamped to non-negative, since a viewport shorter than
 * [ESTIMATED_ROW_HEIGHT_DP] (shouldn't happen on any real device) would otherwise ask for a
 * negative scroll offset. */
private fun LazyListState.centeredScrollOffset(estimatedItemHeightPx: Int): Int {
    val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
    return ((viewportHeight - estimatedItemHeightPx) / 2).coerceAtLeast(0)
}

/**
 * Where the list was last scrolled to. Leaving this screen pops it off the back stack, which
 * discards both its ViewModel and anything `rememberSaveable`d, so the position has to live
 * somewhere longer-lived to still be there on the way back in. In memory only: it survives
 * navigating away and back for as long as the app process does, not an app restart.
 */
private object AchievementsScrollMemory {
    var firstVisibleItemIndex = 0
    var firstVisibleItemScrollOffset = 0
}

/**
 * Every achievement the app tracks, in one list - grouped by theme and, within a theme,
 * easiest-first, exactly as the catalogue declares them. An unlocked achievement stays in its
 * ladder rather than jumping to a separate section; it's just highlighted (its own icon in place
 * of the generic question mark every locked row shows, plus a raised card) so what's already been
 * earned is still obvious at a glance. See [icon] (`AchievementIcons.kt`) for the per-achievement
 * mapping.
 *
 * `Achievement.visibility` gates how much of a locked row is shown: a secret achievement is
 * filtered out of [AchievementsViewModel]'s state entirely while still locked, so it never
 * appears here (or anywhere else) until it's already been earned - and it never counts towards
 * the unlocked/total tally, earned or not; a hidden one still appears, title and all, but its
 * description reads "???" until then.
 *
 * Achievements are per device - there is no per-player breakdown here because there is no
 * per-player record. Resetting them lives in Settings, with the other destructive controls.
 *
 * Debug builds only: tapping the unlocked-count banner [AchievementsViewModel.SUPERUSER_TAP_TARGET]
 * times enters superuser mode, letting a long press on any row force it locked/unlocked
 * ([AchievementsViewModel.onSuperuserLongPressTick]) and a long press on the banner itself unlock
 * everything still locked, or - once nothing is - relock everything in one go
 * ([AchievementsViewModel.onBannerLongPress]). The banner shows no ripple or other hint that it
 * reacts to a tap or a long press at all - this is meant to stay a hidden tester's shortcut, not
 * something a player stumbles onto by noticing the row reacts to touch.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AchievementsScreen(
    viewModel: AchievementsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    val superuserModeActive by viewModel.superuserModeActive.collectAsState()

    val platform = LocalPlatformServices.current
    LaunchedEffect(viewModel) {
        viewModel.toastMessages.collect { message -> platform.showTransientMessage(message) }
    }

    // Restored only once the real list has loaded: uiState starts out empty, and a LazyColumn that
    // first lays out with nothing in it clamps any requested position straight back to the top.
    // Saved on the way out only once restored, so leaving before the load finishes doesn't wipe
    // the remembered position with that momentary top-of-list one. Sits out entirely if a scroll
    // request (see below) is already waiting - that's about to move the list somewhere specific,
    // and restoring the old position first would just be a flash of the wrong place before it did.
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var restoredScroll by remember { mutableStateOf(false) }
    val listLoaded = state.groups.isNotEmpty()
    LaunchedEffect(listLoaded) {
        if (listLoaded && !restoredScroll) {
            if (!AchievementScrollRequests.hasPending()) {
                listState.scrollToItem(
                    AchievementsScrollMemory.firstVisibleItemIndex,
                    AchievementsScrollMemory.firstVisibleItemScrollOffset,
                )
            }
            restoredScroll = true
        }
    }

    // A banner long-pressed elsewhere in the app (see AchievementBannerHost) asks to have its row
    // scrolled to and flashed here - covers both arriving fresh (this screen wasn't even open yet)
    // and a request landing while it already is, since this keeps collecting for as long as the
    // screen is composed either way. Only animates the scroll for the second case - see
    // AchievementScrollRequest.animate.
    var highlightedAchievementId by remember { mutableStateOf<String?>(null) }
    val estimatedRowHeightPx = with(LocalDensity.current) { ESTIMATED_ROW_HEIGHT_DP.roundToPx() }
    LaunchedEffect(Unit) {
        AchievementScrollRequests.requests.collect { request ->
            val groups = snapshotFlow { state.groups }.first { it.isNotEmpty() }
            val flatIndex = flatIndexOf(groups, request.achievementId)
            if (flatIndex != null) {
                // On a fresh navigation, the list hasn't necessarily had its first layout pass
                // yet by the time its data has loaded - centredScrollOffset needs a real
                // viewport size to work with, or it reads as 0 and the row lands pinned to the
                // top instead of centred.
                snapshotFlow { listState.layoutInfo.viewportSize.height }.first { it > 0 }
                val centeredOffset = listState.centeredScrollOffset(estimatedRowHeightPx)
                if (request.animate) {
                    listState.animateScrollToItem(flatIndex, centeredOffset)
                } else {
                    listState.scrollToItem(flatIndex, centeredOffset)
                }
                highlightedAchievementId = request.achievementId
                delay(ROW_FLASH_HOLD_MILLIS)
                if (highlightedAchievementId == request.achievementId) highlightedAchievementId = null
            }
            AchievementScrollRequests.consumePending()
        }
    }

    DisposableEffect(listState) {
        onDispose {
            if (restoredScroll) {
                AchievementsScrollMemory.firstVisibleItemIndex = listState.firstVisibleItemIndex
                AchievementsScrollMemory.firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset
            }
        }
    }

    ScreenScaffold(title = "Achievements", onBack = onBack, modifier = modifier) {
        // No ripple, and no other visual change on tap or long press: this is the hidden entry
        // point into superuser mode (see the class doc above), and a ripple here would be an open
        // invitation to find out what tapping it does - the same reasoning AppLogo's onDiceTap
        // gives for its own hidden tap target. A tap counts toward arming superuser mode; once
        // it's armed, a long press unlocks every remaining achievement, and the long press after
        // that relocks everything - see onBannerLongPress's doc comment. The haptic feedback that
        // combinedClickable fires by default for onLongClick is disabled for the same reason: a
        // buzz on long press would tip off that something's there.
        val unlockedCountInteractionSource = remember { MutableInteractionSource() }
        Card(
            modifier = Modifier.fillMaxWidth().combinedClickable(
                interactionSource = unlockedCountInteractionSource,
                indication = null,
                hapticFeedbackEnabled = false,
                onClick = viewModel::onUnlockedCountTapped,
                onLongClick = viewModel::onBannerLongPress,
            ),
        ) {
            Text(
                text = "${state.unlockedCount} of ${state.totalCount} unlocked",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.groups.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = "Everything's unlocked. Nothing left to chase!",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                    )
                }
            } else {
                // Where each category's header sits in the list: one header item, then its rows.
                val headerIndices = state.groups.runningFold(0) { index, group -> index + 1 + group.items.size }.dropLast(1)
                state.groups.forEachIndexed { groupIndex, group ->
                    stickyHeader(key = "group-${group.category.name}", contentType = HEADER_CONTENT_TYPE) {
                        GroupHeader(
                            text = group.category.label,
                            onPrevious = headerIndices.getOrNull(groupIndex - 1)?.let { target ->
                                { scope.launch { listState.animateScrollToItem(target) } }
                            },
                            onNext = headerIndices.getOrNull(groupIndex + 1)?.let { target ->
                                { scope.launch { listState.animateScrollToItem(target) } }
                            },
                        )
                    }
                    items(group.items, key = { it.achievement.id }, contentType = { ROW_CONTENT_TYPE }) {
                        AchievementRow(
                            item = it,
                            superuserModeActive = superuserModeActive,
                            onSuperuserLongPressTick = viewModel::onSuperuserLongPressTick,
                            highlighted = it.achievement.id == highlightedAchievementId,
                            modifier = Modifier.hiddenUnderPinnedHeader(listState, it.achievement.id),
                        )
                    }
                }
            }
        }
    }
}

/**
 * A theme's subheader - quiet, since the catalogue's own order is what does the real organising.
 * Pinned to the top of the list while its category scrolls by, so it's a plain [Card] rather than
 * the bare [Text] this would otherwise be - the same rounded, opaque shape every other surface on
 * this screen uses - since rows now scroll directly underneath it and would show through bare text.
 *
 * The up/down arrows on its right jump to the previous/next category's header, which then pins in
 * this one's place - a long list is otherwise a lot of flinging to get from Milestones to
 * Collection. A null [onPrevious]/[onNext] (the first/last category) greys that arrow out rather
 * than removing it, so the pair always sits in the same place. They're stock icon buttons, so the
 * header is the 48dp minimum touch height rather than the bare label's.
 */
@Composable
private fun GroupHeader(text: String, onPrevious: (() -> Unit)?, onNext: (() -> Unit)?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = text.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            )
            IconButton(onClick = { onPrevious?.invoke() }, enabled = onPrevious != null) {
                Icon(imageVector = Icons.Filled.KeyboardArrowUp, contentDescription = "Previous category")
            }
            IconButton(onClick = { onNext?.invoke() }, enabled = onNext != null) {
                Icon(imageVector = Icons.Filled.KeyboardArrowDown, contentDescription = "Next category")
            }
        }
    }
}

/**
 * [superuserModeActive] wires a hand-rolled hold-to-repeat gesture onto the whole row - the same
 * "launch a ticking coroutine on down, cancel it on up" shape `DiceTray`'s superuser die-cycling
 * uses, rather than `combinedClickable`'s `onLongClick`, which only ever fires once. Every 500ms
 * ([AchievementsViewModel.SUPERUSER_TICK_MILLIS]) the press is held, [onSuperuserLongPressTick] is
 * called with a 1-based tick count; what it does with that is entirely the ViewModel's call - see
 * its doc comment. The tick loop cancels itself the moment the finger drags past touch slop or the
 * pointer change is otherwise consumed - this screen scrolls constantly, and without that check,
 * a finger that landed on a row on its way to scrolling past it kept ticking that same row for the
 * whole scroll.
 *
 * [highlighted] briefly flashes the row gold - see [AchievementScrollRequests] - rather than a
 * static colour swap, since it's not marking new state the way the unlocked/locked colours do,
 * just pointing at a row that was already whatever it was.
 */
@Composable
private fun AchievementRow(
    item: AchievementItem,
    superuserModeActive: Boolean,
    onSuperuserLongPressTick: (Achievement, Int) -> Unit,
    highlighted: Boolean,
    modifier: Modifier = Modifier,
) {
    val unlocked = item.unlockedAt != null

    Card(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(superuserModeActive, item.achievement) {
                if (!superuserModeActive) return@pointerInput
                val touchSlop = viewConfiguration.touchSlop
                // One awaitEachGesture per press, so no event slips through between the down and
                // the tracking below; launch/cancel aren't suspending, so they can run against the
                // outer coroutineScope from inside it.
                coroutineScope {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var tickCount = 0
                        val tickJob = launch {
                            while (isActive) {
                                delay(AchievementsViewModel.SUPERUSER_TICK_MILLIS)
                                tickCount++
                                onSuperuserLongPressTick(item.achievement, tickCount)
                            }
                        }
                        // Cancels the ticking the moment this stops reading as "holding one spot"
                        // - either the finger has dragged far enough to be a scroll of the list
                        // underneath it, not a stationary long press, or an ancestor (the
                        // LazyColumn's own scroll gesture) has already consumed the change, which
                        // is exactly what happens once a real scroll takes over. Without this, a
                        // press that started a long press but turned into a scroll (scrolling to
                        // see the next row while a finger happened to land on this one first) kept
                        // ticking the whole ride down the list, firing lock/unlock ticks against
                        // whichever row it started on.
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.isConsumed || (change.position - down.position).getDistance() > touchSlop) break
                        } while (event.changes.any { it.pressed })
                        tickJob.cancel()
                    }
                }
            },
        colors = CardDefaults.cardColors(
            containerColor = animateColorAsState(
                targetValue = if (highlighted) {
                    GoldAccent
                    // Earned ones are lifted off the page; the rest stay at the page's own level.
                } else if (unlocked) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                },
                animationSpec = tween(ROW_FLASH_TRANSITION_MILLIS),
                label = "achievementRowFlash",
            ).value,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val tint = if (unlocked) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            Box(
                modifier = Modifier.size(40.dp).border(width = 1.dp, color = tint),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (unlocked) item.achievement.icon else LOCKED_ACHIEVEMENT_ICON,
                    contentDescription = null,
                    tint = if (unlocked) item.achievement.iconTintOrUnspecified(tint) else tint,
                    modifier = Modifier.size(22.dp),
                )
            }

            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = item.achievement.title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = if (item.achievement.visibility == AchievementVisibility.HIDDEN && !unlocked) {
                        "???"
                    } else {
                        item.achievement.description
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                when {
                    item.unlockedAt != null -> Text(
                        text = "Unlocked ${formatTimestamp(item.unlockedAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )

                    item.achievement.hasProgressBar -> ProgressRow(item)
                }
            }
        }
    }
}

/** The progress bar on a locked, countable achievement - a win streak, or a running total. */
@Composable
private fun ProgressRow(item: AchievementItem) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
        LinearProgressIndicator(
            progress = { item.progressFraction },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "${item.progress.grouped()} of ${item.achievement.target.grouped()}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/**
 * Stops a row drawing anywhere above the bottom edge of the pinned category header, so it's cut off
 * at that edge rather than sliding underneath it. The header covers what's behind it, except at its
 * rounded corners, where a row passing underneath still showed through; clipping the row takes it
 * out of that area entirely. The pinned header is the topmost header in the list's layout: while
 * the next one pushes it up, its bottom edge moves too, and the clip follows it.
 *
 * Only affects drawing. The header is laid out on top, so it still takes any touch in that area.
 * Reads [LazyListState.layoutInfo] during the draw pass, so scrolling only redraws the rows and
 * doesn't recompose them.
 */
private fun Modifier.hiddenUnderPinnedHeader(listState: LazyListState, key: Any): Modifier = drawWithContent {
    val visible = listState.layoutInfo.visibleItemsInfo
    val pinnedHeader = visible.filter { it.contentType == HEADER_CONTENT_TYPE }.minByOrNull { it.offset }
    val self = visible.firstOrNull { it.key == key }
    val hiddenHeight = if (pinnedHeader == null || self == null) 0f else (pinnedHeader.offset + pinnedHeader.size - self.offset).toFloat()
    when {
        hiddenHeight <= 0f -> drawContent()
        hiddenHeight >= size.height -> Unit
        else -> clipRect(top = hiddenHeight) { this@drawWithContent.drawContent() }
    }
}
