package net.zodac.dicefive.ui.achievements

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.zodac.dicefive.data.achievements.AchievementEvent
import net.zodac.dicefive.data.achievements.AchievementEvents
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.ui.common.CONTENT_MAX_WIDTH
import net.zodac.dicefive.ui.common.ConfigureOverlayDialogWindow
import net.zodac.dicefive.ui.common.DiceFiveDialog
import net.zodac.dicefive.ui.common.grouped

/** How long a banner sits at full opacity before it starts to go. */
private const val HOLD_MILLIS = 2_000L

/** Deliberately unhurried: the end of a game pops several, and a snap-out would read as a glitch. */
private const val FADE_OUT_MILLIS = 900

private const val FADE_IN_MILLIS = 180

/** A swipe leaves quickly - the player has said they're done with it. */
private const val SWIPE_OUT_MILLIS = 180

/** How long a progress banner's count and bar take to climb from the old value to the new one. */
private const val PROGRESS_COUNT_MILLIS = 700

/** The gap between two banners appearing, so a burst arrives as a stack being dealt, not a wall. */
private const val STAGGER_MILLIS = 300L

/**
 * At most this many on screen at once. A finished game can unlock a dozen, and the rest wait their
 * turn rather than being dropped - see the admission wait in [AchievementBannerHost].
 */
private const val MAX_VISIBLE_BANNERS = 4

/** How far across itself a banner must be dragged to count as "get rid of this" - down from an
 * original 0.25f, which needed too firm a swipe to register. */
private const val SWIPE_DISMISS_FRACTION = 0.15f

/** How much of an older banner peeks out above the one in front of it, in a stack - just enough
 * to show it's there and to stay tappable/swipeable on its own, without the pile eating the
 * screen the way one full-height row per banner used to. Measured from the top of the banner in
 * front of it, not a fixed offset from the stack's own bottom - see the custom `Layout` in
 * [AchievementBannerHost] - since banners aren't all the same height (a one-line description takes
 * less room than a two-line one), and a fixed offset from a shared baseline would show more or less
 * of each one than this depending on that difference, sometimes none of it at all. */
private const val STACK_PEEK_DP = 14

/** A faint outline on every banner, so a stack of them - which overlap with no gap between - reads
 * as separate cards rather than one elongated shape. Plain black at low alpha rather than a theme
 * colour: it needs to work over both the primary-container unlock banner and the
 * surface-container-high progress one without picking a tint that clashes with either. */
private val BANNER_BORDER_COLOR = Color.Black.copy(alpha = 0.12f)

private data class BannerItem(val key: Long, val event: AchievementEvent)

/**
 * Wraps the whole app so achievement banners can outlive the screen that raised them - the burst
 * at the end of a game starts on the board and carries on over the results screen.
 *
 * Banners sit in a bottom-anchored overlapping stack, the longest-queued one in front and nearest
 * the thumb, each newer arrival peeking out by [STACK_PEEK_DP] further back - a burst of several no
 * longer fills the screen. Only the front banner is interactive (swipe or long press); the ones
 * peeking out behind it are inert until it clears, so a swipe can never accidentally land on the
 * wrong one underneath. An unlock always takes the front position over a progress nudge, regardless
 * of which arrived first - see [displayOrder] - but otherwise clearing the front always promotes
 * whichever banner was peeking right behind it, never a fresher arrival that only just got admitted
 * off the backlog: see the `add(0, ...)` in the collector below, and [displayOrder]'s comment, for
 * why a brand new banner has to join the *back* of the stack rather than the front, or it would cut
 * the queue the instant a slot freed up for it. Each banner leaves on its own: a hold, then a slow
 * fade. A horizontal swipe in either direction, or clearing its long-press description dialog,
 * doesn't wait for that.
 *
 * The stack renders in its own [Dialog] window, not as part of [content] - an achievement can fire
 * while a dialog (Settings' credits, a rules dialog, a confirmation) is already on screen, and a
 * new window is always drawn above whatever else was already showing when it appeared, so this
 * keeps the banner - and the description dialog its long press opens - from ending up stuck behind
 * one. A `Dialog` was chosen over a `Popup` for this because a `Popup`'s window is attached as a
 * panel of its parent (here, the main content's own window) and stacks relative to *that*, not to
 * other independent top-level windows like another already-open `Dialog` - so it could still end
 * up under one, however recently it was created. [ConfigureOverlayDialogWindow] then turns that
 * dialog window into a non-modal overlay - no dim, and no swallowing touches/back-presses outside
 * its own content - so it doesn't behave like a real dialog itself.
 */
