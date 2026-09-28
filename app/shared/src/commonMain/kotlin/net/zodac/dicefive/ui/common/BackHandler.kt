package net.zodac.dicefive.ui.common

import androidx.compose.runtime.Composable
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState

/**
 * Sends the system's back action (Android's back button or gesture) to [onBack] while this is in
 * the composition - the latest one composed wins. A stand-in for androidx.activity's BackHandler,
 * which is Android-only, built on the multiplatform navigation-event API that replaced Compose
 * Multiplatform's own. iOS has no system back action at all, so any flow that leans on this needs a
 * visible way out too - see .claude/IOS_SUPPORT.md.
 */
@Composable
internal fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = enabled,
        onBackCompleted = onBack,
    )
}
