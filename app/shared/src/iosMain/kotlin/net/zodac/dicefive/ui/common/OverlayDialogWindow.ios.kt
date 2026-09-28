package net.zodac.dicefive.ui.common

import androidx.compose.runtime.Composable

/**
 * No-op for now: iOS Compose Multiplatform doesn't expose the same per-window dim/touch-modal
 * flags Android's `Window` does, and the iOS app itself hasn't shipped yet (see IOS_SUPPORT.md) -
 * this is left as the platform default dialog presentation until that's built and this can
 * actually be verified there.
 */
@Composable
internal actual fun ConfigureOverlayDialogWindow() {
}
