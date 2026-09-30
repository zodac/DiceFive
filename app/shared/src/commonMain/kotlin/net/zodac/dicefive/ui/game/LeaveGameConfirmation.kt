package net.zodac.dicefive.ui.game

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import net.zodac.dicefive.ui.common.DiceFiveDialog

/**
 * The one "Leave game?" confirmation, whichever way the player is leaving - the system back
 * gesture, or a long-pressed achievement banner that jumps to the Achievements page. Whoever is
 * leaving hands the destination to [request]; [LeaveGameConfirmationDialog] shows the dialog, and
 * [isShowing] is what everything else reacts to: `GameScreen` holds the game's clocks and the
 * banner stack its countdowns for exactly as long as the dialog is up, so nothing pauses per
 * caller.
 */
class LeaveGameConfirmation {

    private var pendingLeave by mutableStateOf<(() -> Unit)?>(null)

    val isShowing: Boolean
        get() = pendingLeave != null

    /** Asks the player to confirm leaving; [onLeave] runs only if they do. */
    fun request(onLeave: () -> Unit) {
        pendingLeave = onLeave
    }

    internal fun confirm() {
        val leave = pendingLeave ?: return
        pendingLeave = null
        leave()
    }

    internal fun dismiss() {
        pendingLeave = null
    }
}

val LocalLeaveGameConfirmation = staticCompositionLocalOf { LeaveGameConfirmation() }

/** Shows [confirmation]'s dialog while it has a request pending. */
@Composable
fun LeaveGameConfirmationDialog(confirmation: LeaveGameConfirmation) {
    if (!confirmation.isShowing) return
    DiceFiveDialog(
        icon = Icons.AutoMirrored.Filled.Logout,
        title = "Leave game?",
        message = "Your progress is saved - you can continue this game later.",
        confirmLabel = "Leave",
        onConfirm = confirmation::confirm,
        dismissLabel = "Cancel",
        onDismiss = confirmation::dismiss,
        onDismissRequest = confirmation::dismiss,
    )
}
