package net.zodac.dicefive.ui.achievements

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.TimeSource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.zodac.dicefive.app.LocalAppContainer
import net.zodac.dicefive.data.achievements.AchievementEvent
import net.zodac.dicefive.data.achievements.UnlockedStyle
import net.zodac.dicefive.data.achievements.AchievementEvents
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.ui.common.SoraFontFamily
import net.zodac.dicefive.ui.common.delayWhileResumed
import net.zodac.dicefive.ui.game.LocalLeaveGameConfirmation
import net.zodac.dicefive.ui.game.style.unlocksStyle
import net.zodac.dicefive.ui.common.CONTENT_MAX_WIDTH
import net.zodac.dicefive.ui.common.LocalReduceMotion
import net.zodac.dicefive.ui.common.ConfigureOverlayDialogWindow
import net.zodac.dicefive.ui.common.ShrinkThenWrapText
import net.zodac.dicefive.ui.common.grouped

/**
 * How long a banner sits at full opacity before it starts to go - with the fades either side, 5s on
 * screen in all.
 */
private const val HOLD_MILLIS = 4_000L

/** Deliberately unhurried: the end of a game pops several, and a snap-out would read as a glitch. */
private const val FADE_OUT_MILLIS = 820

private const val FADE_IN_MILLIS = 180

/** A swipe leaves quickly - the player has said they're done with it. */
private const val SWIPE_OUT_MILLIS = 180

/** How long a progress banner's count and bar take to climb from the old value to the new one. */
private const val PROGRESS_COUNT_MILLIS = 700

/** How long the pause/play glyph stays fully visible after a tap before it fades. */
private const val HOLD_INDICATOR_MILLIS = 1_000L
private const val HOLD_INDICATOR_FADE_MILLIS = 250

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
 * longer fills the screen. Only the front banner is interactive (swipe to dismiss); the ones
 * peeking out behind it are inert until it clears, so a swipe can never accidentally land on the
 * wrong one underneath. An unlock always takes the front position over a progress nudge, regardless
 * of which arrived first - see [displayOrder] - but otherwise clearing the front always promotes
 * whichever banner was peeking right behind it, never a fresher arrival that only just got admitted
 * off the backlog: see the `add(0, ...)` in the collector below, and [displayOrder]'s comment, for
 * why a brand new banner has to join the *back* of the stack rather than the front, or it would cut
 * the queue the instant a slot freed up for it. Each banner leaves on its own: a hold, then a slow
 * fade. A horizontal swipe in either direction doesn't wait for that.
 *
 * The stack renders in its own [Dialog] window, not as part of [content] - an achievement can fire
 * while a dialog (Settings' About page, a rules dialog, a confirmation) is already on screen, and a
 * new window is always drawn above whatever else was already showing when it appeared, so this
 * keeps the banner from ending up stuck behind one. A `Dialog` was chosen over a `Popup` for this
 * because a `Popup`'s window is attached as a panel of its parent (here, the main content's own
 * window) and stacks relative to *that*, not to other independent top-level windows like another
 * already-open `Dialog` - so it could still end up under one, however recently it was created.
 * [ConfigureOverlayDialogWindow] then turns that dialog window into a non-modal overlay - no dim,
 * and no swallowing touches/back-presses outside its own content - so it doesn't behave like a
 * real dialog itself.
 *
 * Long-pressing the front banner asks [onAchievementSelected] to take the player to that
 * achievement on the Achievements screen - or, for a styles banner, [onStylesSelected] to open the
 * Styles screen (see `AchievementScrollRequests` and `StyleScrollRequests`, which is how the
 * request actually reaches those screens - this host has no reference to it, only to the
 * `NavHostController` its caller wires [onAchievementSelected] up to). Mid-game, with the
 * "confirm before leaving" setting on, and only while the game's own screen is in front
 * ([isOnGameScreen] - a saved game elsewhere, or the Achievements page itself, has nothing being
 * left), that's gated behind the shared leave-game confirmation first (`LeaveGameConfirmation`),
 * during which every banner's hold countdown is paused (see [paused] on [BannerSlot]) so the stack
 * doesn't quietly clear itself out from under the player while they're deciding.
 */
