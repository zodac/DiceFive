package net.zodac.dicefive.ui.about

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.zodac.dicefive.BuildConfig
import net.zodac.dicefive.ui.common.AppLogo
import net.zodac.dicefive.ui.common.ScreenScaffold

private const val GITHUB_URL = "https://github.com/zodac/DiceFive"

@Composable
fun AboutScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current

    ScreenScaffold(title = "About", onBack = onBack, modifier = modifier, scrollable = true) {
        Spacer(modifier = Modifier.weight(0.25f))

        AppLogo(dieSize = 28.dp, titleSize = 40.sp)

        Text(
            text = "Version ${BuildConfig.VERSION_NAME}",
            modifier = Modifier.padding(top = 20.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(
            text = "A local-play dice game for one to four players, humans or AI.",
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        OutlinedButton(
            onClick = { uriHandler.openUri(GITHUB_URL) },
            modifier = Modifier.padding(top = 24.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                modifier = Modifier.padding(end = 8.dp),
            )
            Text("View on GitHub")
        }

        Spacer(modifier = Modifier.weight(0.45f))
    }
}
