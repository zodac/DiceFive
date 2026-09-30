package net.zodac.dicefive

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The app does nothing in the background, and a plain `delay` is how that would quietly stop being true:
 * it keeps counting after the app is backgrounded (the process, and its coroutines, stay alive), so a
 * banner clears itself unseen, a turn is forfeited or a CPU's played out behind the player's back.
 * Every wait in the shared code goes through `Lifecycle.delayWhileResumed` (UI) or
 * `GameViewModel.pausableDelay`, which don't run while the app isn't in front. See UI.md, "Nothing runs
 * in the background".
 */
class NoBackgroundTimersTest {

    private val sharedSource = File("../shared/src/commonMain")

    @Test
    fun `no shared code waits with a plain delay`() {
        assertTrue("Run from app/android, next to app/shared: ${sharedSource.absolutePath}", sharedSource.isDirectory)
        // A bare delay( - not delayWhileResumed( or pausableDelay(, and not a call on something else.
        val plainDelay = Regex("""(?<![\w.])delay\(""")
        val offenders = sharedSource.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    val code = line.substringBefore("//")
                    if (plainDelay.containsMatchIn(code)) "${file.name}:${index + 1}: ${line.trim()}" else null
                }
            }
            .toList()

        assertTrue("Plain delay() keeps running in the background - use delayWhileResumed or pausableDelay:\n${offenders.joinToString("\n")}", offenders.isEmpty())
    }
}