@Composable
fun AchievementBannerHost(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val banners = remember { mutableStateListOf<BannerItem>() }
    // Which banner (if any) has its long-press description dialog open - only ever the front one,
    // since only it is interactive, but read by every banner below so ALL of their hold countdowns
    // pause together, not just the one actually showing the dialog. Otherwise a banner peeking out
    // behind it could still time out and clear itself while the dialog was up.
    var descriptionShownForKey by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(Unit) {
        var nextKey = 0L
        AchievementEvents.events.collect { event ->
            // Wait for a free slot rather than evicting a banner that's still being read. The
            // event flow buffers the backlog, and suspending here applies the back-pressure.
            snapshotFlow { banners.size }.first { it < MAX_VISIBLE_BANNERS }
            // Index 0, not appended - see displayOrder just below. A banner just admitted off the
            // backlog is the newest thing that's happened to this list, but it still has to queue
            // behind whatever's already visible; appending it would instead make it the new last
            // element, which is the front slot, letting it cut in front of a banner the player can
            // already see peeking out behind the one they're about to swipe away.
            banners.add(0, BannerItem(key = nextKey++, event = event))
            delay(STAGGER_MILLIS)
        }
    }

    Box(modifier = modifier) {
        content()
    }

    if (banners.isNotEmpty()) {
        Dialog(
            // Never called: nothing here is dismissible from outside - see ConfigureOverlayDialogWindow.
            onDismissRequest = {},
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false,
            ),
        ) {
            ConfigureOverlayDialogWindow()
            // Display order only, not the underlying list (removal below still targets `banners`
            // directly) - a real unlock always sits in front of a progress nudge, wherever in the
            // arrival order it actually landed. sortedBy is stable, so within each of the two
            // groups, `banners`' own order - oldest-still-queued first, since new arrivals are
            // inserted at the front of it, not appended (see the collector above) - is preserved.
            // Front is always the *last* element of this list, so within a type group it's always
            // the one that's been waiting longest, never one that only just joined the back.
            val displayOrder = banners.sortedBy { it.event is AchievementEvent.Unlocked }
            Layout(
                modifier = Modifier
                    .widthIn(max = CONTENT_MAX_WIDTH)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                content = {
                    displayOrder.forEachIndexed { index, item ->
                        // 0 for the front (frontmost, drawn last so it's on top), climbing for each
                        // one further back in the stack.
                        val depthFromFront = displayOrder.lastIndex - index
                        key(item.key) {
                            BannerSlot(
                                item = item,
                                interactive = depthFromFront == 0,
                                anyDescriptionShowing = descriptionShownForKey != null,
                                showOwnDescription = descriptionShownForKey == item.key,
                                onRequestDescription = { descriptionShownForKey = item.key },
                                onDismissDescription = { descriptionShownForKey = null },
                                onDismissed = { banners.remove(item) },
                                modifier = Modifier.zIndex(index.toFloat()),
                            )
                        }
                    }
                },
            ) { measurables, constraints ->
                // A banner's own description can be one or two lines, so banners aren't all the
                // same height - measured here, not assumed, so the stack's own size and each
                // banner's peek are both based on actual heights rather than a guess that's wrong
                // as often as it's right.
                val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
                val peekPx = STACK_PEEK_DP.dp.roundToPx()
                val width = placeables.maxOfOrNull { it.width } ?: 0
                // The front banner (last in the list - see displayOrder above) sets the stack's
                // baseline height; each one behind it needs just enough extra room for its own
                // STACK_PEEK_DP sliver, not its own full height, since the rest of it sits behind
                // whatever's in front - see the placement loop below.
                val frontHeight = placeables.lastOrNull()?.height ?: 0
                val height = frontHeight + (placeables.size - 1).coerceAtLeast(0) * peekPx
                layout(width, height) {
                    placeables.forEachIndexed { index, placeable ->
                        val depthFromFront = placeables.lastIndex - index
                        // Every banner's top sits exactly STACK_PEEK_DP above the top of the one
                        // in front of it - not above its own bottom-aligned position, which is
                        // what a fixed per-banner offset from the stack's own bottom would give,
                        // and which only lines up when every banner happens to be the same height.
                        val y = height - frontHeight - depthFromFront * peekPx
                        placeable.placeRelative(x = (width - placeable.width) / 2, y = y)
                    }
                }
            }
        }
    }
}

