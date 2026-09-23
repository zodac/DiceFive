package net.zodac.dicefive.ui.achievements

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.zodac.dicefive.data.achievements.AchievementEvent
import net.zodac.dicefive.data.achievements.AchievementEvents
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.ui.common.CONTENT_MAX_WIDTH

/** How long a banner sits at full opacity before it starts to go. */
private const val HOLD_MILLIS = 1_500L

/** Deliberately unhurried: the end of a game pops several, and a snap-out would read as a glitch. */
private const val FADE_OUT_MILLIS = 900

private const val FADE_IN_MILLIS = 180

/** A swipe leaves quickly - the player has said they're done with it. */
private const val SWIPE_OUT_MILLIS = 180

/** The gap between two banners appearing, so a burst arrives as a stack being dealt, not a wall. */
private const val STAGGER_MILLIS = 300L

/**
 * At most this many on screen at once. A finished game can unlock a dozen, and the rest wait their
 * turn rather than being dropped - see the admission wait in [AchievementBannerHost].
 */
private const val MAX_VISIBLE_BANNERS = 4

/** Banners are confined to the bottom half of the screen, clear of the board and the scorecard. */
private const val BOTTOM_HALF = 0.5f

/** How far across itself a banner must be dragged to count as "get rid of this". */
private const val SWIPE_DISMISS_FRACTION = 0.25f

private data class BannerItem(val key: Long, val event: AchievementEvent)

/**
 * Wraps the whole app so achievement banners can outlive the screen that raised them - the burst
 * at the end of a game starts on the board and carries on over the results screen.
 *
 * Banners stack upward from the bottom edge, newest nearest the thumb, and each one leaves on its
 * own: a hold, then a slow fade. A horizontal swipe in either direction clears one early.
 */
@Composable
fun AchievementBannerHost(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val banners = remember { mutableStateListOf<BannerItem>() }

    LaunchedEffect(Unit) {
        var nextKey = 0L
        AchievementEvents.events.collect { event ->
            // Wait for a free slot rather than evicting a banner that's still being read. The
            // event flow buffers the backlog, and suspending here applies the back-pressure.
            snapshotFlow { banners.size }.first { it < MAX_VISIBLE_BANNERS }
            banners += BannerItem(key = nextKey++, event = event)
            delay(STAGGER_MILLIS)
        }
    }

    Box(modifier = modifier) {
        content()

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(BOTTOM_HALF)
                .clipToBounds()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            banners.forEach { item ->
                key(item.key) {
                    BannerSlot(item = item, onDismissed = { banners.remove(item) })
                }
            }
        }
    }
}

/**
 * One banner's lifetime and gestures. The slot owns the animation; [onDismissed] only removes it
 * from the list, and is safe to call twice if a swipe lands during the closing fade.
 */
@Composable
private fun BannerSlot(item: BannerItem, onDismissed: () -> Unit) {
    val scope = rememberCoroutineScope()
    val alpha = remember { Animatable(0f) }
    val offsetX = remember { Animatable(0f) }
    var swipedAway by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        alpha.animateTo(1f, tween(FADE_IN_MILLIS))
        delay(HOLD_MILLIS)
        if (!swipedAway) {
            alpha.animateTo(0f, tween(FADE_OUT_MILLIS))
            onDismissed()
        }
    }

    Box(
        modifier = Modifier
            .widthIn(max = CONTENT_MAX_WIDTH)
            .fillMaxWidth()
            .offset { IntOffset(offsetX.value.roundToInt(), 0) }
            .alpha(alpha.value)
            .pointerInput(item.key) {
                detectHorizontalDragGestures(
                    onDragEnd = {
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
                    },
                ) { change, dragAmount ->
                    change.consume()
                    scope.launch { offsetX.snapTo(offsetX.value + dragAmount) }
                }
            },
    ) {
        when (val event = item.event) {
            is AchievementEvent.Unlocked -> UnlockedBanner(event.achievement)
            is AchievementEvent.Progressed -> ProgressBanner(event.achievement, event.current)
        }
    }
}

/** The full-fat banner: something was actually earned. */
@Composable
private fun UnlockedBanner(achievement: Achievement) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(imageVector = Icons.Filled.EmojiEvents, contentDescription = null, modifier = Modifier.size(28.dp))
            Column {
                Text(text = "Achievement unlocked", style = MaterialTheme.typography.labelSmall)
                Text(
                    text = achievement.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * The quieter one: not earned yet, but closer. Lower emphasis and a shorter body than an unlock,
 * so a run of progress nudges can't be mistaken for the real thing.
 */
@Composable
private fun ProgressBanner(achievement: Achievement, current: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shadowElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(imageVector = Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null, modifier = Modifier.size(18.dp))
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = achievement.title,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Text(
                        text = "${current.grouped()} of ${achievement.target.grouped()}",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                LinearProgressIndicator(
                    progress = { current.toFloat() / achievement.target },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
        }
    }
}
