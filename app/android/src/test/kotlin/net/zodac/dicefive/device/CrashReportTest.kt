package net.zodac.dicefive.device

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.zodac.dicefive.app.Breadcrumbs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** A crash report: the page's text, the GitHub issue it fills in, and the Intent extras that carry it. */
@RunWith(AndroidJUnit4::class)
class CrashReportTest {

    private fun report(trace: String = "java.lang.IllegalStateException: Boom\n\tat Game.roll(Game.kt:1)", lastActions: List<String> = listOf("Opened menu", "Rolled, player 1")) =
        CrashReport(
            app = "DiceFive 1.3.0 (play release)",
            android = "17 (API 37)",
            device = "Google Pixel 9",
            screen = "411x914dp, font 1.0x",
            language = "en-GB",
            thread = "main",
            lastActions = lastActions,
            trace = trace,
        )

    @Test
    fun `the page's text has every field, the last actions oldest first, then the trace`() {
        assertEquals(
            """
            App: DiceFive 1.3.0 (play release)
            Android: 17 (API 37)
            Device: Google Pixel 9
            Screen: 411x914dp, font 1.0x
            Language: en-GB
            Thread: main
            Last actions:
            - Opened menu
            - Rolled, player 1
            java.lang.IllegalStateException: Boom
            	at Game.roll(Game.kt:1)
            """.trimIndent(),
            report().text(),
        )
    }

    @Test
    fun `no last actions say so`() {
        assertTrue("Last actions:\n- none\n" in report(lastActions = emptyList()).text())
    }

    @Test
    fun `the issue is titled by the error and filled in with a table of the details, the last actions and the trace`() {
        val url = Uri.parse(report().issueUrl())
        assertEquals("https://github.com/zodac/DiceFive/issues/new", "${url.scheme}://${url.host}${url.path}")
        assertEquals("[Crash] java.lang.IllegalStateException: Boom", url.getQueryParameter("title"))
        assertEquals(
            """
            **What were you doing when it happened?**

            (Say here)

            **Details**

            | | |
            |---|---|
            | App | DiceFive 1.3.0 (play release) |
            | Android | 17 (API 37) |
            | Device | Google Pixel 9 |
            | Screen | 411x914dp, font 1.0x |
            | Language | en-GB |
            | Thread | main |

            **Last actions** (oldest first)

            1. Opened menu
            2. Rolled, player 1

            **Stack trace**

            ```
            java.lang.IllegalStateException: Boom
            	at Game.roll(Game.kt:1)
            ```
            """.trimIndent(),
            url.getQueryParameter("body"),
        )
    }

    @Test
    fun `a trace too long for a link is cut from its end - saying so - and the link fits`() {
        val trace = "java.lang.StackOverflowError\n" + (1..2_000).joinToString("\n") { "\tat Game.recurse(Game.kt:$it)" }
        val link = report(trace = trace).issueUrl()
        assertTrue(link.length <= CrashReport.MAX_URL_LENGTH)
        val body = checkNotNull(Uri.parse(link).getQueryParameter("body"))
        assertTrue("java.lang.StackOverflowError\n\tat Game.recurse(Game.kt:1)\n" in body)
        assertTrue("\t... cut to fit the link - the crash page's Copy details has it all\n```" in body)
    }

    @Test
    fun `a report goes through an Intent's extras whole`() {
        val report = report()
        val back = CrashReport.from(report.toBundle())
        assertEquals(report.text(), back.text())
    }

    @Test
    fun `a report made from an error has its thread, its trace and the breadcrumbs`() {
        Breadcrumbs.record("Scored CHANCE, player 1")
        val error = IllegalStateException("Boom")
        val report = CrashReport.of(ApplicationProvider.getApplicationContext(), Thread("game-worker"), error)
        assertEquals("game-worker", report.thread)
        assertEquals(error.stackTraceToString(), report.trace)
        assertEquals("Scored CHANCE, player 1", report.lastActions.last())
        assertTrue(report.app.startsWith("DiceFive "))
    }

    @Test
    fun `a trace too long for an Intent is cut to fit`() {
        val report = CrashReport.of(ApplicationProvider.getApplicationContext(), Thread.currentThread(), IllegalStateException("x".repeat(500_000)))
        assertTrue(report.trace.length <= CrashReport.MAX_TRACE_LENGTH)
    }
}
