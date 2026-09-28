package net.zodac.dicefive.ui.common

import androidx.compose.runtime.Composable

/**
 * Called from inside a [androidx.compose.ui.window.Dialog]'s content to turn its window into a
 * transparent, non-modal overlay: no dim behind it, and a touch or back-press outside its own
 * content passes straight through to whatever's beneath, rather than being swallowed the way a
 * normal (modal) dialog window swallows it. The window still sizes itself to its content (see the
 * caller's `usePlatformDefaultWidth = false`), so "outside its own content" is almost everywhere -
 * only the content actually drawn intercepts anything.
 *
 * Used for the achievement banner stack, which needs to sit in its own always-on-top window - a
 * window is always drawn above whatever else was already showing when it's created, which a plain
 * composable layered within the main content can't get - without behaving like a dialog itself.
 */
@Composable
internal expect fun ConfigureOverlayDialogWindow()
