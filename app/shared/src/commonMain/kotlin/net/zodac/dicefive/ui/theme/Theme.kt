package net.zodac.dicefive.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import net.zodac.dicefive.ui.common.StringsLanguage

private val Colors = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    inversePrimary = InversePrimary,
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    tertiary = Tertiary,
    onTertiary = OnTertiary,
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = OnTertiaryContainer,
    background = Background,
    onBackground = OnBackground,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    surfaceDim = SurfaceDim,
    surfaceBright = SurfaceBright,
    surfaceContainerLowest = SurfaceContainerLowest,
    surfaceContainerLow = SurfaceContainerLow,
    surfaceContainer = SurfaceContainer,
    surfaceContainerHigh = SurfaceContainerHigh,
    surfaceContainerHighest = SurfaceContainerHighest,
    inverseSurface = InverseSurface,
    inverseOnSurface = InverseOnSurface,
    outline = Outline,
    outlineVariant = OutlineVariant,
    error = Error,
    onError = OnError,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer,
)

/**
 * The app theme, on Material 3 (JetBrains material3 1.9.0, which is androidx material3 1.4.0).
 *
 * Four deliberate choices:
 * - **One scheme, dark.** There is no light variant: the app always looks like this, whatever the
 *   system's dark-mode setting, and there is no theme option in Settings.
 * - **`MaterialTheme`, not `MaterialExpressiveTheme`.** androidx 1.4.0 ships the Expressive-era
 *   components, but its `MaterialExpressiveTheme` is `internal`; the public expressive theme entry
 *   point, with `MotionScheme` and `ButtonGroup`, is androidx 1.5.0's. Revisit once a stable
 *   JetBrains material3 is built on it and that migration is worth doing.
 * - **No dynamic colour.** M3 recommends deriving the palette from the user's wallpaper, but the
 *   navy-and-gold table is the game's identity and shouldn't change per device. Swapping this for
 *   `dynamicDarkColorScheme(context)` on API 31+ is a two-line change if that's ever wanted.
 * - **No typography or shape overrides.** The M3 type scale and shape defaults come with the
 *   theme; overriding them with hand-written styles is how an app quietly loses the system's
 *   sizing, tracking and optical corrections. Per-use deviations belong at the call site.
 *
 * It also sets the layout direction and plural rules to the strings' language ([StringsLanguage]), so that every
 * root - the app, previews and tests - lays out the way the text reads.
 */
@Composable
fun DiceFiveTheme(content: @Composable () -> Unit) {
    StringsLanguage { MaterialTheme(colorScheme = Colors, content = content) }
}
