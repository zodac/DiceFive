package net.zodac.dicefive.ui.common

import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics

/**
 * A tap and a long press, with the same two actions offered to a screen reader, named by [onClickLabel] and
 * [onLongClickLabel] ("Double-tap to select, double-tap and hold to choose a colour").
 *
 * - [screenReaderClick] is what activating the element does for a screen reader when that isn't what a tap does (a tap
 *   shows a tooltip, which isn't announced, so a screen reader's one action is the dialog that says the same and more).
 * - [clearSemantics] drops the semantics of everything inside, for an element that speaks for itself through
 *   [semantics] (its description, state and position). The long-press action is then only offered when
 *   [onLongClickLabel] names it, since an unnamed one would just repeat the click's.
 *
 * Put it ahead of other semantics and gestures on the element: what is set first is what a screen reader reads.
 */
fun Modifier.accessibleCombinedClickable(
    onClick: () -> Unit,
    onClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    role: Role? = null,
    screenReaderClick: (() -> Unit)? = null,
    clearSemantics: Boolean = false,
    semantics: SemanticsPropertyReceiver.() -> Unit = {},
): Modifier {
    val actions: SemanticsPropertyReceiver.() -> Unit = {
        semantics()
        if (role != null) this.role = role
        if (clearSemantics || screenReaderClick != null) {
            onClick(label = onClickLabel) {
                (screenReaderClick ?: onClick)()
                true
            }
        }
        if (onLongClick != null && onLongClickLabel != null && clearSemantics) {
            onLongClick(label = onLongClickLabel) {
                onLongClick()
                true
            }
        }
    }
    val spoken = if (clearSemantics) Modifier.clearAndSetSemantics(actions) else Modifier.semantics(mergeDescendants = true, actions)
    return this
        .then(spoken)
        .combinedClickable(
            onClickLabel = onClickLabel,
            onLongClickLabel = onLongClickLabel,
            role = role,
            onLongClick = onLongClick,
            onClick = onClick,
        )
}