/**
 * One banner's lifetime and gestures. The slot owns the animation; [onDismissed] only removes it
 * from the list, and is safe to call twice if a swipe lands during the closing fade.
 *
 * [interactive] gates the gesture recognizer entirely - false for every banner but the front one
 * in the stack, so a swipe or long press can only ever land on the one actually on top; the ones
 * peeking out behind it don't so much as consume the touch.
 *
 * The hold countdown only runs while [interactive] is true - a banner peeking out behind the
 * front one doesn't start timing out until it's actually promoted to the front, so a burst of
 * several banners each get their own full [HOLD_MILLIS] once it's their turn rather than all
 * ticking down together and clearing within moments of each other. [anyDescriptionShowing] pauses
 * it further on top of that, for the one banner it's actually possible to open a dialog on (the
 * front one - see [interactive]).
 *
 * A single gesture recognizer handles both a horizontal swipe (dismiss) and a long press (show
 * the full description) - they have to live in the same `pointerInput` block rather than two
 * separate ones, since both start from the same down event and only diverge once the finger
 * either moves past touch slop (a swipe) or the long-press timeout elapses first (neither moved).
 * A long press followed by drag is treated as a swipe, same as anywhere else in Android - the
 * timeout is cancelled the moment real movement is seen.
 */
@Composable
private fun BannerSlot(
    item: BannerItem,
    interactive: Boolean,
    anyDescriptionShowing: Boolean,
    showOwnDescription: Boolean,
    onRequestDescription: () -> Unit,
    onDismissDescription: () -> Unit,
    onDismissed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val alpha = remember { Animatable(0f) }
    val offsetX = remember { Animatable(0f) }
    var swipedAway by remember { mutableStateOf(false) }

    // Fades in as soon as it's placed in the stack, whether or not it's the front banner yet -
    // every banner in a burst should be visible right away, even the ones peeking out behind the
    // front one that aren't counting down yet.
    LaunchedEffect(Unit) {
        alpha.animateTo(1f, tween(FADE_IN_MILLIS))
    }

    // Re-runs whenever this banner is promoted to/demoted from the front of the stack, or its
    // description dialog opens/closes: becoming the front banner is what starts its hold countdown
    // in the first place, and losing that status (shouldn't normally happen, but is handled the
    // same way for safety) or opening its dialog cancels whatever's left of it (the
    // `return@LaunchedEffect` below). Closing the dialog starts a fresh full-length hold rather
    // than resuming a partial one - reading the description is itself a reason to stick around.
    LaunchedEffect(interactive, anyDescriptionShowing, swipedAway) {
        if (!interactive || swipedAway || anyDescriptionShowing) return@LaunchedEffect
        delay(HOLD_MILLIS)
        if (!swipedAway) {
            alpha.animateTo(0f, tween(FADE_OUT_MILLIS))
            onDismissed()
        }
    }

    Box(
        modifier = modifier
            .widthIn(max = CONTENT_MAX_WIDTH)
            .fillMaxWidth()
            .offset { IntOffset(offsetX.value.roundToInt(), 0) }
            .alpha(alpha.value)
            .then(
                if (!interactive) {
                    Modifier
                } else {
                    Modifier.pointerInput(item.key) {
                        val longPressTimeoutMillis = viewConfiguration.longPressTimeoutMillis
                        val touchSlop = viewConfiguration.touchSlop
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var dragging = false
                            var previousX = down.position.x
                            val longPressJob = scope.launch {
                                delay(longPressTimeoutMillis)
                                if (!dragging) onRequestDescription()
                            }
                            try {
                                do {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!dragging) {
                                        val totalDeltaX = change.position.x - down.position.x
                                        if (abs(totalDeltaX) > touchSlop) {
                                            dragging = true
                                            longPressJob.cancel()
                                        }
                                    }
                                    if (dragging) {
                                        change.consume()
                                        val deltaX = change.position.x - previousX
                                        scope.launch { offsetX.snapTo(offsetX.value + deltaX) }
                                    }
                                    previousX = change.position.x
                                } while (event.changes.any { it.pressed })
                            } finally {
                                longPressJob.cancel()
                            }

                            if (dragging) {
                                scope.launch {
                                    if (abs(offsetX.value) > size.width * SWIPE_DISMISS_FRACTION) {
                                        swipedAway = true
                                        val target = size.width.toFloat() * if (offsetX.value > 0) 1 else -1
                                        launch { alpha.animateTo(0f, tween(SWIPE_OUT_MILLIS)) }
                                        offsetX.animateTo(target, tween(SWIPE_OUT_MILLIS))
                                        onDismissed()
                                    } else {
                                        offsetX.animateTo(0f, tween(SWIPE_OUT_MILLIS))
                                    }
                                }
                            }
                        }
                    }
                },
            ),
    ) {
        when (val event = item.event) {
            is AchievementEvent.Unlocked -> UnlockedBanner(event.achievement)
            is AchievementEvent.Progressed -> ProgressBanner(event.achievement, event.previous, event.current, interactive)
        }
    }

    if (showOwnDescription) {
        val defaultIconTint = MaterialTheme.colorScheme.primary
        DiceFiveDialog(
            // The achievement's own icon for an unlock, same as its banner; a progress nudge keeps
            // the generic trophy, since nothing has been earned yet.
            icon = when (val event = item.event) {
                is AchievementEvent.Unlocked -> event.achievement.icon
                is AchievementEvent.Progressed -> Icons.Filled.EmojiEvents
            },
            iconTint = when (val event = item.event) {
                is AchievementEvent.Unlocked -> event.achievement.iconTintOrUnspecified(defaultIconTint)
                is AchievementEvent.Progressed -> defaultIconTint
            },
            title = item.event.achievement.title,
            message = item.event.achievement.description,
            confirmLabel = "Got it",
            onConfirm = onDismissDescription,
            onDismissRequest = onDismissDescription,
        )
    }
}

