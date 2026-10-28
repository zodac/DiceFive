package net.zodac.dicefive.device

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import java.util.Locale
import net.zodac.dicefive.BuildConfig
import net.zodac.dicefive.app.Breadcrumbs

/**
 * What an error nothing caught leaves to report: the build, the device, the thread it was thrown on, what was done
 * just before ([Breadcrumbs]), and its stack trace. Developer text, never translated. [text] is what the crash page
 * shows and copies; [issueUrl] opens a GitHub issue with the same filled in, for the player to post.
 */
internal class CrashReport(
    val app: String,
    val android: String,
    val device: String,
    val screen: String,
    val language: String,
    val thread: String,
    val lastActions: List<String>,
    val trace: String,
) {

    /** Everything, as plain text. */
    fun text(): String = buildString {
        for ((name, value) in fields()) appendLine("$name: $value")
        appendLine("Last actions:")
        if (lastActions.isEmpty()) appendLine("- none")
        for (action in lastActions) appendLine("- $action")
        append(trace)
    }

    /**
     * A new issue on the app's repository with this report filled in, which the player reads and posts themselves. The
     * trace is cut, from its end, to keep the link within [MAX_URL_LENGTH]: the whole of it is on the page to copy.
     */
    fun issueUrl(): String {
        val title = "[Crash] ${trace.lineSequence().first().take(MAX_TITLE_LENGTH)}"
        val lines = trace.lines()
        fun url(kept: Int): String {
            val cut = if (kept < lines.size) "\n\t... cut to fit the link - the crash page's Copy details has it all" else ""
            return "$NEW_ISSUE_URL?title=${Uri.encode(title)}&body=${Uri.encode(issueBody(lines.take(kept).joinToString("\n") + cut))}"
        }
        var low = 0
        var high = lines.size
        // The most of the trace's lines that fit, found by halving.
        while (low < high) {
            val middle = (low + high + 1) / 2
            if (url(middle).length <= MAX_URL_LENGTH) low = middle else high = middle - 1
        }
        return url(low)
    }

    private fun issueBody(trace: String): String = buildString {
        appendLine("**What were you doing when it happened?**")
        appendLine()
        appendLine("(Say here)")
        appendLine()
        appendLine("**Details**")
        appendLine()
        appendLine("| | |")
        appendLine("|---|---|")
        for ((name, value) in fields()) appendLine("| $name | ${value.replace("|", "\\|")} |")
        appendLine()
        appendLine("**Last actions** (oldest first)")
        appendLine()
        if (lastActions.isEmpty()) appendLine("None")
        lastActions.forEachIndexed { index, action -> appendLine("${index + 1}. $action") }
        appendLine()
        appendLine("**Stack trace**")
        appendLine()
        appendLine("```")
        appendLine(trace)
        append("```")
    }

    private fun fields(): List<Pair<String, String>> =
        listOf("App" to app, "Android" to android, "Device" to device, "Screen" to screen, "Language" to language, "Thread" to thread)

    /** The report as an Intent's extras. */
    fun toBundle(): Bundle = Bundle().apply {
        putString(APP, app)
        putString(ANDROID, android)
        putString(DEVICE, device)
        putString(SCREEN, screen)
        putString(LANGUAGE, language)
        putString(THREAD, thread)
        putStringArrayList(LAST_ACTIONS, ArrayList(lastActions))
        putString(TRACE, trace)
    }

    companion object {
        private const val NEW_ISSUE_URL = "https://github.com/zodac/DiceFive/issues/new"

        /** The longest link GitHub is given: its own limit is a little over 8,000 characters. */
        const val MAX_URL_LENGTH = 7_500

        private const val MAX_TITLE_LENGTH = 120

        /** The most of a trace kept - an Intent's extras have to fit a binder transaction. */
        const val MAX_TRACE_LENGTH = 100_000

        private const val APP = "app"
        private const val ANDROID = "android"
        private const val DEVICE = "device"
        private const val SCREEN = "screen"
        private const val LANGUAGE = "language"
        private const val THREAD = "thread"
        private const val LAST_ACTIONS = "lastActions"
        private const val TRACE = "trace"

        /** The report of [error], thrown on [thread], in the app [context] is from. */
        fun of(context: Context, thread: Thread, error: Throwable): CrashReport {
            val configuration = context.resources.configuration
            return CrashReport(
                app = "DiceFive ${BuildConfig.VERSION_NAME} (${BuildConfig.FLAVOR} ${BuildConfig.BUILD_TYPE})",
                android = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                device = "${Build.MANUFACTURER} ${Build.MODEL}",
                screen = "${configuration.screenWidthDp}x${configuration.screenHeightDp}dp, font ${configuration.fontScale}x",
                language = Locale.getDefault().toLanguageTag(),
                thread = thread.name,
                lastActions = Breadcrumbs.recent,
                trace = runCatching { error.stackTraceToString() }.getOrElse { error.toString() }.take(MAX_TRACE_LENGTH),
            )
        }

        /** The report [toBundle] wrote, with an empty one's fields blank. */
        fun from(bundle: Bundle?): CrashReport = CrashReport(
            app = bundle?.getString(APP).orEmpty(),
            android = bundle?.getString(ANDROID).orEmpty(),
            device = bundle?.getString(DEVICE).orEmpty(),
            screen = bundle?.getString(SCREEN).orEmpty(),
            language = bundle?.getString(LANGUAGE).orEmpty(),
            thread = bundle?.getString(THREAD).orEmpty(),
            lastActions = bundle?.getStringArrayList(LAST_ACTIONS).orEmpty(),
            trace = bundle?.getString(TRACE).orEmpty(),
        )
    }
}