@Composable
fun AchievementBannerHost(
    modifier: Modifier = Modifier,
    isOnGameScreen: () -> Boolean,
    onAchievementSelected: (Achievement) -> Unit,
    onStylesSelected: (List<UnlockedStyle>) -> Unit,
    content: @Composable () -> Unit,
) {
    val banners = remember { mutableStateListOf<BannerItem>() }

    val container = LocalAppContainer.current
    val hasInProgressGame by container.inProgressGameRepository.hasInProgressGame.collectAsStateWithLifecycle(initialValue = false)
    val confirmBeforeLeavingGame by container.settingsRepository.confirmBeforeLeavingGame.collectAsStateWithLifecycle(initialValue = true)
    val leaveConfirmation = LocalLeaveGameConfirmation.current

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // Under reduced motion a burst isn't dealt out one at a time: every banner that fits is there at once.
    val reduceMotion by rememberUpdatedState(LocalReduceMotion.current)
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
            if (!reduceMotion) lifecycle.delayWhileResumed(STAGGER_MILLIS)
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
            // Not fillMaxSize: the window wraps this height, and a full-screen window would swallow every touch
            // meant for the screen beneath - see ConfigureOverlayDialogWindow.
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.BottomCenter,
            ) {
                // Display order only, not the underlying list (removal below still targets `banners`
                // directly) - a real unlock (an achievement, or styles) always sits in front of a progress nudge, wherever in the
                // arrival order it actually landed. sortedBy is stable, so within each of the two
                // groups, `banners`' own order - oldest-still-queued first, since new arrivals are
                // inserted at the front of it, not appended (see the collector above) - is preserved.
                // Front is always the *last* element of this list, so within a type group it's always
                // the one that's been waiting longest, never one that only just joined the back.
                val displayOrder = banners.sortedBy { it.event !is AchievementEvent.Progressed }
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
                                    paused = leaveConfirmation.isShowing,
                                    onDismissed = { banners.remove(item) },
                                    onLongPress = {
                                        val open: () -> Unit = when (val event = item.event) {
                                            is AchievementEvent.Unlocked -> ({ onAchievementSelected(event.achievement) })
                                            is AchievementEvent.Progressed -> ({ onAchievementSelected(event.achievement) })
                                            is AchievementEvent.StylesUnlocked -> ({ onStylesSelected(event.styles) })
                                        }
                                        if (isOnGameScreen() && hasInProgressGame && confirmBeforeLeavingGame) {
                                            leaveConfirmation.request(open)
                                        } else {
                                            open()
                                        }
                                    },
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
 * ticking down together and clearing within moments of each other. It's also held off while a
 * finger is on the banner, restarting in full once it lifts. [paused] holds it off further
 * on top of that, while [AchievementBannerHost]'s leave-game confirmation is up - a banner
 * shouldn't be able to quietly time out and clear itself while the player's still deciding whether
 * to leave the game the long press that raised that confirmation started from.
 *
 * A single gesture recognizer handles both a horizontal swipe (dismiss) and a long press (jump to
 * this achievement on the Achievements screen) - they have to live in the same `pointerInput`
 * block rather than two separate ones, since both start from the same down event and only diverge
 * once the finger either moves past touch slop (a swipe) or the long-press timeout elapses first
 * (neither moved). A long press followed by drag is treated as a swipe, same as anywhere else in
 * Android - the timeout is cancelled the moment real movement is seen.
 */
@Composable
private fun BannerSlot(
    item: BannerItem,
    interactive: Boolean,
    paused: Boolean,
    onDismissed: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // Under reduced motion every fade and slide here is a snap: a banner is simply there, then simply gone. A drag
    // still follows the finger - that's the player moving it, not an animation.
    val reduceMotion = LocalReduceMotion.current
    // Already at full strength under reduced motion, so it's there on its very first frame, not one later.
    val alpha = remember { Animatable(if (reduceMotion) 1f else 0f) }
    val offsetX = remember { Animatable(0f) }
    var swipedAway by remember { mutableStateOf(false) }
    // A finger on the banner - mid-swipe, or just holding it there to keep reading - holds its countdown off.
    var touched by remember { mutableStateOf(false) }
    // A tap pauses the countdown until the next tap; see the class doc. holdRemainingMillis is what's
    // left of the hold, so a resume carries on from where the pause landed instead of starting over.
    var held by remember { mutableStateOf(false) }
    var holdRemainingMillis by remember { mutableLongStateOf(HOLD_MILLIS) }
    val indicatorAlpha = remember { Animatable(0f) }
    var indicatorShowsPause by remember { mutableStateOf(true) }
    var indicatorTick by remember { mutableIntStateOf(0) }

    // The pause/play glyph is a child of the banner, so it shares the banner's own alpha: if the
    // banner is fading out (under a second left when resumed) the glyph goes with it, and never
    // outlives it.
    LaunchedEffect(indicatorTick) {
        if (indicatorTick == 0) return@LaunchedEffect
        indicatorAlpha.snapTo(1f)
        lifecycle.delayWhileResumed(HOLD_INDICATOR_MILLIS)
        indicatorAlpha.moveTo(0f, HOLD_INDICATOR_FADE_MILLIS, reduceMotion)
    }

    // Fades in as soon as it's placed in the stack, whether or not it's the front banner yet -
    // every banner in a burst should be visible right away, even the ones peeking out behind the
    // front one that aren't counting down yet.
    LaunchedEffect(Unit) {
        alpha.moveTo(1f, FADE_IN_MILLIS, reduceMotion)
    }

    // Re-runs whenever this banner is promoted to/demoted from the front of the stack, or the
    // leave-game confirmation opens/closes: becoming the front banner is what starts its hold
    // countdown in the first place, and losing that status (shouldn't normally happen, but is
    // handled the same way for safety) or the confirmation opening cancels whatever was left of it
    // (the `return@LaunchedEffect` below). The confirmation closing starts a fresh full-length
    // hold rather than resuming a partial one. A finger on it does the same - pausing it while
    // it's down, and a fresh hold once it lifts - and brings it back to full opacity if it had
    // already started fading, so a slow swipe or a press to keep it can't lose it mid-gesture.
    LaunchedEffect(interactive, swipedAway, paused, touched, held) {
        if (!interactive || swipedAway || paused) return@LaunchedEffect
        if (touched) {
            alpha.moveTo(1f, FADE_IN_MILLIS, reduceMotion)
            return@LaunchedEffect
        }
        if (held) return@LaunchedEffect
        // Not while the app is in the background: a banner shouldn't clear itself unseen.
        val holdStarted = TimeSource.Monotonic.markNow()
        try {
            lifecycle.delayWhileResumed(holdRemainingMillis)
        } finally {
            // Whatever cancelled this (a touch, a pause) leaves the unspent part for a resume.
            holdRemainingMillis = (holdRemainingMillis - holdStarted.elapsedNow().inWholeMilliseconds).coerceAtLeast(0L)
        }
        if (!swipedAway) {
            alpha.moveTo(0f, FADE_OUT_MILLIS, reduceMotion)
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
                if (interactive) {
                    // The tap has to be reachable without the raw gesture below: TalkBack gets it as an action.
                    Modifier.semantics(mergeDescendants = true) {
                        onClick(label = if (held) "Resume countdown" else "Pause countdown") {
                            held = !held
                            indicatorShowsPause = held
                            indicatorTick++
                            true
                        }
                    }
                } else {
                    Modifier
                },
            )
            .then(
                if (!interactive) {
                    Modifier
                } else {
                    Modifier.pointerInput(item.key) {
                        val longPressTimeoutMillis = viewConfiguration.longPressTimeoutMillis
                        val touchSlop = viewConfiguration.touchSlop
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            touched = true
                            var dragging = false
                            var longPressed = false
                            var previousX = down.position.x
                            val longPressJob = scope.launch {
                                lifecycle.delayWhileResumed(longPressTimeoutMillis)
                                if (!dragging) {
                                    longPressed = true
                                    onLongPress()
                                }
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
                                touched = false
                            }

                            if (!dragging && !longPressed) {
                                // A plain tap: pause the countdown, or resume it.
                                held = !held
                                indicatorShowsPause = held
                                indicatorTick++
                            } else {
                                // Swiped or long-pressed: the usual fresh hold.
                                holdRemainingMillis = HOLD_MILLIS
                                held = false
                            }
                            if (dragging) {
                                scope.launch {
                                    if (abs(offsetX.value) > size.width * SWIPE_DISMISS_FRACTION) {
                                        swipedAway = true
                                        val target = size.width.toFloat() * if (offsetX.value > 0) 1 else -1
                                        launch { alpha.moveTo(0f, SWIPE_OUT_MILLIS, reduceMotion) }
                                        offsetX.moveTo(target, SWIPE_OUT_MILLIS, reduceMotion)
                                        onDismissed()
                                    } else {
                                        offsetX.moveTo(0f, SWIPE_OUT_MILLIS, reduceMotion)
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
            is AchievementEvent.StylesUnlocked -> StylesUnlockedBanner(event)
        }
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f),
            contentColor = Color.White,
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).alpha(indicatorAlpha.value),
        ) {
            Icon(
                imageVector = if (indicatorShowsPause) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                // Only announced while it's on screen; the tap itself is the semantics action above.
                contentDescription = if (indicatorAlpha.value > 0f) (if (indicatorShowsPause) "Paused" else "Resumed") else null,
                modifier = Modifier.padding(4.dp).size(20.dp),
            )
        }
    }
}

/**
 * An achievement's title: always ONE line, so every banner is the same height. Set in the brand
 * face ([SoraFontFamily], bold - its only weight), at titleMedium when it fits, otherwise stepped down until it does, but never below [MIN_READABLE_FONT_SIZE]; a title too
 * long even then is ellipsised, not wrapped. `MAX_ACHIEVEMENT_TITLE_LENGTH` keeps every title short
 * enough not to get that far on a normal phone.
 */
@Composable
private fun BannerTitle(title: String, modifier: Modifier = Modifier) {
    ShrinkThenWrapText(
        text = title,
        style = MaterialTheme.typography.titleMedium.copy(fontFamily = SoraFontFamily, fontWeight = FontWeight.Bold),
        wrappedMaxLines = 1,
        modifier = modifier,
    )
}

/** How many lines an unlock banner's description gets, always - a short one reserves the same space as a
 * long one, so every unlock banner is the same size whichever achievement it's for. bodySmall (12sp),
 * never shrunk; a description that needs more is ellipsised. */
private const val BANNER_DESCRIPTION_LINES = 2

/**
 * An achievement's description under its title: bodySmall, exactly [BANNER_DESCRIPTION_LINES] lines
 * tall whatever its length.
 */
@Composable
private fun BannerDescription(description: String, modifier: Modifier = Modifier) {
    Text(
        text = description,
        style = MaterialTheme.typography.bodySmall,
        minLines = BANNER_DESCRIPTION_LINES,
        maxLines = BANNER_DESCRIPTION_LINES,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/**
 * A banner as one node a screen reader announces when it appears, without being asked - a polite
 * live region - saying [announcement] in place of its drawn text. Otherwise a banner comes and
 * goes, a few seconds later, without a TalkBack user ever knowing it was there.
 */
private fun Modifier.announced(announcement: String): Modifier = clearAndSetSemantics {
    contentDescription = announcement
    liveRegion = LiveRegionMode.Polite
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
        modifier = Modifier.fillMaxWidth().announced(
            buildString {
                append("Achievement unlocked: ${achievement.title}. ${achievement.description}")
                if (achievement.unlocksStyle) append(". Unlocks a style")
            },
        ),
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
            Box(modifier = Modifier.size(40.dp)) {
                Box(
                    modifier = Modifier.fillMaxSize().border(width = 1.dp, color = defaultIconTint),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = achievement.icon,
                        contentDescription = null,
                        tint = achievement.iconTintOrUnspecified(defaultIconTint),
                        modifier = Modifier.size(22.dp),
                    )
                }
                if (achievement.unlocksStyle) StyleRewardStar(tint = defaultIconTint)
            }
            Column(modifier = Modifier.weight(1f)) {
                BannerTitle(achievement.title)
                BannerDescription(achievement.description)
            }
        }
    }
}

/**
 * Earned enough achievements to unlock one or more styles by count - one banner however many styles,
 * from however many categories, a single update unlocked. Unmistakably not an achievement: the
 * tertiary container rather than the primary one, and the style star (the badge an achievement that
 * unlocks a style wears - see [StyleRewardStar]) is the icon itself, in a circle rather than the
 * achievement's square. Same footprint (padding, 40dp icon, a one-line title and two-line
 * description) as [UnlockedBanner], so the stack doesn't change height when the two mix.
 */
@Composable
private fun StylesUnlockedBanner(event: AchievementEvent.StylesUnlocked) {
    Surface(
        modifier = Modifier.fillMaxWidth().announced(stylesUnlockedAnnouncement(event)),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        border = BorderStroke(1.dp, BANNER_BORDER_COLOR),
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier.size(40.dp).border(width = 1.dp, color = LocalContentColor.current, shape = CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(24.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                BannerTitle(stylesUnlockedTitle(event))
                BannerDescription(stylesUnlockedDescription(event))
            }
        }
    }
}

/** Animates to [target] over [millis], or goes straight there under [reduceMotion]. */
private suspend fun Animatable<Float, AnimationVector1D>.moveTo(target: Float, millis: Int, reduceMotion: Boolean) {
    if (reduceMotion) snapTo(target) else animateTo(target, tween(millis))
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
    // Under reduced motion it doesn't climb: the new count is simply there once this banner is at the front.
    val reduceMotion = LocalReduceMotion.current
    LaunchedEffect(interactive) {
        if (!interactive) return@LaunchedEffect
        if (reduceMotion) {
            animatedProgress.snapTo(current.toFloat())
        } else {
            animatedProgress.animateTo(current.toFloat(), tween(PROGRESS_COUNT_MILLIS))
        }
    }
    val displayedValue = animatedProgress.value.roundToInt()

    Surface(
        // The final count, not the climbing one - announcing every step of the climb would be noise.
        modifier = Modifier.fillMaxWidth().announced("${achievement.title}: ${current.grouped()} of ${achievement.target.grouped()}"),
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
