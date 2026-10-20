package net.zodac.dicefive.ui.achievements

import net.zodac.dicefive.ui.common.LocalLandscapePage
import androidx.compose.runtime.SideEffect
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.navigation.AchievementScrollRequests
import net.zodac.dicefive.resources.achievements_missing_action
import net.zodac.dicefive.resources.achievements_missing_message
import net.zodac.dicefive.resources.achievements_missing_tooltip
import net.zodac.dicefive.resources.achievements_missing_modes_action
import net.zodac.dicefive.resources.achievements_missing_modes_message
import net.zodac.dicefive.resources.achievements_missing_modes_tooltip
import net.zodac.dicefive.resources.achievements_missing_numbers_action
import net.zodac.dicefive.resources.achievements_missing_numbers_message
import net.zodac.dicefive.resources.achievements_missing_numbers_tooltip
import net.zodac.dicefive.resources.achievements_mode_finished
import net.zodac.dicefive.resources.achievements_number_label
import net.zodac.dicefive.resources.achievements_number_scored
import net.zodac.dicefive.resources.achievements_score_recorded
import net.zodac.dicefive.resources.common_done
import net.zodac.dicefive.resources.common_ok
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.model.AchievementCategory
import net.zodac.dicefive.model.AchievementVisibility
import net.zodac.dicefive.platform.LocalPlatformServices
import net.zodac.dicefive.resources.Res
import net.zodac.dicefive.resources.achievements_all_unlocked
import net.zodac.dicefive.resources.achievements_hidden_description
import net.zodac.dicefive.resources.achievements_next_category_cd
import net.zodac.dicefive.resources.achievements_previous_category_cd
import net.zodac.dicefive.resources.achievements_progress
import net.zodac.dicefive.resources.achievements_reward_locked
import net.zodac.dicefive.resources.achievements_reward_show_action
import net.zodac.dicefive.resources.achievements_reward_unlocked
import net.zodac.dicefive.resources.achievements_title
import net.zodac.dicefive.resources.achievements_unlocked_at
import net.zodac.dicefive.resources.achievements_locked_only
import net.zodac.dicefive.resources.achievements_category_count
import net.zodac.dicefive.resources.achievements_unlocked_count
import net.zodac.dicefive.ui.common.accessibleCombinedClickable
import net.zodac.dicefive.ui.common.LazyListScrollbar
import net.zodac.dicefive.ui.common.AppTooltip
import net.zodac.dicefive.ui.common.AppTooltipState
import net.zodac.dicefive.ui.common.DiceFiveDialog
import net.zodac.dicefive.ui.common.ScreenScaffold
import net.zodac.dicefive.ui.common.VerticalScrollbar
import net.zodac.dicefive.ui.common.delayWhileResumed
import net.zodac.dicefive.ui.common.localised
import net.zodac.dicefive.ui.common.timestamp
import net.zodac.dicefive.ui.common.grouped
import net.zodac.dicefive.ui.common.rememberAppTooltipState
import net.zodac.dicefive.ui.common.stringResource
import net.zodac.dicefive.ui.style.styleRewards
import net.zodac.dicefive.ui.style.unlocksStyle
import net.zodac.dicefive.ui.theme.GoldAccent

// contentType tags, so hiddenUnderPinnedHeader can tell a category header from a row by the list's
// own layout info rather than by parsing keys.
private const val HEADER_CONTENT_TYPE = "header"
private const val ROW_CONTENT_TYPE = "achievement"
private const val TAIL_CONTENT_TYPE = "tail"

/** How long a row scrolled to from [AchievementScrollRequests] stays flashed gold before fading
 * back to its normal colour - long enough to register as "here it is", short enough to still read
 * as a flash rather than a new steady state. */
private const val ROW_FLASH_HOLD_MILLIS = 900L
private const val ROW_FLASH_TRANSITION_MILLIS = 400

/** Achievements to a row on a landscape page. */
private const val LANDSCAPE_COLUMNS = 2

