package net.zodac.dicefive.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.zodac.dicefive.model.sanitizePlayerName
import net.zodac.dicefive.online.GameServices
import net.zodac.dicefive.online.GameServicesState
import net.zodac.dicefive.resources.Res
import net.zodac.dicefive.resources.online_sign_in
import net.zodac.dicefive.resources.online_signed_in_as
import net.zodac.dicefive.resources.online_signed_in_cd
import net.zodac.dicefive.resources.online_signed_out
import net.zodac.dicefive.resources.online_signing_in
import net.zodac.dicefive.resources.online_sign_out
import net.zodac.dicefive.ui.common.stringResource

/**
 * The Settings card for the platform's online game service, titled with the service's own name
 * ([GameServices.serviceName]) - the shared UI never writes "Google". Nothing is drawn while the service is
 * [GameServicesState.Unavailable], so a device or build with none looks exactly as it did.
 *
 * The account's name appears here and nowhere else: it is ellipsised rather than capped, never fills or renames
 * a player's seat, and TalkBack hears it in full ([online_signed_in_cd]).
 */
@Composable
fun OnlineAccountCard(
    services: GameServices,
    modifier: Modifier = Modifier,
) {
    val state = services.state.collectAsState().value
    if (state is GameServicesState.Unavailable) return
    val scope = rememberCoroutineScope()
    val name = services.serviceName
    val accountName = (state as? GameServicesState.SignedIn)?.account?.displayName?.let(::sanitizePlayerName)
    val status = when (state) {
        is GameServicesState.SignedIn -> stringResource(Res.string.online_signed_in_as, accountName.orEmpty())
        GameServicesState.SigningIn -> stringResource(Res.string.online_signing_in)
        else -> stringResource(Res.string.online_signed_out)
    }
    // The full name is spoken even where the screen cuts it short.
    val spoken = if (accountName != null) stringResource(Res.string.online_signed_in_cd, name, accountName) else "$name, $status"

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
                supportingContent = { Text(status, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                modifier = Modifier.weight(1f).clearAndSetSemantics {
                    heading()
                    contentDescription = spoken
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            when (state) {
                GameServicesState.SignedOut ->
                    TextButton(onClick = { scope.launch { services.signIn() } }, modifier = Modifier.padding(end = 8.dp)) {
                        Text(stringResource(Res.string.online_sign_in))
                    }
                is GameServicesState.SignedIn ->
                    TextButton(onClick = { scope.launch { services.signOut() } }, modifier = Modifier.padding(end = 8.dp)) {
                        Text(stringResource(Res.string.online_sign_out))
                    }
                else -> Unit
            }
        }
    }
}
