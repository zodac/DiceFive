package net.zodac.yahtzee.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = YahtzeeGreen,
    secondary = YahtzeeGreenLight,
    background = YahtzeeIvory,
    surface = YahtzeeIvory,
)

private val DarkColors = darkColorScheme(
    primary = YahtzeeGreenLight,
    secondary = YahtzeeGreen,
    background = YahtzeeCharcoal,
    surface = YahtzeeCharcoal,
)

@Composable
fun YahtzeeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
