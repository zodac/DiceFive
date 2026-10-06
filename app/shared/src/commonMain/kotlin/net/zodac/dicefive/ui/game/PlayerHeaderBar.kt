package net.zodac.dicefive.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import net.zodac.dicefive.game.Standing
import net.zodac.dicefive.game.label
import net.zodac.dicefive.game.spoken
import net.zodac.dicefive.game.standings
import net.zodac.dicefive.model.PlayerState
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.ui.common.LocalReduceMotion
import net.zodac.dicefive.ui.common.ShrinkThenWrapText
import net.zodac.dicefive.ui.theme.color

/** How long a score takes to count up: most turns' points rise in [SCORE_RISE_MIN_MILLIS], a bigger
 * jump gets [SCORE_RISE_MILLIS_PER_POINT] each, and past [SCORE_RISE_MAX_MILLIS] it just counts faster. */
private const val SCORE_RISE_MIN_MILLIS = 1000
private const val SCORE_RISE_MAX_MILLIS = 2000
private const val SCORE_RISE_MILLIS_PER_POINT = 40

internal fun scoreRiseMillis(pointsGained: Int): Int =
    (pointsGained * SCORE_RISE_MILLIS_PER_POINT).coerceIn(SCORE_RISE_MIN_MILLIS, SCORE_RISE_MAX_MILLIS)

/**
 * The top row of player tabs: name, running total, place in the game so far (see [standings] - none
 * in a solo game, or before anyone has scored a turn), and (for the active player) a colored outline
 * plus a small dot underneath - the only "whose turn is it" indicator, since the scoring grid
 * below always shows just the active player's own card. Tapping a tab shows that player's
 * scorecard read-only in place of the live board (see [PlayerHeaderBar]'s `onPlayerTap`) - the
 * active player's own ring never moves for this, but the tab being viewed gets a dashed outline
 * so it's clear the board on screen isn't the current turn's.
 *
 * Tabs are only tappable when [enabled] - an AI's turn plays out on its own with nobody to ask it
 * to pause, so switching away from the live board mid-turn would just hide it running rather than
 * let anyone actually study another scorecard.
 */
