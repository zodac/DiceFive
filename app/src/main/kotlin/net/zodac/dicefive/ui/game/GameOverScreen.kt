package net.zodac.dicefive.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import net.zodac.dicefive.model.GameState
import net.zodac.dicefive.model.PlayerState
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.ui.common.BrandBackdrop
import net.zodac.dicefive.ui.common.PageColumn
import net.zodac.dicefive.ui.common.SoraFontFamily
import net.zodac.dicefive.ui.theme.CupRimGold
import net.zodac.dicefive.ui.theme.GoldAccent
import net.zodac.dicefive.ui.theme.GoldAccentDim

/** Matches the length of celebration.ogg, so the fireworks burst finishes right as the fanfare does. */
private const val CELEBRATION_MILLIS = 3300

/**
 * The end-of-game results page.
 *
 * A full themed page rather than a few lines appended under the board: the game is over, so the
 * board's felt, dice and scorecard are no longer what the player is looking at. The winner (or
 * winners - a tie on the top score is perfectly possible) gets a raised primary card with a
 * trophy; everyone else is a plain ranked row.
 */
@Composable
fun GameOverScreen(
    state: GameState,
    onBackToMenu: () -> Unit,
    onPlayAgain: () -> Unit,
    modifier: Modifier = Modifier,
    soundEnabled: Boolean = true,
) {
    val ranked = state.players.sortedByDescending { it.totalScore }
    val topScore = ranked.firstOrNull()?.totalScore ?: 0
    // Ties share the top spot: "the first player in the sorted list" would silently crown one of
    // two equal scores, which is the sort of thing that only ever shows up in a real game.
    val winners = ranked.filter { it.totalScore == topScore }
    val runnersUp = ranked.drop(winners.size)
    // Any human seat sharing the win counts - not just the primary player - so a tie between a
    // human and the CPU still gets the celebration.
    val humanWon = winners.any { it.type == PlayerType.HUMAN }

    val soundEffects = rememberSoundEffects()
    soundEffects.enabled = soundEnabled
    // Fires once when this screen is first composed for a finished game, not on every recomposition.
    LaunchedEffect(Unit) {
        if (humanWon) soundEffects.playCelebration()
    }

    BrandBackdrop(modifier = modifier) {
        if (humanWon) {
            GoldFireworks(durationMillis = CELEBRATION_MILLIS, modifier = Modifier.fillMaxSize())
        }

        PageColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Spacer(modifier = Modifier.height(24.dp))

            // Styled like every other page's title (ScreenScaffold's app bar): bold Sora in the
            // brand gold. No "X wins" line under it - the winner card below already says so.
            Text(
                text = "Game Over",
                style = MaterialTheme.typography.titleLarge,
                fontFamily = SoraFontFamily,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )

            for (winner in winners) {
                WinnerCard(player = winner)
            }

            if (runnersUp.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        runnersUp.forEachIndexed { index, player ->
                            // Ranks continue past however many players shared the win.
                            RunnerUpRow(rank = winners.size + index + 1, player = player)
                        }
                    }
                }
            }

            // A fixed gap, not one that grows to push these to the bottom of the screen - the
            // achievement banner stack sits over the bottom half (see AchievementBannerHost), so
            // pinning these as a footer put them right where a banner could land on top of them.
            // They now just follow directly after the score list instead.
            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onBackToMenu,
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                ) {
                    Text(text = "Back to Menu", style = MaterialTheme.typography.titleMedium)
                }
                Button(
                    onClick = onPlayAgain,
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                ) {
                    Text(text = "Play Again", style = MaterialTheme.typography.titleMedium)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun WinnerCard(player: PlayerState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.EmojiEvents,
                contentDescription = "Winner",
                modifier = Modifier.size(44.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = player.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(text = "Winner", style = MaterialTheme.typography.labelLarge)
            }
            Text(
                text = player.totalScore.toString(),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun RunnerUpRow(rank: Int, player: PlayerState) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = rank.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = player.name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = player.totalScore.toString(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** How much of [CELEBRATION_MILLIS] a single burst's particles take to expand and fade. */
private const val BURST_LIFE_FRACTION = 0.4f

/** How late into [CELEBRATION_MILLIS] the last burst is still allowed to kick off - leaves enough
 * runway for [BURST_LIFE_FRACTION] of its own life to play out before the animation ends. */
private const val LAST_BURST_START_FRACTION = 1f - BURST_LIFE_FRACTION

private const val BURST_COUNT = 5
private const val PARTICLES_PER_BURST = 18
private val FIREWORK_GOLDS = listOf(GoldAccent, GoldAccentDim, CupRimGold)

/** One exploding burst of gold sparks: fixed launch point, launch time and per-particle
 * angle/speed, all decided once up front so the shape stays put across recompositions instead of
 * reshuffling mid-animation. */
private class FireworkBurst(
    val startFraction: Float,
    val center: Offset,
    val angles: FloatArray,
    val speeds: FloatArray,
    val colors: List<Color>,
)

private fun randomBursts(random: Random): List<FireworkBurst> = List(BURST_COUNT) { index ->
    FireworkBurst(
        // Spread launches across the first LAST_BURST_START_FRACTION of the animation, with a
        // little jitter so they don't all fire on a perfectly even beat.
        startFraction = (index / BURST_COUNT.toFloat()) * LAST_BURST_START_FRACTION + random.nextFloat() * 0.05f,
        // Kept off the very edges so a burst's outer sparks don't clip the screen bounds.
        center = Offset(x = 0.15f + random.nextFloat() * 0.7f, y = 0.12f + random.nextFloat() * 0.45f),
        angles = FloatArray(PARTICLES_PER_BURST) { i ->
            (i / PARTICLES_PER_BURST.toFloat()) * (2 * Math.PI).toFloat() + random.nextFloat() * 0.3f
        },
        speeds = FloatArray(PARTICLES_PER_BURST) { 0.55f + random.nextFloat() * 0.45f },
        colors = List(PARTICLES_PER_BURST) { FIREWORK_GOLDS[random.nextInt(FIREWORK_GOLDS.size)] },
    )
}

/**
 * A one-shot, all-gold firework display - matches the game's navy-and-gold identity rather than
 * the usual multicoloured display, and only plays once (no looping) since it's tied to a human
 * player's win, not an ambient decoration. [progress] drives every burst's particles from a single
 * animated value rather than each burst running its own clock, so they all stay in lockstep even
 * if this composable recomposes mid-animation.
 */
@Composable
private fun GoldFireworks(durationMillis: Int, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(0f) }
    val bursts = remember { randomBursts(Random(System.nanoTime())) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, animationSpec = tween(durationMillis = durationMillis, easing = LinearEasing))
    }
    Canvas(modifier = modifier) {
        val sparkRadius = size.minDimension * 0.007f
        val maxBurstRadius = size.minDimension * 0.3f
        for (burst in bursts) {
            val localProgress = (progress.value - burst.startFraction) / BURST_LIFE_FRACTION
            if (localProgress < 0f || localProgress >= 1f) continue
            val center = Offset(burst.center.x * size.width, burst.center.y * size.height)
            // Sparks slow down as they fly out (ease-out) and gently fall under a touch of gravity.
            val easedProgress = 1f - (1f - localProgress) * (1f - localProgress)
            val fallOffset = localProgress * localProgress * size.minDimension * 0.08f
            val alpha = 1f - localProgress
            for (i in 0 until PARTICLES_PER_BURST) {
                val radius = maxBurstRadius * burst.speeds[i] * easedProgress
                val x = center.x + cos(burst.angles[i]) * radius
                val y = center.y + sin(burst.angles[i]) * radius + fallOffset
                drawCircle(color = burst.colors[i].copy(alpha = alpha), radius = sparkRadius, center = Offset(x, y))
            }
        }
    }
}