/** How many list items a group's [count] achievements take, [columns] to a row. */
private fun rowCount(count: Int, columns: Int): Int = (count + columns - 1) / columns

/** The list's key for a header - see [indexOfKey]. */
private fun headerKey(category: AchievementCategory): String = "group-${category.name}"

/** The flat `LazyColumn` index of the achievement with [achievementId], mirroring exactly how the
 * list itself is built below - one header item per group, then its rows of [columns] - or null if it isn't in
 * [groups] at all (a still-locked secret achievement, say - see the class doc above). */
private fun flatIndexOf(groups: List<AchievementGroup>, achievementId: String, columns: Int): Int? {
    var index = 0
    for (group in groups) {
        index++ // the group's own sticky header
        val itemIndex = group.items.indexOfFirst { it.achievement.id == achievementId }
        if (itemIndex != -1) return index + itemIndex / columns
        index += rowCount(group.items.size, columns)
    }
    return null
}

/**
 * The flat index of the list item with [key] - a header's ([headerKey]), or a row's, which is its first achievement's id - in
 * the list laid out [columns] to a row, or the row holding that achievement where it's laid out with another number. Null if
 * it isn't in [groups].
 */
private fun indexOfKey(groups: List<AchievementGroup>, key: Any?, columns: Int): Int? {
    var index = 0
    for (group in groups) {
        if (key == headerKey(group.category)) return index
        index += 1 + rowCount(group.items.size, columns)
    }
    return (key as? String)?.let { flatIndexOf(groups, it, columns) }
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

/** How far into the page's margin the list's scrollbar sits, past the cards' edge. */
private val LIST_SCROLLBAR_OFFSET = 12.dp

/** The missing-numbers list's scrollbar, at its end edge; the list keeps as much clear at each side. */
private val MISSING_SCROLLBAR_WIDTH = 8.dp

/** The pinned category header's height (48dp arrows plus card padding), until it's measured. */
private val ESTIMATED_HEADER_HEIGHT_DP = 56.dp

/** The `scrollOffset` to pass to `animateScrollToItem`/`scrollToItem` so the item lands roughly
 * centred rather than pinned to the very top. That parameter's sign is the opposite of what it
 * looks like it should be: positive scrolls the item *further towards/off the top* (it's "how far
 * forward the list has scrolled past this item resting at the top"), so pulling it down into the
 * middle of the viewport needs a negative value - hence the minus sign below, and the magnitude is
 * clamped non-negative first only so this can't ever come out positive by accident. */
private fun LazyListState.centeredScrollOffset(estimatedItemHeightPx: Int): Int {
    val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
    return -((viewportHeight - estimatedItemHeightPx) / 2).coerceAtLeast(0)
}

/**
 * Where the list was last scrolled to. Leaving this screen pops it off the back stack, which
 * discards both its ViewModel and anything `rememberSaveable`d, so the position has to live
 * somewhere longer-lived to still be there on the way back in. In memory only: it survives
 * navigating away and back for as long as the app process does, not an app restart.
 */
private object AchievementsScrollMemory {
    // By the key of the first item showing, not its index: the list has two achievements to a row in landscape, so the same
    // place has another index after a turn of the device.
    var firstVisibleItemKey: Any? = null
    var firstVisibleItemScrollOffset = 0
    var onlyLocked = false
}

/** Forgets the remembered place and the Locked only switch, so the list opens at the top with every row - tests start from there. */
fun resetAchievementsScrollMemory() {
    AchievementsScrollMemory.firstVisibleItemKey = null
    AchievementsScrollMemory.firstVisibleItemScrollOffset = 0
    AchievementsScrollMemory.onlyLocked = false
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
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val superuserModeActive by viewModel.superuserModeActive.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle

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
    // Achievements to a row: LANDSCAPE_COLUMNS on a landscape page, one otherwise. Set by the page below, which knows its shape.
    var columns by remember { mutableIntStateOf(1) }
    var onlyLocked by remember { mutableStateOf(AchievementsScrollMemory.onlyLocked) }
    val groups = remember(state.groups, onlyLocked) {
        if (!onlyLocked) {
            state.groups
        } else {
            state.groups.map { group -> group.copy(items = group.items.filter { it.unlockedAt == null }) }.filter { it.items.isNotEmpty() }
        }
    }
    val fullGroups = state.groups
    val scope = rememberCoroutineScope()
    val currentGroups by rememberUpdatedState(groups)
    var restoredScroll by remember { mutableStateOf(false) }
    val listLoaded = state.isLoaded
    LaunchedEffect(listLoaded) {
        if (listLoaded && !restoredScroll) {
            if (AchievementScrollRequests.pending() == null) {
                indexOfKey(groups, AchievementsScrollMemory.firstVisibleItemKey, columns)?.let { index ->
                    listState.scrollToItem(index, AchievementsScrollMemory.firstVisibleItemScrollOffset)
                }
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
            snapshotFlow { state }.first { it.isLoaded }
            // The filtered groups are what its index is counted in; an unlocked row isn't in them while only the locked are shown.
            val flatIndex = flatIndexOf(currentGroups, request.achievementId, columns)
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
                lifecycle.delayWhileResumed(ROW_FLASH_HOLD_MILLIS)
                if (highlightedAchievementId == request.achievementId) highlightedAchievementId = null
            }
            AchievementScrollRequests.consumePending()
        }
    }

    DisposableEffect(listState) {
        onDispose {
            if (restoredScroll) {
                AchievementsScrollMemory.firstVisibleItemKey = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.key
                AchievementsScrollMemory.firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset
            }
        }
    }

    // Room after the last row so it can scroll up to rest just under the pinned header (and so a
    // banner long press can centre the rows at the bottom, instead of leaving them clamped at the
    // list's end, under the unlock banner). Sized as viewport - pinned header - last row, both
    // measured once seen; the header/row estimates only cover the first frames.
    var tailSpacePx by remember { mutableIntStateOf(0) }
    val headerEstimatePx = with(LocalDensity.current) { ESTIMATED_HEADER_HEIGHT_DP.roundToPx() }
    LaunchedEffect(listState) {
        var headerPx = headerEstimatePx
        var lastRowPx = estimatedRowHeightPx
        snapshotFlow { listState.layoutInfo }.collect { info ->
            info.visibleItemsInfo.firstOrNull { it.contentType == HEADER_CONTENT_TYPE }?.let { headerPx = it.size }
            info.visibleItemsInfo.lastOrNull { it.contentType == ROW_CONTENT_TYPE }
                ?.takeIf { it.index == info.totalItemsCount - 2 }?.let { lastRowPx = it.size }
            tailSpacePx = (info.viewportSize.height - headerPx - lastRowPx).coerceAtLeast(0)
        }
    }

    // The unlocked count, and the switch to show only what's still locked: a row of their own over the list, or [compact] in the
    // app bar's end on a landscape page, where the list wants the height.
    val unlockedCount = @Composable { countModifier: Modifier, compact: Boolean ->
        // No ripple, and no other visual change on tap or long press: this is the hidden entry
        // point into superuser mode (see the class doc above), and a ripple here would be an open
        // invitation to find out what tapping it does - the same reasoning AppLogo's onDiceTap
        // gives for its own hidden tap target. A tap counts toward arming superuser mode; once
        // it's armed, a long press unlocks every remaining achievement, and the long press after
        // that relocks everything - see onBannerLongPress's doc comment. No haptic feedback on long
        // press either: a buzz would tip off that something's there.
        Card(
            // Raw gestures, not combinedClickable: that would tell TalkBack this is a button with a
            // long-press action, announcing a control that (to anyone not in on it) does nothing.
            modifier = countModifier.pointerInput(Unit) {
                detectTapGestures(
                    onTap = { viewModel.onUnlockedCountTapped() },
                    onLongPress = { viewModel.onBannerLongPress() },
                )
            },
        ) {
            Text(
                text = stringResource(Res.string.achievements_unlocked_count, state.unlockedCount, state.totalCount),
                style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = if (compact) 4.dp else 12.dp).then(if (compact) Modifier else Modifier.fillMaxWidth()),
            )
        }
    }
    val lockedOnlySwitch = @Composable {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            // One target for the label and switch, read as "Locked only, switch, on".
            // No ripple: it drew as a pale rectangle over the label, and the switch already shows the change.
            modifier = Modifier.toggleable(
                value = onlyLocked,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Switch,
            ) {
                onlyLocked = it
                AchievementsScrollMemory.onlyLocked = it
                scope.launch { listState.scrollToItem(0) }
            },
        ) {
            Text(
                text = stringResource(Res.string.achievements_locked_only),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.widthIn(max = 104.dp),
            )
            Switch(checked = onlyLocked, onCheckedChange = null)
        }
    }

    ScreenScaffold(
        title = stringResource(Res.string.achievements_title),
        onBack = onBack,
        modifier = modifier,
        wide = true,
        actions = {
            if (LocalLandscapePage.current && state.isLoaded) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(end = 8.dp)) {
                    unlockedCount(Modifier, true)
                    lockedOnlySwitch()
                }
            }
        },
    ) {
        // Nothing but the title bar until the unlocks and scores are read: drawn any sooner, the
        // page says "0 of 0 unlocked" and "Everything's unlocked" before the real list arrives.
        if (!state.isLoaded) return@ScreenScaffold
        val pageColumns = if (LocalLandscapePage.current) LANDSCAPE_COLUMNS else 1
        SideEffect { columns = pageColumns }

        if (!LocalLandscapePage.current) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                unlockedCount(Modifier.weight(1f), false)
                lockedOnlySwitch()
            }
        }

        // The list fills the page's width; its scrollbar sits in the page's margin beside it, not over the cards.
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (groups.isEmpty()) {
                    item(key = "empty") {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = stringResource(Res.string.achievements_all_unlocked),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                            )
                        }
                    }
                } else {
                    // Where each category's header sits in the list: one header item, then its rows.
                    val headerIndices = groups.runningFold(0) { index, group -> index + 1 + rowCount(group.items.size, pageColumns) }.dropLast(1)
                    groups.forEachIndexed { groupIndex, group ->
                        stickyHeader(key = headerKey(group.category), contentType = HEADER_CONTENT_TYPE) {
                            GroupHeader(
                                text = stringResource(group.category.label),
                                // The full category, not the "locked only" filtered rows; Easter Eggs stay uncounted so their total isn't spoiled.
                                counts = if (group.category == AchievementCategory.EASTER_EGGS) {
                                    null
                                } else {
                                    fullGroups.first { it.category == group.category }.items.let { all -> all.count { it.unlockedAt != null } to all.size }
                                },
                                onPrevious = headerIndices.getOrNull(groupIndex - 1)?.let { target ->
                                    { scope.launch { listState.animateScrollToItem(target) } }
                                },
                                onNext = headerIndices.getOrNull(groupIndex + 1)?.let { target ->
                                    { scope.launch { listState.animateScrollToItem(target) } }
                                },
                            )
                        }
                        // [pageColumns] to a row, each row keyed by its first achievement - one to a row in portrait, keyed as before.
                        items(group.items.chunked(pageColumns), key = { it.first().achievement.id }, contentType = { ROW_CONTENT_TYPE }) { row ->
                            val rowKey = row.first().achievement.id
                            Row(
                                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).hiddenUnderPinnedHeader(listState, rowKey),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                for (item in row) {
                                    // Its share of the row is this box's: a row with a tooltip has the tooltip's box around its card.
                                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                        AchievementRow(
                                            item = item,
                                            superuserModeActive = superuserModeActive,
                                            onSuperuserLongPressTick = viewModel::onSuperuserLongPressTick,
                                            highlighted = item.achievement.id == highlightedAchievementId,
                                            modifier = Modifier.fillMaxHeight(),
                                        )
                                    }
                                }
                                // A row's last place left empty, so the card in it stays the width of the others.
                                repeat(pageColumns - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                            }
                        }
                    }
                    item(key = "tail-space", contentType = TAIL_CONTENT_TYPE) {
                        Spacer(Modifier.height(with(LocalDensity.current) { tailSpacePx.toDp() }))
                    }
                }
            }
            // Told which items are headers, which rows and which the tail, so its estimate of the rows not yet seen - and so the
            // thumb's size and speed - holds steady as the list scrolls past each kind.
            val headerItems = remember(groups, pageColumns) { groups.runningFold(0) { index, group -> index + 1 + rowCount(group.items.size, pageColumns) }.dropLast(1).toSet() }
            val tailItem = remember(groups, pageColumns) { groups.sumOf { 1 + rowCount(it.items.size, pageColumns) } }
            LazyListScrollbar(
                listState = listState,
                modifier = Modifier.offset(x = LIST_SCROLLBAR_OFFSET),
                contentTypeOf = { index ->
                    when (index) {
                        in headerItems -> HEADER_CONTENT_TYPE
                        tailItem -> TAIL_CONTENT_TYPE
                        else -> ROW_CONTENT_TYPE
                    }
                },
                // The tail's height is the screen's own sum, before it's ever scrolled to.
                sizeOf = { index -> tailSpacePx.takeIf { index == tailItem } },
            )
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
private fun GroupHeader(text: String, counts: Pair<Int, Int>?, onPrevious: (() -> Unit)?, onNext: (() -> Unit)?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val complete = counts != null && counts.first == counts.second
            val color = if (complete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            val spokenCount = counts?.let { stringResource(Res.string.achievements_unlocked_count, it.first, it.second) }
            Text(
                text = text.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = color,
                // Said as written, not spelled out as the all-caps it's drawn in, with the count after it.
                modifier = Modifier.weight(1f).padding(start = 12.dp).semantics {
                    heading()
                    contentDescription = if (spokenCount == null) text else "$text, $spokenCount"
                },
            )
            if (counts != null) {
                // The heading above already speaks the count.
                Text(
                    text = stringResource(Res.string.achievements_category_count, counts.first, counts.second),
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    modifier = Modifier.padding(horizontal = 8.dp).clearAndSetSemantics { },
                )
            }
            IconButton(onClick = { onPrevious?.invoke() }, enabled = onPrevious != null) {
                Icon(imageVector = Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(Res.string.achievements_previous_category_cd))
            }
            IconButton(onClick = { onNext?.invoke() }, enabled = onNext != null) {
                Icon(imageVector = Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(Res.string.achievements_next_category_cd))
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
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var showDetails by remember { mutableStateOf(false) }
    val missingLabel = stringResource(
        when {
            item.achievement.scoreBand != null -> Res.string.achievements_missing_action
            item.achievement.isEveryMode -> Res.string.achievements_missing_modes_action
            else -> Res.string.achievements_missing_numbers_action
        },
    )
    val listsMissing = !unlocked && !superuserModeActive && item.missing.isNotEmpty()
    // A tap shows the short tip - the style(s) it unlocks, and how many are still missing - and a long press opens the
    // full dialog, as on the Styles screen. A row with neither has no gesture at all.
    val rewardMessage = rewardTooltipMessage(item.achievement, unlocked)
    val missingMessage = if (listsMissing) {
        stringResource(
            when {
                item.achievement.scoreBand != null -> Res.string.achievements_missing_tooltip
                item.achievement.isEveryMode -> Res.string.achievements_missing_modes_tooltip
                else -> Res.string.achievements_missing_numbers_tooltip
            },
            item.missing.size.grouped(),
        )
    } else {
        null
    }
    val tipMessage = listOfNotNull(rewardMessage, missingMessage).joinToString("\n").ifEmpty { null }.takeIf { !superuserModeActive }
    val detailsLabel = if (listsMissing) missingLabel else stringResource(Res.string.achievements_reward_show_action)
    val tooltipState = rememberAppTooltipState()
    val scope = rememberCoroutineScope()
    if (showDetails && tipMessage != null) {
        if (listsMissing) {
            MissingDialog(item, onDismiss = { showDetails = false })
        } else {
            DiceFiveDialog(
                icon = item.achievement.icon,
                iconTint = item.achievement.iconTintOrUnspecified(MaterialTheme.colorScheme.primary),
                title = stringResource(item.achievement.title),
                message = rewardMessage.orEmpty(),
                confirmLabel = stringResource(Res.string.common_ok),
                onConfirm = { showDetails = false },
                onDismissRequest = { showDetails = false },
            )
        }
    }
    // The flash is gold, which the usual text, icon and border colours (light, or gold itself) vanish into: on it,
    // they all become the unlocked row's own blue instead.
    val flashInk = MaterialTheme.colorScheme.secondaryContainer
    // Fades in step with the card's own colour, so the text never sits dark on the old blue (or pale on the gold).
    val flash by animateFloatAsState(
        targetValue = if (highlighted) 1f else 0f,
        animationSpec = tween(ROW_FLASH_TRANSITION_MILLIS),
        label = "achievementRowFlashInk", // i18n: not translated - an animation label, not shown
    )
    fun ink(normal: Color): Color = lerp(normal, flashInk, flash)

    RowTooltip(tipMessage, tooltipState) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .then(
                    if (tipMessage != null) {
                        // A tooltip isn't announced, so a screen reader's one action is the dialog, which says the same and more.
                        Modifier.accessibleCombinedClickable(
                            onClick = { scope.launch { tooltipState.show() } },
                            onClickLabel = detailsLabel,
                            onLongClick = { showDetails = true },
                            onLongClickLabel = detailsLabel,
                            role = Role.Button,
                            screenReaderClick = { showDetails = true },
                        )
                    } else {
                        Modifier
                    },
                )
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
                                    lifecycle.delayWhileResumed(AchievementsViewModel.SUPERUSER_TICK_MILLIS)
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
                    label = "achievementRowFlash", // i18n: not translated - an animation label, not shown
                ).value,
            ),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val tint = ink(
                    if (unlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(modifier = Modifier.size(40.dp)) {
                    Box(
                        modifier = Modifier.fillMaxSize().border(width = 1.dp, color = tint),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (unlocked) item.achievement.icon else LOCKED_ACHIEVEMENT_ICON,
                            contentDescription = null,
                            tint = if (unlocked) item.achievement.iconTintOrUnspecified(tint) else tint,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    if (item.achievement.unlocksStyle) StyleRewardStar(tint = tint)
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(item.achievement.title),
                        style = MaterialTheme.typography.titleSmall,
                        color = ink(LocalContentColor.current),
                    )
                    Text(
                        text = if (item.achievement.visibility == AchievementVisibility.HIDDEN && !unlocked) {
                            stringResource(Res.string.achievements_hidden_description)
                        } else {
                            stringResource(item.achievement.description)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = ink(MaterialTheme.colorScheme.onSurfaceVariant),
                    )

                    when {
                        item.unlockedAt != null -> Text(
                            text = stringResource(Res.string.achievements_unlocked_at, timestamp(item.unlockedAt)),
                            style = MaterialTheme.typography.labelSmall,
                            color = ink(MaterialTheme.colorScheme.onSurfaceVariant),
                            modifier = Modifier.padding(top = 2.dp),
                        )

                        item.achievement.hasProgressBar -> ProgressRow(item, ::ink)
                    }
                }
            }
        }
    }
}