@Composable
fun PlayerHeaderBar(
    players: List<PlayerState>,
    currentPlayerIndex: Int,
    viewedPlayerIndex: Int?,
    enabled: Boolean,
    onPlayerTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Three or four tabs leave roughly a quarter of the screen each, which a name at labelLarge
    // can outgrow even at the setup screen's length cap - so the name (not the score) steps down
    // a size, rather than every full-length name arriving pre-ellipsised.
    val compactNames = players.size > 2
    val places = standings(players)

    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        players.forEachIndexed { index, player ->
            PlayerTab(
                name = player.name,
                cpu = player.type == PlayerType.AI,
                score = player.totalScore,
                color = player.colour.color,
                active = index == currentPlayerIndex,
                viewed = index == viewedPlayerIndex,
                compactName = compactNames,
                standing = places?.get(index),
                showsPlaces = players.size > 1,
                enabled = enabled,
                onClick = { onPlayerTap(index) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PlayerTab(
    name: String,
    cpu: Boolean,
    score: Int,
    color: Color,
    active: Boolean,
    viewed: Boolean,
    compactName: Boolean,
    standing: Standing?,
    showsPlaces: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = modifier
            .padding(horizontal = 3.dp)
            .clip(shape)
            .then(if (active) Modifier.border(1.5.dp, color.copy(alpha = 0.85f), shape) else Modifier)
            .then(if (viewed) Modifier.dashedBorder(1.5.dp, color.copy(alpha = 0.85f), 10.dp) else Modifier)
            .clickable(enabled = enabled, role = Role.Tab, onClickLabel = "View scorecard", onClick = onClick)
            // Selected is the scorecard on view; the border's other meaning - whose turn it is - is said
            // instead, with the player's place (the visible "=2nd" is cleared below: read as "equals").
            .semantics {
                selected = viewed
                val state = listOfNotNull("Current turn".takeIf { active }, standing?.spoken()).joinToString(", ")
                if (state.isNotEmpty()) stateDescription = state
            }
            .padding(vertical = 6.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val nameStyle = if (compactName) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
        ) {
            if (cpu) {
                // Measured first, so the name gets whatever width is left after it.
                CpuPlayerIcon(size = if (compactName) 12.dp else 14.dp, tint = color)
            }
            // Shrinks a little for a name that doesn't fit its tab (a full-length CPU name beside its chip
            // icon, at four players on a narrow phone), but never below MIN_READABLE_FONT_SIZE: past
            // that it wraps to a second line rather than getting smaller - only reached at a large
            // system font, or by a very wide name, since the caps on name length (see
            // GameSetupState.maxPlayerNameLength) are sized to fit one line at that size.
            ShrinkThenWrapText(
                text = name,
                style = nameStyle,
                color = color,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            )
        }
        Text(
            text = risingScore(score).toString(),
            color = color,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.headlineSmall,
        )
        // Whose turn it is (the dot) and the player's place. Its height is kept in any game with
        // places to show, so the tabs don't grow the moment the first score puts someone ahead.
        Row(
            modifier = Modifier.padding(top = 2.dp).then(if (showsPlaces) Modifier.heightIn(min = PLACE_ROW_HEIGHT) else Modifier),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(
                modifier = Modifier
                    .padding(top = if (showsPlaces) 0.dp else 2.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (active) color else Color.Transparent),
            )
            if (standing != null) {
                Text(
                    text = standing.label(),
                    color = color,
                    fontWeight = FontWeight.Medium,
                    style = if (compactName) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    modifier = Modifier.clearAndSetSemantics {},
                )
                // Balances the dot, so the place sits centred under the score.
                Box(modifier = Modifier.size(6.dp))
            }
        }
    }
}

/** Room for a place label under a score - so the tab's height doesn't change when one first appears. */
private val PLACE_ROW_HEIGHT = 16.dp

/** Compose's built-in `Modifier.border` has no dash-pattern option, so the "viewed player" ring
 * draws its own rounded-rect stroke instead of reusing the active player's solid one. */
private fun Modifier.dashedBorder(width: Dp, color: Color, cornerRadius: Dp): Modifier = drawWithContent {
    drawContent()
    val strokeWidthPx = width.toPx()
    val inset = strokeWidthPx / 2
    drawRoundRect(
        color = color,
        topLeft = Offset(inset, inset),
        size = Size(size.width - strokeWidthPx, size.height - strokeWidthPx),
        cornerRadius = CornerRadius(cornerRadius.toPx()),
        style = Stroke(
            width = strokeWidthPx,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
        ),
    )
}

/**
 * [score] as it should read right now: counting up to a new total over [scoreRiseMillis] instead
 * of jumping straight there. A score that lands while the previous one is still rising (a quick
 * solo player can roll and score again inside a second) skips that one to its end first, so each
 * rise starts from the total it was actually built on rather than from wherever the last one had
 * got to. A total that goes down (an undo) just snaps - there's nothing to celebrate.
 */
@Composable
private fun risingScore(score: Int): Int {
    val shown = remember { Animatable(score.toFloat()) }
    val reduceMotion = LocalReduceMotion.current
    // The last total this tab was told about - where an interrupted rise was heading.
    var lastTarget by remember { mutableIntStateOf(score) }
    // Keyed on score, so a new total cancels the rise still running before this starts.
    LaunchedEffect(score) {
        val from = lastTarget
        lastTarget = score
        shown.snapTo(from.toFloat())
        // Reduced motion: the new total appears at once instead of counting up.
        if (score <= from || reduceMotion) {
            shown.snapTo(score.toFloat())
            return@LaunchedEffect
        }
        shown.animateTo(
            targetValue = score.toFloat(),
            animationSpec = tween(durationMillis = scoreRiseMillis(score - from), easing = LinearOutSlowInEasing),
        )
    }
    return shown.value.roundToInt()
}
