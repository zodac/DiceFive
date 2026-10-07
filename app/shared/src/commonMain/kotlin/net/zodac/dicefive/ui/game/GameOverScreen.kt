package net.zodac.dicefive.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import net.zodac.dicefive.game.TieBreak
import net.zodac.dicefive.game.TieBreakCriterion
import net.zodac.dicefive.model.GameState
import net.zodac.dicefive.model.PlayerState
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.resources.Res
import net.zodac.dicefive.resources.common_tied_rank
import net.zodac.dicefive.resources.gameover_main_menu
import net.zodac.dicefive.resources.gameover_play_again
import net.zodac.dicefive.resources.gameover_review
import net.zodac.dicefive.resources.gameover_title
import net.zodac.dicefive.resources.gameover_winner
import net.zodac.dicefive.ui.common.BrandBackdrop
import net.zodac.dicefive.ui.common.LocalReduceMotion
import net.zodac.dicefive.ui.common.PageColumn
import net.zodac.dicefive.ui.common.PageTopBar
import net.zodac.dicefive.ui.common.SoraFontFamily
import net.zodac.dicefive.ui.theme.CupRimGold
import net.zodac.dicefive.ui.theme.GoldAccent
import net.zodac.dicefive.ui.theme.GoldAccentDim
import org.jetbrains.compose.resources.stringResource

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
    onReviewScorecards: () -> Unit,
    modifier: Modifier = Modifier,
    soundEnabled: Boolean = true,
) {
    val ranked = TieBreak.rank(state.players)
    // A tie on raw score is now almost always broken by the house rule (see game/TieBreak.kt) -
    // "winners" only stays plural when every criterion in that list also matches.
    val winners = ranked.filter { it.rank == 1 }
    val runnersUp = ranked.filter { it.rank != 1 }
    // Any human seat sharing the win counts - not just the primary player - so a tie between a
    // human and the CPU still gets the celebration. A solo game has nobody to beat, so its lone
    // player "winning" is a given and gets no fanfare.
    val humanWon = state.players.size > 1 && winners.any { it.player.type == PlayerType.HUMAN }

    val soundEffects = rememberSoundEffects()
    soundEffects.enabled = soundEnabled
    // Fires once when this screen is first composed for a finished game, not on every recomposition.
    LaunchedEffect(Unit) {
        if (humanWon) soundEffects.playCelebration()
    }

    BrandBackdrop(modifier = modifier, driftingDice = true) {
        // The fanfare (its own sound setting) still plays; the sparks are what reduced motion drops.
        if (humanWon && !LocalReduceMotion.current) {
            GoldFireworks(durationMillis = CELEBRATION_MILLIS, modifier = Modifier.fillMaxSize())
        }

        // The same app bar as every other page (so "Game Over" sits where their titles do), but no
        // back arrow: the two ways out are the buttons at the end. Fixed above the results, which
        // scroll beneath it like any other page's content.
        Column(modifier = Modifier.fillMaxSize()) {
            PageTopBar(title = stringResource(Res.string.gameover_title), onBack = null)
            PageColumn(
                modifier = Modifier.weight(1f),
                // The app bar has already taken the status bar's inset.
                contentPadding = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom).asPaddingValues(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                for (winner in winners) {
                    WinnerCard(player = winner.player, solo = state.players.size == 1, tieBreakReason = winner.tieBreakReason)
                }

                if (runnersUp.isNotEmpty()) {
                    // "=" only for a true tie (every tie-break criterion also matches, not just raw
                    // score) - a rank shared with more than one runner-up here is exactly that, since
                    // rankPlayers only ever repeats a rank for a true tie.
                    val sharedRanks = runnersUp.groupingBy { it.rank }.eachCount().filterValues { it > 1 }.keys
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            for (runnerUp in runnersUp) {
                                RunnerUpRow(
                                    rank = runnerUp.rank,
                                    isTrueTie = runnerUp.rank in sharedRanks,
                                    player = runnerUp.player,
                                    tieBreakReason = runnerUp.tieBreakReason,
                                )
                            }
                        }
                    }
                }

                // A fixed gap, not one that grows to push these to the bottom of the screen - the
                // achievement banner stack sits over the bottom half (see AchievementBannerHost), so
                // pinning these as a footer put them right where a banner could land on top of them.
                // They now just follow directly after the score list instead.
                Spacer(modifier = Modifier.height(20.dp))

                // A full-width third action rather than squeezing into the row below - "review the
                // board" is a detour on the way to one of the two real exits from this screen, not a
                // third option of equal weight with them.
                OutlinedButton(
                    onClick = onReviewScorecards,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) {
                    Text(text = stringResource(Res.string.gameover_review), style = MaterialTheme.typography.titleMedium)
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = onBackToMenu,
                        modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                    ) {
                        Text(text = stringResource(Res.string.gameover_main_menu), style = MaterialTheme.typography.titleMedium)
                    }
                    Button(
                        onClick = onPlayAgain,
                        modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                    ) {
                        Text(text = stringResource(Res.string.gameover_play_again), style = MaterialTheme.typography.titleMedium)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

/** [solo] keeps the gold card but drops the trophy and "Winner" label - nobody was beaten.
 * [tieBreakReason] is only set when this winner shares their raw score with the very next player
 * down the results, and the house rule (not the official rules) is the only reason they're the one
 * holding the trophy. */
@Composable
private fun WinnerCard(player: PlayerState, solo: Boolean, tieBreakReason: TieBreakCriterion? = null) {
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
            if (!solo) {
                Icon(
                    imageVector = Icons.Filled.EmojiEvents,
                    contentDescription = stringResource(Res.string.gameover_winner),
                    modifier = Modifier.size(44.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (player.type == PlayerType.AI) CpuPlayerIcon(size = 24.dp)
                    Text(
                        text = player.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        // A second line before an ellipsis: a long name beside the trophy and score used to be cut
                        // off; now it wraps.
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!solo) {
                    Text(text = stringResource(Res.string.gameover_winner), style = MaterialTheme.typography.labelLarge)
                }
                if (tieBreakReason != null) {
                    Text(
                        text = stringResource(tieBreakReason.reasonText),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            Text(
                text = player.totalScore.toString(),
                style = MaterialTheme.typography.displaySmall,
                fontFamily = SoraFontFamily,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** [isTrueTie] prefixes the rank with "=" - only when this player shares it with another runner-up
 * because every tie-break criterion also matched between them, not just their raw score.
 * [tieBreakReason] is only set when this player shares their raw score with the very next player
 * down the results, and the house rule is why they're the one ranked above instead. */
@Composable
private fun RunnerUpRow(rank: Int, isTrueTie: Boolean, player: PlayerState, tieBreakReason: TieBreakCriterion? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = if (isTrueTie) stringResource(Res.string.common_tied_rank, rank) else rank.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (player.type == PlayerType.AI) CpuPlayerIcon(size = 18.dp)
                Text(
                    text = player.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (tieBreakReason != null) {
                Text(
                    text = stringResource(tieBreakReason.reasonText),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = player.totalScore.toString(),
            style = MaterialTheme.typography.titleLarge,
            fontFamily = SoraFontFamily,
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
            (i / PARTICLES_PER_BURST.toFloat()) * (2 * PI).toFloat() + random.nextFloat() * 0.3f
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
    val bursts = remember { randomBursts(Random.Default) }
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
