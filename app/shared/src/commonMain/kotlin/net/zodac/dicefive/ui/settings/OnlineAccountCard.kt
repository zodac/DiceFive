package net.zodac.dicefive.ui.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import net.zodac.dicefive.model.sanitizePlayerName
import net.zodac.dicefive.online.GameServices
import net.zodac.dicefive.online.GameServicesState
import net.zodac.dicefive.online.SyncProgress
import net.zodac.dicefive.online.SyncStep
import net.zodac.dicefive.resources.Res
import net.zodac.dicefive.resources.common_clauses_join
import net.zodac.dicefive.resources.online_last_saved
import net.zodac.dicefive.resources.online_sign_in
import net.zodac.dicefive.resources.online_sign_in_failed
import net.zodac.dicefive.resources.online_sign_in_failed_cd
import net.zodac.dicefive.resources.online_signed_in_cd
import net.zodac.dicefive.resources.online_signed_in_saved_cd
import net.zodac.dicefive.resources.online_signed_in_syncing_cd
import net.zodac.dicefive.resources.online_signed_out
import net.zodac.dicefive.resources.online_signing_in
import net.zodac.dicefive.resources.online_sync_progress
import net.zodac.dicefive.resources.online_sync_step_achievements
import net.zodac.dicefive.resources.online_sync_step_downloading
import net.zodac.dicefive.resources.online_sync_step_game
import net.zodac.dicefive.resources.online_sync_step_leaderboards
import net.zodac.dicefive.resources.online_sync_step_scores
import net.zodac.dicefive.resources.online_sync_step_settings
import net.zodac.dicefive.resources.online_sync_step_uploading
import net.zodac.dicefive.resources.online_unreachable
import net.zodac.dicefive.resources.online_unreachable_cd
import net.zodac.dicefive.resources.online_sign_out
import net.zodac.dicefive.ui.common.LocalReduceMotion
import net.zodac.dicefive.ui.common.stringResource
import net.zodac.dicefive.ui.common.timestamp
import org.jetbrains.compose.resources.StringResource

/**
 * The Settings card for the platform's online game service, titled with the service's own name
 * ([GameServices.serviceName]) - the shared UI never writes "Google". Nothing is drawn while the service is
 * [GameServicesState.Unavailable], so a device or build with none looks exactly as it did.
 *
 * The account's name appears here and nowhere else - on its own, in gold, a size up from the lines under it: it is
 * ellipsised rather than capped, never fills or renames a player's seat, and TalkBack hears it in full, as "signed in
 * as" the name ([online_signed_in_cd]).
 *
 * Signed in, [lastSavedEpochMillis] - when the account's cloud save last matched this device - shows under the account
 * ("Last saved ..."), once there is one, and a quiet line while the last sync couldn't reach the service
 * ([unreachable]), which is spoken last. While a sync is under way ([syncProgress]), the step it is on and how far
 * through ("Merging scores (3/7)") show under the header at the card's full width, after a spinner (a still sync icon
 * under reduced motion) and above a progress bar; the unreachable line waits for its result.
 *
 * Signed out after a sign-in that didn't work, the service's reason ([GameServicesState.SignedOut.failure]) shows in
 * red under "Not signed in" ("Couldn't sign in: ..."), untranslated so a player can report it, and is spoken last. It
 * goes once Sign in is tapped again.
 *
 * Sign out is offered only once the sign-in's sync has finished ([signInSynced]): until then, and while signing in,
 * Sign in stays where it was, greyed and disabled, so the card doesn't look finished while it isn't.
 */
