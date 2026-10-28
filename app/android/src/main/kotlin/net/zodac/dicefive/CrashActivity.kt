package net.zodac.dicefive

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.core.net.toUri
import net.zodac.dicefive.data.settings.AnimationLevel
import net.zodac.dicefive.device.CrashHandler
import net.zodac.dicefive.device.CrashReport
import net.zodac.dicefive.ui.common.CrashPage
import net.zodac.dicefive.ui.common.LastAnimationLevel
import net.zodac.dicefive.ui.common.LocalAnimationLevel
import net.zodac.dicefive.ui.common.LocalReduceMotion
import net.zodac.dicefive.ui.common.RevealedPage
import net.zodac.dicefive.ui.common.stringsLanguageConfiguration
import net.zodac.dicefive.ui.theme.DiceFiveTheme

/**
 * The page an error nothing caught leaves in place of the app ([CrashHandler]): [CrashPage], with the [CrashReport] the
 * handler wrote, to copy or post as a GitHub issue. It runs in a process of its own, where none of the app is set up -
 * the app's own process is on its way down - and Restart starts the app again in a fresh one.
 */
class CrashActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        // In the strings' language, as MainActivity is.
        stringsLanguageConfiguration()?.let(::applyOverrideConfiguration)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val report = CrashReport.from(intent.getBundleExtra(EXTRA_REPORT))
        val details = report.text()
        // The level the app was drawn at, as DiceFiveApp provides it: this process doesn't read the settings.
        val animationLevel = AnimationLevel.entries.firstOrNull { it.id == intent.getStringExtra(EXTRA_ANIMATION_LEVEL) } ?: AnimationLevel.default
        setContent {
            CompositionLocalProvider(LocalAnimationLevel provides animationLevel, LocalReduceMotion provides !animationLevel.gameplayMotion) {
                DiceFiveTheme {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        // Faded in as a page opened from the menu is: Android's own window animation is off (intent).
                        RevealedPage {
                            CrashPage(details = details, onRestart = ::restart, onCopy = { copy(details) }, onReport = { openIssue(report) })
                        }
                    }
                }
            }
        }
    }

    /** The app again, in its own process - and this page's ended, rather than kept in memory beside it. */
    private fun restart() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
        if (CrashHandler.isCrashProcess()) Process.killProcess(Process.myPid())
    }

    /** [details] on the clipboard, as plain text. */
    private fun copy(details: String) {
        getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(CLIP_LABEL, details))
    }

    /** The browser, at a new GitHub issue with [report] filled in - or nothing, on a phone with no browser. */
    private fun openIssue(report: CrashReport) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, report.issueUrl().toUri()))
        } catch (_: ActivityNotFoundException) {
            // Copy details is still there.
        }
    }

    companion object {
        private const val EXTRA_REPORT = "net.zodac.dicefive.CRASH_REPORT"
        private const val EXTRA_ANIMATION_LEVEL = "net.zodac.dicefive.ANIMATION_LEVEL"

        /** What the clipboard says it holds. */
        private const val CLIP_LABEL = "DiceFive crash report"

        /** Where Compose resources keep their context: the library's own, internal to it, and kept by name by r8-rules.pro. */
        internal const val RESOURCES_CONTEXT_CLASS = "org.jetbrains.compose.resources.AndroidContextProvider"
        internal const val RESOURCES_CONTEXT_FIELD = "ANDROID_CONTEXT"

        /**
         * Gives Compose resources [context] where they have none, before anything reads them - the Application's to call.
         * Their ContentProvider sets it, and a provider only starts in the app's own process, never this page's.
         */
        internal fun provideComposeResourcesContext(context: Context) {
            runCatching {
                val field = Class.forName(RESOURCES_CONTEXT_CLASS).getDeclaredField(RESOURCES_CONTEXT_FIELD).apply { isAccessible = true }
                if (field.get(null) == null) field.set(null, context)
            }
        }

        /**
         * Opens the page with [report], in a task of its own in place of the app's, drawn at [animationLevel] (the app's
         * last). Without Android's window animation: the page fades in as the app's own pages do.
         */
        internal fun intent(context: Context, report: CrashReport, animationLevel: AnimationLevel = LastAnimationLevel.value): Intent =
            Intent(context, CrashActivity::class.java)
                .putExtra(EXTRA_REPORT, report.toBundle())
                .putExtra(EXTRA_ANIMATION_LEVEL, animationLevel.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
    }
}