/**
 * Everything a collection achievement needs - a score band's scores, Full Set's numbers ("1s" to "6s"), or Grand Tour's
 * game modes - the ones already collected struck through and faded, in a scrollable pop-up. Modes go one to a line, as
 * a name of several words run together with the next would be hard to tell apart.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MissingDialog(item: AchievementItem, onDismiss: () -> Unit) {
    val achievement = item.achievement
    val isScores = achievement.scoreBand != null
    DiceFiveDialog(
        icon = achievement.icon,
        iconTint = achievement.iconTintOrUnspecified(MaterialTheme.colorScheme.primary),
        title = stringResource(achievement.title),
        message = stringResource(
            when {
                isScores -> Res.string.achievements_missing_message
                achievement.isEveryMode -> Res.string.achievements_missing_modes_message
                else -> Res.string.achievements_missing_numbers_message
            },
            item.missing.size.grouped(),
        ),
        confirmLabel = stringResource(Res.string.common_done),
        onConfirm = onDismiss,
        onDismissRequest = onDismiss,
    ) {
        val scroll = rememberScrollState()
        // A long list scrolls, with a scrollbar at its edge to say so.
        Box(modifier = Modifier.padding(top = 12.dp).heightIn(max = 240.dp)) {
            FlowRow(
                // Kept clear of the scrollbar at either side, so the centred entries stay centred.
                modifier = Modifier.fillMaxWidth().verticalScroll(scroll).padding(horizontal = MISSING_SCROLLBAR_WIDTH),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                maxItemsInEachRow = if (achievement.isEveryMode) 1 else Int.MAX_VALUE,
            ) {
                // Every entry, the collected ones struck through and faded; TalkBack gets the same as words.
                achievement.checklist?.forEach { entry ->
                    val collected = entry in item.collected
                    val text = when {
                        isScores -> entry.grouped()
                        achievement.isEveryMode -> stringResource(GameMode.entries[entry].displayName)
                        else -> stringResource(Res.string.achievements_number_label, entry.localised())
                    }
                    val spoken = when {
                        !collected -> text
                        isScores -> stringResource(Res.string.achievements_score_recorded, text)
                        achievement.isEveryMode -> stringResource(Res.string.achievements_mode_finished, text)
                        else -> stringResource(Res.string.achievements_number_scored, text)
                    }
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (collected) 0.38f else 1f),
                        textDecoration = if (collected) TextDecoration.LineThrough else null,
                        modifier = Modifier.semantics { contentDescription = spoken },
                    )
                }
            }
            Box(modifier = Modifier.matchParentSize(), contentAlignment = Alignment.TopEnd) {
                VerticalScrollbar(scroll, width = MISSING_SCROLLBAR_WIDTH)
            }
        }
    }
}

/**
 * The line(s) naming the style(s) an achievement unlocks, or null for one that unlocks none. Not marked on the
 * row (there's no icon for it): a tooltip rather than only a dialog because it's a passing "what's this?".
 */
