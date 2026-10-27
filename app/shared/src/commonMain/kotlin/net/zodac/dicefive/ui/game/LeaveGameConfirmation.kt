package net.zodac.dicefive.ui.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import net.zodac.dicefive.resources.Res
import net.zodac.dicefive.resources.game_leave_confirm
import net.zodac.dicefive.resources.game_leave_dismiss
import net.zodac.dicefive.resources.game_leave_message
import net.zodac.dicefive.resources.game_leave_title
import net.zodac.dicefive.resources.game_leave_tutorial_message
import net.zodac.dicefive.resources.game_leave_tutorial_title
import net.zodac.dicefive.ui.common.DiceFiveDialog
import net.zodac.dicefive.ui.common.stringResource
import net.zodac.dicefive.ui.icons.Symbols

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

    /** Whether it's the tutorial being left, which the dialog words as its own. */
    internal var leavingTutorial by mutableStateOf(false)
        private set

    val isShowing: Boolean
        get() = pendingLeave != null

    /** Asks the player to confirm leaving - the [tutorial], or a game; [onLeave] runs only if they do. */
    fun request(tutorial: Boolean = false, onLeave: () -> Unit) {
        leavingTutorial = tutorial
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
        icon = Symbols.Logout,
        title = stringResource(if (confirmation.leavingTutorial) Res.string.game_leave_tutorial_title else Res.string.game_leave_title),
        message = stringResource(if (confirmation.leavingTutorial) Res.string.game_leave_tutorial_message else Res.string.game_leave_message),
        confirmLabel = stringResource(Res.string.game_leave_confirm),
        onConfirm = confirmation::confirm,
        dismissLabel = stringResource(Res.string.game_leave_dismiss),
        onDismiss = confirmation::dismiss,
        onDismissRequest = confirmation::dismiss,
    )
}
