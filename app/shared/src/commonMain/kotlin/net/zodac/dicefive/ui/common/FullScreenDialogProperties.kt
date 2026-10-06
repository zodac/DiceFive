package net.zodac.dicefive.ui.common

import androidx.compose.ui.window.DialogProperties

/**
 * The properties of a [androidx.compose.ui.window.Dialog] that holds a whole page (the mode and modifier
 * pickers): full width rather than the platform's dialog width. On Android its window also draws under the
 * system bars, so the page's own [ScreenScaffold] places itself against the insets as every other page does
 * (`decorFitsSystemWindows`, a parameter only Android's [DialogProperties] has).
 */
internal expect fun fullScreenDialogProperties(): DialogProperties
