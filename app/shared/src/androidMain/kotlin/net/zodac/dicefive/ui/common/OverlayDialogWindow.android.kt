package net.zodac.dicefive.ui.common

import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

@Composable
internal actual fun ConfigureOverlayDialogWindow() {
    val view = LocalView.current
    SideEffect {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
        // No dim at all, rather than a dim of 0: with the flag set the system still lays a full-screen (invisible) dim layer
        // under the banner, and from Android 12 a touch on a window beneath it - the dialog the banner is shown over, say
        // the About dialog's close button - is dropped as an untrusted occlusion while the banner is up.
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        // Compose makes a `usePlatformDefaultWidth = false` dialog window match-parent in both directions, and a full-screen
        // window takes every touch (NOT_TOUCH_MODAL only passes touches *outside* its bounds). Wrap the height so only the
        // strip the content occupies intercepts anything.
        window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
        window.setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
    }
}
