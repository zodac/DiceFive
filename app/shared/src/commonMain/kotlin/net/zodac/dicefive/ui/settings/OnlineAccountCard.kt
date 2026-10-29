package net.zodac.dicefive.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import net.zodac.dicefive.model.sanitizePlayerName
import net.zodac.dicefive.online.GameServices
import net.zodac.dicefive.online.GameServicesState
import net.zodac.dicefive.online.MergeStep
import net.zodac.dicefive.online.SyncProgress
import net.zodac.dicefive.online.SyncStep
import net.zodac.dicefive.resources.Res
import net.zodac.dicefive.resources.common_clauses_join
import net.zodac.dicefive.resources.online_last_saved
import net.zodac.dicefive.resources.online_sign_in
import net.zodac.dicefive.resources.online_sign_in_failed
import net.zodac.dicefive.resources.online_sign_in_failed_cd
import net.zodac.dicefive.resources.online_sign_out
import net.zodac.dicefive.resources.online_signed_in_cd
import net.zodac.dicefive.resources.online_signed_in_saved_cd
import net.zodac.dicefive.resources.online_signed_in_syncing_cd
import net.zodac.dicefive.resources.online_signed_out
import net.zodac.dicefive.resources.online_signing_in
import net.zodac.dicefive.resources.online_sync_step_downloading
import net.zodac.dicefive.resources.online_sync_step_leaderboards
import net.zodac.dicefive.resources.online_sync_step_uploading
import net.zodac.dicefive.resources.online_unreachable
import net.zodac.dicefive.resources.online_unreachable_cd
import net.zodac.dicefive.ui.common.stringResource
import net.zodac.dicefive.ui.common.timestamp
import net.zodac.dicefive.ui.icons.Symbols
import org.jetbrains.compose.resources.StringResource

/**
 * The Settings card for the platform's online game service, titled with the service's own name
 * ([GameServices.serviceName]) while signed out - the shared UI never writes "Google". Nothing is drawn while the
 * service is [GameServicesState.Unavailable], so a device or build with none looks exactly as it did.
 *
 * Signed in, the account's name is the title in the service's place, and appears nowhere else - in gold, a size up
 * from the lines under it: it is ellipsised rather than capped, never fills or renames a player's seat, and TalkBack
 * hears it in full after the service's name, as "signed in as" the name ([online_signed_in_cd]).
 *
 * Signed in, [lastSavedEpochMillis] - when the account's cloud save last matched this device - shows under the account
 * ("Saved ..."), once there is one, and a quiet line while the last sync couldn't reach the service
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
        // Sign in or out sits on the header's row, outside its cleared semantics, so TalkBack still reaches it. The text
        // runs up to the button, whose own padding is the gap, so "Saved" and its time fit one line on a phone; the
        // button's label ends 16dp from the card's edge, in line with the switches below.
        Row(modifier = Modifier.heightIn(min = 56.dp).padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier.weight(1f).padding(vertical = 8.dp).clearAndSetSemantics {
                    heading()
                    contentDescription = spoken
                },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                val avatar = (state as? GameServicesState.SignedIn)?.account?.avatar
                if (avatar != null) {
                    Image(bitmap = avatar, contentDescription = null, modifier = Modifier.size(24.dp).clip(CircleShape))
                } else {
                    Icon(imageVector = Symbols.SportsEsports, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column {
                    if (accountName != null) {
                        // The gamer name alone, in the brand's gold: the service's name is spoken, not shown.
                        Text(
                            accountName,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else {
                        Text(name, style = MaterialTheme.typography.bodyLarge)
                    }
                    CompositionLocalProvider(
                        LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant,
                        LocalTextStyle provides MaterialTheme.typography.bodyMedium,
                    ) {
                        if (accountName == null) Text(status, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (lastSaved != null) Text(stringResource(Res.string.online_last_saved, lastSaved))
                        if (showUnreachable) Text(stringResource(Res.string.online_unreachable, name))
                        if (failure != null) Text(stringResource(Res.string.online_sign_in_failed, failure), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            if (state is GameServicesState.SignedIn && signInSynced) {
                TextButton(onClick = { scope.launch { services.signOut() } }) {
                    Text(stringResource(Res.string.online_sign_out))
                }
            } else {
                TextButton(onClick = { scope.launch { services.signIn() } }, enabled = state is GameServicesState.SignedOut) {
                    Text(stringResource(Res.string.online_sign_in))
                }
            }
        }
        if (sync != null) {
            StepProgress(
                syncStep.orEmpty(),
                sync.number,
                sync.total,
                // Under the header's text, past its icon; the heading already says all of it, so TalkBack skips it.
                Modifier.padding(start = TEXT_START, end = 16.dp, bottom = 16.dp).semantics { hideFromAccessibility() },
            )
        }
    }
}

/** What the card calls each step: a merge as every merge's progress names it ([MergeStep.label]). */
private val SyncStep.label: StringResource
    get() = when (this) {
        SyncStep.DOWNLOADING -> Res.string.online_sync_step_downloading
        SyncStep.SETTINGS, SyncStep.GAME, SyncStep.ACHIEVEMENTS, SyncStep.SCORES -> checkNotNull(merge).label
        SyncStep.UPLOADING -> Res.string.online_sync_step_uploading
        SyncStep.LEADERBOARDS -> Res.string.online_sync_step_leaderboards
    }