@Composable
fun OnlineAccountCard(
    services: GameServices,
    modifier: Modifier = Modifier,
    lastSavedEpochMillis: Long? = null,
    syncProgress: SyncProgress? = null,
    unreachable: Boolean = false,
    signInSynced: Boolean = true,
) {
    val state = services.state.collectAsStateWithLifecycle().value
    if (state is GameServicesState.Unavailable) return
    val scope = rememberCoroutineScope()
    val name = services.serviceName
    val accountName = (state as? GameServicesState.SignedIn)?.account?.displayName?.let(::sanitizePlayerName)
    // Signed in, the name stands alone in place of this.
    val status = when (state) {
        GameServicesState.SigningIn -> stringResource(Res.string.online_signing_in)
        else -> stringResource(Res.string.online_signed_out)
    }
    val lastSaved = lastSavedEpochMillis?.takeIf { accountName != null }?.let { timestamp(it) }
    val sync = syncProgress?.takeIf { accountName != null }
    val syncStep = sync?.let { stringResource(it.step.label) }
    val showUnreachable = unreachable && accountName != null && sync == null
    val failure = (state as? GameServicesState.SignedOut)?.failure
    // The full name is spoken even where the screen cuts it short.
    val account = when {
        accountName == null -> "$name, $status"
        sync != null -> stringResource(Res.string.online_signed_in_syncing_cd, name, accountName, syncStep.orEmpty(), sync.number, sync.total)
        lastSaved == null -> stringResource(Res.string.online_signed_in_cd, name, accountName)
        else -> stringResource(Res.string.online_signed_in_saved_cd, name, accountName, lastSaved)
    }
    val spoken = when {
        showUnreachable -> stringResource(Res.string.common_clauses_join, account, stringResource(Res.string.online_unreachable_cd, name))
        failure != null -> stringResource(Res.string.common_clauses_join, account, stringResource(Res.string.online_sign_in_failed_cd, failure))
        else -> account
    }

    Card(modifier = modifier.fillMaxWidth()) {
        // Sign in or out sits on the header's row, outside its cleared semantics, so TalkBack still reaches it.
        Row(verticalAlignment = Alignment.CenterVertically) {
            ListItem(
                leadingContent = {
                    val avatar = (state as? GameServicesState.SignedIn)?.account?.avatar
                    if (avatar != null) {
                        Image(bitmap = avatar, contentDescription = null, modifier = Modifier.size(24.dp).clip(CircleShape))
                    } else {
                        Icon(imageVector = Icons.Filled.SportsEsports, contentDescription = null)
                    }
                },
                headlineContent = { Text(name, style = MaterialTheme.typography.bodyLarge) },
                supportingContent = {
                    Column {
                        if (accountName != null) {
                            // The gamer name alone, in the brand's gold, a size up from the lines under it.
                            val nameStyle = MaterialTheme.typography.bodyMedium
                            Text(
                                accountName,
                                style = nameStyle.copy(fontSize = (nameStyle.fontSize.value + 1).sp),
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        } else {
                            Text(status, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        if (lastSaved != null) Text(stringResource(Res.string.online_last_saved, lastSaved))
                        if (showUnreachable) Text(stringResource(Res.string.online_unreachable, name))
                        if (failure != null) Text(stringResource(Res.string.online_sign_in_failed, failure), color = MaterialTheme.colorScheme.error)
                    }
                },
                modifier = Modifier.weight(1f).clearAndSetSemantics {
                    heading()
                    contentDescription = spoken
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            if (state is GameServicesState.SignedIn && signInSynced) {
                TextButton(onClick = { scope.launch { services.signOut() } }, modifier = Modifier.padding(end = 8.dp)) {
                    Text(stringResource(Res.string.online_sign_out))
                }
            } else {
                TextButton(
                    onClick = { scope.launch { services.signIn() } },
                    enabled = state is GameServicesState.SignedOut,
                    modifier = Modifier.padding(end = 8.dp),
                ) {
                    Text(stringResource(Res.string.online_sign_in))
                }
            }
        }
        if (sync != null) {
            SyncingLines(
                stringResource(Res.string.online_sync_progress, syncStep.orEmpty(), sync.number, sync.total),
                sync,
                // Under the header's text, past its icon; the heading already says all of it, so TalkBack skips it.
                Modifier.padding(start = TEXT_START, end = 16.dp, bottom = 16.dp).semantics { hideFromAccessibility() },
            )
        }
    }
}

/** Where a [ListItem]'s text starts, past its leading icon: 16dp padding, the 24dp icon, then 16dp. */
private val TEXT_START = 56.dp

/**
 * [text] after a spinner the height of its first line's letters (a still sync icon under reduced motion, as nothing may
 * spin), above a bar of how far through [progress] is, which glides from step to step ([SYNC_BAR_MILLIS]) - and
 * snaps under reduced motion.
 */
@Composable
private fun SyncingLines(text: String, progress: SyncProgress, modifier: Modifier = Modifier) {
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
                Icon(imageVector = Icons.Filled.Sync, contentDescription = null, modifier = iconModifier, tint = colour)
            } else {
                CircularProgressIndicator(modifier = iconModifier, color = colour, strokeWidth = 2.dp)
            }
            Text(text, style = style, color = colour)
        }
        // The step just started counts as half done, so the bar is never empty and never full while a sync runs.
        val target = (progress.number - 0.5f) / progress.total
        val reduceMotion = LocalReduceMotion.current
        val shown = remember { Animatable(0f) }
        LaunchedEffect(target, reduceMotion) {
            if (reduceMotion) shown.snapTo(target) else shown.animateTo(target, tween(SYNC_BAR_MILLIS, easing = FastOutSlowInEasing))
        }
        LinearProgressIndicator(
            progress = { shown.value },
            modifier = Modifier.fillMaxWidth(),
            drawStopIndicator = {},
        )
    }
}

/** How long the sync bar takes to glide to a step: the merges come in a burst, so it carries on through them. */
private const val SYNC_BAR_MILLIS = 400

/** What the card calls each step. */
private val SyncStep.label: StringResource
    get() = when (this) {
        SyncStep.DOWNLOADING -> Res.string.online_sync_step_downloading
        SyncStep.ACHIEVEMENTS -> Res.string.online_sync_step_achievements
        SyncStep.SCORES -> Res.string.online_sync_step_scores
        SyncStep.SETTINGS -> Res.string.online_sync_step_settings
        SyncStep.GAME -> Res.string.online_sync_step_game
        SyncStep.UPLOADING -> Res.string.online_sync_step_uploading
        SyncStep.LEADERBOARDS -> Res.string.online_sync_step_leaderboards
    }