/** The smallest a banner's title shrinks to before it's ellipsised instead (titleMedium, its normal
 * size, is 16sp), and how finely it steps down from there. */
private val BANNER_TITLE_MIN_FONT_SIZE = 12.sp
private val BANNER_TITLE_FONT_STEP = 0.5.sp

/**
 * An achievement's title on one line: at titleMedium when it fits, otherwise stepped down until it
 * does - "Rules? Where We're Going, We Don't Need Rules" needs about 12.4sp on a typical phone - but
 * never below [BANNER_TITLE_MIN_FONT_SIZE], still comfortably readable at a glance. Past that floor
 * (a narrower screen, or a longer title) it's ellipsised rather than shrunk further.
 */
@Composable
private fun BannerTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(
            minFontSize = BANNER_TITLE_MIN_FONT_SIZE,
            maxFontSize = MaterialTheme.typography.titleMedium.fontSize,
            stepSize = BANNER_TITLE_FONT_STEP,
        ),
        modifier = modifier,
    )
}

/** How many lines an unlock banner's description gets before it's ellipsised - the longest ones
 * ("Have a scoring option after the 2nd roll, then leave yourself with none after the 3rd") still
 * need two even at the smallest size below. */
private const val BANNER_DESCRIPTION_MAX_LINES = 2