@Composable
private fun rewardTooltipMessage(achievement: Achievement, unlocked: Boolean): String? {
    if (!achievement.unlocksStyle) return null
    return achievement.styleRewards.map { reward ->
        stringResource(if (unlocked) Res.string.achievements_reward_unlocked else Res.string.achievements_reward_locked, reward.description())
    }.joinToString("\n")
}

/**
 * The tip shown over a row's [content] when it's tapped ([state] is shown from the row's own tap), or just
 * [content] for a row with no [message]. Its own long press is off: that opens the row's dialog.
 */
@Composable
private fun RowTooltip(message: String?, state: AppTooltipState, content: @Composable () -> Unit) {
    if (message == null) {
        content()
    } else {
        AppTooltip(message = message, state = state, enabled = false, content = content)
    }
}

/** The progress bar on a locked, countable achievement - a win streak, or a running total. */
@Composable
private fun ProgressRow(item: AchievementItem, ink: (Color) -> Color) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
        LinearProgressIndicator(
            progress = { item.progressFraction },
            modifier = Modifier.fillMaxWidth(),
            color = ink(ProgressIndicatorDefaults.linearColor),
            trackColor = ink(ProgressIndicatorDefaults.linearTrackColor),
        )
        Text(
            text = stringResource(Res.string.achievements_progress, item.progress.grouped(), item.achievement.target.grouped()),
            style = MaterialTheme.typography.labelSmall,
            color = ink(MaterialTheme.colorScheme.onSurfaceVariant),
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
