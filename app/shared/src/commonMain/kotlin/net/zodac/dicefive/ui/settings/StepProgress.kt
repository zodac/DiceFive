package net.zodac.dicefive.ui.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import net.zodac.dicefive.online.MergeStep
import net.zodac.dicefive.resources.Res
import net.zodac.dicefive.resources.online_sync_progress
import net.zodac.dicefive.resources.online_sync_step_achievements
import net.zodac.dicefive.resources.online_sync_step_game
import net.zodac.dicefive.resources.online_sync_step_scores
import net.zodac.dicefive.resources.online_sync_step_settings
import net.zodac.dicefive.ui.common.LocalReduceMotion
import net.zodac.dicefive.ui.common.stringResource
import net.zodac.dicefive.ui.icons.Symbols
import org.jetbrains.compose.resources.StringResource

/** Where a [androidx.compose.material3.ListItem]'s text starts, past its leading icon: 16dp padding, the 24dp icon, then 16dp. */
internal val TEXT_START = 56.dp

/**
 * A job's progress on a Settings card - the online card's sync and the progress files card's import - so both look,
 * move and read the same: [step] and how far through ("Merging scores (5/7)", [online_sync_progress]) after a spinner
 * the height of its first line's letters (a still sync icon under reduced motion, as nothing may spin), above a bar
 * on its [number]th step of [total] (from 1), which glides from step to step ([STEP_BAR_MILLIS]) and snaps under
 * reduced motion. A merge's step is named by [MergeStep.label], whichever job it's in.
 *
 * Drawn only: what TalkBack hears of it is each card's own, as each speaks for itself.
 */
@Composable
internal fun StepProgress(step: String, number: Int, total: Int, modifier: Modifier = Modifier) {
    val style = MaterialTheme.typography.bodyMedium
    val colour = MaterialTheme.colorScheme.onSurfaceVariant
    val density = LocalDensity.current
    val size = with(density) { style.fontSize.toDp() }
    // Centred on the first line, however many the text wraps to.
    val lineHeight = with(density) { style.lineHeight.toDp() }
    val iconModifier = Modifier.padding(top = (lineHeight - size) / 2).size(size)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (LocalReduceMotion.current) {
                Icon(imageVector = Symbols.Sync, contentDescription = null, modifier = iconModifier, tint = colour)
            } else {
                CircularProgressIndicator(modifier = iconModifier, color = colour, strokeWidth = 2.dp)
            }
            Text(stringResource(Res.string.online_sync_progress, step, number, total), style = style, color = colour)
        }
        // The step just started counts as half done, so the bar is never empty and never full while a job runs.
        val target = (number - 0.5f) / total
        val reduceMotion = LocalReduceMotion.current
        val shown = remember { Animatable(0f) }
        LaunchedEffect(target, reduceMotion) {
            if (reduceMotion) shown.snapTo(target) else shown.animateTo(target, tween(STEP_BAR_MILLIS, easing = FastOutSlowInEasing))
        }
        LinearProgressIndicator(
            progress = { shown.value },
            modifier = Modifier.fillMaxWidth(),
            drawStopIndicator = {},
        )
    }
}

/** How long the bar takes to glide to a step: merges come in a burst, so it carries on through them. */
private const val STEP_BAR_MILLIS = 400

/** What every merge's progress calls each of its parts: a sync's and an import's alike. */
internal val MergeStep.label: StringResource
    get() = when (this) {
        MergeStep.SETTINGS -> Res.string.online_sync_step_settings
        MergeStep.GAME -> Res.string.online_sync_step_game
        MergeStep.ACHIEVEMENTS -> Res.string.online_sync_step_achievements
        MergeStep.SCORES -> Res.string.online_sync_step_scores
    }