/** The smallest a banner's description shrinks to before it's ellipsised instead (bodySmall, its
 * normal size, is 12sp), and how finely it steps down from there - finer than the title's, since
 * there's less room to spare on the way down to a size that's still legible at all. */
private val BANNER_DESCRIPTION_MIN_FONT_SIZE = 9.sp
private val BANNER_DESCRIPTION_FONT_STEP = 0.25.sp

/**
 * An achievement's description under its title, always exactly [BANNER_DESCRIPTION_MAX_LINES]
 * lines - a short one-liner reserves the same space as a long two-liner rather than leaving the
 * card shorter, so every unlock banner is the same size no matter which achievement it's for: at
 * bodySmall when it fits, otherwise stepped down until it does, same idea as [BannerTitle] but
 * smaller throughout, since the description is the secondary line and there are two of them to fit
 * where the title only ever needed one.
 */
@Composable
private fun BannerDescription(description: String, modifier: Modifier = Modifier) {
    Text(
        text = description,
        style = MaterialTheme.typography.bodySmall,
        minLines = BANNER_DESCRIPTION_MAX_LINES,
        maxLines = BANNER_DESCRIPTION_MAX_LINES,
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(
            minFontSize = BANNER_DESCRIPTION_MIN_FONT_SIZE,
            maxFontSize = MaterialTheme.typography.bodySmall.fontSize,
            stepSize = BANNER_DESCRIPTION_FONT_STEP,
        ),
        modifier = modifier,
    )
}

/**
 * The full-fat banner: something was actually earned. Shows the achievement's own icon in the same
 * bordered square the Achievements screen shows it in once it's unlocked, rather than a generic
 * trophy, so a burst of unlocks reads as distinct achievements at a glance, not a stack of
 * identical cups.
 */
@Composable
private fun UnlockedBanner(achievement: Achievement) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        border = BorderStroke(1.dp, BANNER_BORDER_COLOR),
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val defaultIconTint = LocalContentColor.current
            Box(
                modifier = Modifier.size(40.dp).border(width = 1.dp, color = defaultIconTint),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = achievement.icon,
                    contentDescription = null,
                    tint = achievement.iconTintOrUnspecified(defaultIconTint),
                    modifier = Modifier.size(22.dp),
                )
            }
            Column {
                BannerTitle(achievement.title)
                BannerDescription(achievement.description)
            }
        }
    }
}

/**
 * The quieter one: not earned yet, but closer. Same footprint as [UnlockedBanner] (padding, icon
 * size) so the stack doesn't jump in size as progress and unlock banners mix - only the colour
 * keeps it lower emphasis, so it still can't be mistaken for the real thing.
 *
 * The count and the bar both animate from [previous] to [current] rather than snapping straight
 * to the new value, so a progress nudge visibly climbs instead of just appearing already-there.
 * That climb only plays once this banner is the front of the stack - see [interactive] - rather
 * than while it's still peeking out behind another one, unseen: starting it early would have it
 * finish (or even fully play out) before the player ever gets to watch it count up.
 */
@Composable
private fun ProgressBanner(achievement: Achievement, previous: Int, current: Int, interactive: Boolean) {
    val animatedProgress = remember { Animatable(previous.toFloat()) }
    LaunchedEffect(interactive) {
        if (!interactive) return@LaunchedEffect
        animatedProgress.animateTo(current.toFloat(), tween(PROGRESS_COUNT_MILLIS))
    }
    val displayedValue = animatedProgress.value.roundToInt()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        border = BorderStroke(1.dp, BANNER_BORDER_COLOR),
        shadowElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(imageVector = Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null, modifier = Modifier.size(28.dp))
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BannerTitle(achievement.title, modifier = Modifier.weight(1f, fill = false))
                    Text(
                        text = "${displayedValue.grouped()} of ${achievement.target.grouped()}",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                LinearProgressIndicator(
                    progress = { (animatedProgress.value / achievement.target).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
            }
        }
    }
}
