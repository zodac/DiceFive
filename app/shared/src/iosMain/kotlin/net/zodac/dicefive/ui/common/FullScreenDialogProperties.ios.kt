package net.zodac.dicefive.ui.common

import androidx.compose.ui.window.DialogProperties

// iOS has no decorFitsSystemWindows; its dialog already lays out against the safe area.
internal actual fun fullScreenDialogProperties(): DialogProperties = DialogProperties(usePlatformDefaultWidth = false)
