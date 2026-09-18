package net.zodac.dicefive.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = DiceFiveGreen,
    secondary = DiceFiveGreenLight,
    background = DiceFiveIvory,
    surface = DiceFiveIvory,
)

private val DarkColors = darkColorScheme(
    primary = DiceFiveGreenLight,
    secondary = DiceFiveGreen,
    background = DiceFiveCharcoal,
    surface = DiceFiveCharcoal,
)

@Composable
fun DiceFiveTheme(
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
