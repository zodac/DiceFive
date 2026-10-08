package net.zodac.dicefive.baselineprofile

import android.content.res.Resources
import android.graphics.Rect
import java.util.regex.Pattern
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.Until

internal const val PACKAGE = "net.zodac.dicefive"
// Generous: on a hosted runner's emulator a single lookup of the screen can take two to four seconds, so
// a short timeout allows only one or two looks. It only costs time when something is really wrong.
private const val TIMEOUT_MS = 15_000L

/**
 * An integer passed to the run as an instrumentation argument - from Gradle,
 * `-Pandroid.testInstrumentationRunnerArguments.<name>=<n>` - or null when it wasn't. Used to cut a
 * run down to one or two laps while the journey itself is being checked.
 */
internal fun intArgument(name: String): Int? =
    InstrumentationRegistry.getArguments().getString(name)?.toIntOrNull()?.takeIf { it > 0 }

/**
 * The app's screens as steps the profile generator and the benchmarks share. Selectors are the
 * visible labels and the screen-reader descriptions the app already has (no test tags), so a
 * relabelled button breaks the journey - which is the point of [await]: it fails the run loudly
 * instead of quietly recording a thinner profile.
 */

/** Waits for [selector] and returns it, or fails the run naming it. */
internal fun MacrobenchmarkScope.await(selector: BySelector, what: String): UiObject2 =
    device.wait(Until.findObject(selector), TIMEOUT_MS)
        ?: error("Baseline Profile journey: '$what' never appeared")

internal fun MacrobenchmarkScope.tapText(text: String) = tap(text) { await(By.text(text), text) }

internal fun MacrobenchmarkScope.tapDesc(description: String, startsWith: Boolean = false) {
    val selector = if (startsWith) By.descStartsWith(description) else By.desc(description)
    tap(description) { await(selector, description) }
}

/**
 * Taps what [find] returns, looking it up again if it goes stale first: a node found while its screen
 * or dialog is still opening (the mode picker's list, say) can be re-composed before the tap reaches it.
 */
private fun MacrobenchmarkScope.tap(what: String, find: () -> UiObject2) {
    repeat(TAP_ATTEMPTS) {
        try {
            find().click()
            device.waitForIdle()
            return
        } catch (_: StaleObjectException) {
            // Re-composed between the lookup and the tap - look again.
        }
    }
    error("Baseline Profile journey: '$what' kept going stale before it could be tapped")
}

/** True if [text] is on screen right now, without waiting. */
internal fun MacrobenchmarkScope.hasText(text: String): Boolean = device.hasObject(By.text(text))

/** The menu's "DiceFive" wordmark (AppLogo): only the menu shows it, so its going says the menu has. */
private val MENU = By.text("DiceFive")

/**
 * Taps the menu's [label] button until the menu has gone. A tap can be dropped - one that lands while
 * a dialog's dim layer is still fading out over the menu ("Untrusted touch due to occlusion" in logcat),
 * or that a slow emulator doesn't take - and the steps after it would then run on the menu: their back
 * press leaves the app. Nor can the screen be told by its title, as the menu has a button of that name.
 */
internal fun MacrobenchmarkScope.openFromMenu(label: String) {
    await(By.text(label), label)
    repeat(CLOSE_ATTEMPTS) {
        try {
            device.findObject(By.text(label))?.click()
        } catch (_: StaleObjectException) {
            // Re-composed between the lookup and the tap - look again.
        }
        if (device.wait(Until.gone(MENU), TIMEOUT_MS)) {
            device.waitForIdle()
            return
        }
    }
    error("Baseline Profile journey: '$label' never opened from the menu")
}

/**
 * Taps a dialog's button, found by [selector], until the dialog is gone. A back press sent while it
 * is still closing reaches the dialog's window after it has dropped its back handler, and the system
 * takes it as leaving the app: the run lands on the launcher. A tap can also go unanswered: one that
 * lands while the dialog's text is still settling from a scroll only stops the scroll.
 */
private fun MacrobenchmarkScope.closeDialog(selector: BySelector, what: String) {
    repeat(CLOSE_ATTEMPTS) {
        try {
            device.findObject(selector)?.click()
        } catch (_: StaleObjectException) {
            // Re-composed between the lookup and the tap - look again.
        }
        if (device.wait(Until.gone(selector), CLOSE_WAIT_MS)) {
            device.waitForIdle()
            return
        }
    }
    error("Baseline Profile journey: '$what' never closed its dialog")
}

internal fun MacrobenchmarkScope.back() {
    device.pressBack()
    device.waitForIdle()
}

/** Swipes up the middle of the screen [times] times - a long page or a dialog's text. */
internal fun MacrobenchmarkScope.scrollDown(times: Int) {
    val x = device.displayWidth / 2
    repeat(times) {
        device.swipe(x, device.displayHeight * 3 / 4, x, device.displayHeight / 4, 20)
        device.waitForIdle()
    }
}

/** Swipes down the middle of the screen [times] times - back up a long page. */
internal fun MacrobenchmarkScope.scrollUp(times: Int) {
    val x = device.displayWidth / 2
    repeat(times) {
        device.swipe(x, device.displayHeight / 4, x, device.displayHeight * 3 / 4, 20)
        device.waitForIdle()
    }
}

/**
 * Taps the topmost match for [text]. For a label that appears twice on a screen, such as a Rules tab
 * and the page heading or footer pill of the same name: the tab rows are above everything else.
 */
internal fun MacrobenchmarkScope.tapTopText(text: String) = tap(text) {
    await(By.text(text), text)
    device.findObjects(By.text(text)).minByOrNull { it.visibleBounds.top }
        ?: error("Baseline Profile journey: '$text' never appeared")
}

/** Swipes along the screen at [heightFraction] of its height, right to left, [times] times. */
internal fun MacrobenchmarkScope.scrollRow(heightFraction: Float, times: Int) {
    val y = (device.displayHeight * heightFraction).toInt()
    repeat(times) {
        device.swipe(device.displayWidth * 9 / 10, y, device.displayWidth / 10, y, 20)
        device.waitForIdle()
    }
}

/** From the menu: Play (or New Game, when a game is saved) -> Start Game, then back to the menu. */
internal fun MacrobenchmarkScope.playATurn() {
    // Waited for, not just looked for: the menu may still be drawing, and "Play" is only offered without a saved game.
    await(By.text(Pattern.compile("Play|New Game")), "the menu's Play or New Game")
    openFromMenu(if (hasText("New Game")) "New Game" else "Play")
    tapText("Start Game")

    // Roll, hold a die, roll again, then score: the loop a player spends nearly all their time in.
    // Rolling animates, which waitForIdle can't see, so each roll is given a moment to settle.
    tapDesc("Dice cup", startsWith = true)
    Thread.sleep(ROLL_SETTLE_MS)
    tapDesc("Die 1", startsWith = true)
    tapDesc("Dice cup", startsWith = true)
    Thread.sleep(ROLL_SETTLE_MS)
    tapDesc("Chance")
    Thread.sleep(ROLL_SETTLE_MS)

    leaveGame()
}

/**
 * From the menu, with a game saved: Continue, wait for the board, then back out. The tap is tried
 * again if the board doesn't come: the system drops a touch that lands while the last dialog's dim
 * layer is still fading out over the menu ("Untrusted touch due to occlusion" in logcat).
 */
internal fun MacrobenchmarkScope.resumeGame() {
    val board = By.descStartsWith("Dice cup")
    await(By.text("Continue"), "Continue")
    repeat(CLOSE_ATTEMPTS) {
        device.findObject(By.text("Continue"))?.click()
        if (device.wait(Until.hasObject(board), TIMEOUT_MS)) {
            device.waitForIdle()
            leaveGame()
            return
        }
    }
    error("Baseline Profile journey: 'the board after Continue' never appeared")
}

/**
 * Back out of a game: the back press asks first (unless the setting is off), and "Leave" confirms. It
 * waits for whichever comes - the dialog or the menu - rather than giving the dialog a fixed time: one
 * that took longer to appear than that was taken for no dialog, and the menu behind it never came.
 */
private fun MacrobenchmarkScope.leaveGame() {
    back()
    await(By.text(Pattern.compile("Leave game\\?|Settings")), "the leave dialog or the menu after leaving a game")
    if (hasText("Leave game?")) {
        closeDialog(By.text("Leave"), "the leave dialog's Leave button")
    }
    await(By.text("Settings"), "the menu after leaving a game")
}

/** Settings, flipping one switch (and flipping it back), picking an animation level (and back) and opening the Licences and About dialogs. */
internal fun MacrobenchmarkScope.visitSettings() {
    openFromMenu("Settings")
    tapText("Sound effects")
    tapText("Sound effects")
    // Back to High: the journeys after this one are profiling the full animations.
    tapText("Low")
    tapText("High")

    // Each dialog is waited for by its Close button and left by it: the Settings page behind is
    // itself scrollable and has the same "Licences" text, so neither a scrollable nor a label says
    // the dialog is up, and a back press sent too early would leave Settings altogether.
    tapText("Licences")
    await(By.desc("Close licences"), "the Licences dialog's Close button")
    scrollDown(3)
    closeDialog(By.desc("Close licences"), "the Licences dialog's Close button")

    tapText("About")
    await(By.desc("Close about"), "the About dialog's Close button")
    scrollDown(1)
    closeDialog(By.desc("Close about"), "the About dialog's Close button")

    back()
}

/**
 * Locks every achievement again (Settings > Reset achievements), so the "Not Those Dice!" unlock in
 * [visitAchievementBanner] fires on every lap of the run - it can only be earned once, and the app's
 * data survives from one lap to the next.
 */
internal fun MacrobenchmarkScope.resetAchievements() {
    openFromMenu("Settings")
    tapText("Reset achievements")
    await(By.text("Reset"), "the reset dialog's Reset button")
    closeDialog(By.text("Reset"), "the reset dialog's Reset button")
    back()
}

/**
 * Taps the logo's dice on the menu (the "Not Those Dice!" easter egg) to raise an unlock banner,
 * then long-presses the banner, which takes the player to that achievement on the Achievements
 * screen, where its row glows. Needs the achievement locked - see [resetAchievements].
 *
 * The dice are drawn with a raw tap gesture and have no label to find them by, so they are found
 * from the wordmark under them: AppLogo puts "DiceFive" 16dp below the row of dice (each 34dp tall),
 * so the row's middle is about 33dp above the wordmark's top. A few nearby heights are tried in case
 * the fan sits a little off that. Every one of them is above the wordmark, well clear of the buttons -
 * and the run fails the moment a tap leaves the menu, rather than carrying on tapping elsewhere.
 */
internal fun MacrobenchmarkScope.visitAchievementBanner() {
    for (attempt in 1..BANNER_ATTEMPTS) {
        // A banner that was raised but missed can't be raised again until the achievement is locked.
        if (attempt > 1) resetAchievements()
        val bannerBounds = withShortIdleWait { raiseBanner() } ?: continue
        longPressAt(bannerBounds)

        // Lands on the Achievements screen - told by the menu going, as the menu has an Achievements
        // button too; if it stays, the press missed, and the banner is tried again. Then the row's gold
        // flash is given time to play out before leaving.
        if (!device.wait(Until.gone(MENU), TIMEOUT_MS)) continue
        await(By.text("Achievements"), "the Achievements screen after the banner's long-press")
        Thread.sleep(GLOW_MS)
        back()
        return
    }
    error("Baseline Profile journey: tapping the logo's dice never raised a 'Not Those Dice!' banner it could press")
}

/**
 * Taps the logo's dice (see [visitAchievementBanner]) until the banner appears, and returns where it is,
 * or null if no tap raised it - or it was gone again before it could be found.
 */
private fun MacrobenchmarkScope.raiseBanner(): Rect? {
    val density = Resources.getSystem().displayMetrics.density
    val wordmarkTop = await(MENU, "the menu's DiceFive wordmark").visibleBounds.top
    val x = device.displayWidth / 2
    val banner = By.descStartsWith("Achievement unlocked: Not Those Dice!")

    for (heightDp in DICE_PROBE_HEIGHTS_DP) {
        device.click(x, wordmarkTop - (heightDp * density).toInt())
        val node = device.wait(Until.findObject(banner), BANNER_APPEAR_MS)
        if (node != null) return boundsOf(node, banner)
        check(device.hasObject(MENU)) {
            "Baseline Profile journey: a tap ${heightDp}dp above the wordmark left the menu"
        }
    }
    return null
}

/**
 * Runs [block] with UiAutomator's wait for the screen to go idle cut to [SHORT_IDLE_WAIT_MS]. Every node
 * lookup waits for that first, and the menu and the banner animate, so on a hosted runner's emulator each
 * lookup sat out two to four seconds of it - most of the four seconds the banner stays up, so the banner
 * was found only as it went. The rest of the journey keeps the usual wait.
 */
private inline fun <T> withShortIdleWait(block: () -> T): T {
    val configurator = Configurator.getInstance()
    val saved = configurator.waitForIdleTimeout
    configurator.waitForIdleTimeout = SHORT_IDLE_WAIT_MS
    try {
        return block()
    } finally {
        configurator.waitForIdleTimeout = saved
    }
}

/**
 * Where [node] is on screen, looking [selector] up once more if [node] has gone stale in the meantime
 * (the banner re-composes as it fades in), or null if that finds nothing either.
 */
private fun MacrobenchmarkScope.boundsOf(node: UiObject2, selector: BySelector): Rect? =
    try {
        node.visibleBounds
    } catch (_: StaleObjectException) {
        device.findObject(selector)?.visibleBounds
    }

/**
 * Long-presses the middle of [bounds]: a finger held still there for [LONG_PRESS_STEPS] steps of
 * UiAutomator's 5ms, well past the long-press timeout. Pressed by position, not through the banner's
 * node: the banner only stays up for four seconds, and on a hosted runner's emulator each node lookup
 * took two to four of them, so looking it up again to press it found it already gone. A held finger
 * also stops its countdown.
 */
private fun MacrobenchmarkScope.longPressAt(bounds: Rect) {
    device.swipe(bounds.centerX(), bounds.centerY(), bounds.centerX(), bounds.centerY(), LONG_PRESS_STEPS)
}

/**
 * From the menu, with a game saved (see [playATurn]): New Game in a mode other than Standard, a roll, then
 * out again. Afterwards the mode is put back to Standard, so the next lap's [playATurn] - and anything else
 * that starts a game - gets the mode it expects (the setup remembers the last pick).
 */
internal fun MacrobenchmarkScope.playAModeGame() {
    openFromMenu("New Game")
    chooseMode("Tricolour")
    tapText("Start Game")
    tapDesc("Dice cup", startsWith = true)
    Thread.sleep(ROLL_SETTLE_MS)
    leaveGame()

    openFromMenu("New Game")
    chooseMode("Standard")
    // Back only once the picker's dialog has gone: a back press sent while it closes leaves the app (see
    // closeDialog). Its list is the only place the other mode's name shows, so that going says it has
    // closed; the pause after covers the rest of its fade.
    await(By.text("Start Game"), "the setup after choosing a mode")
    device.wait(Until.gone(By.text("Tricolour")), TIMEOUT_MS)
    Thread.sleep(PICKER_CLOSE_MS)
    back()
    await(By.text("Settings"), "the menu after leaving the setup")
}

/**
 * Opens the setup's mode picker and picks [mode], which must not be the current one: the field shows
 * the current mode's name too, so that name could be found twice.
 */
private fun MacrobenchmarkScope.chooseMode(mode: String) {
    tapDesc("Game Mode")
    tapText(mode)
}

/** A long page of text: the Rules, then the Modes group and one of its pages. */
internal fun MacrobenchmarkScope.visitRules() {
    openFromMenu("Rules")
    scrollDown(4)
    tapTopText("Modes")
    tapTopText("Tricolour")
    scrollDown(2)
    back()
}

/** Leaderboard and Statistics, which only need opening. */
internal fun MacrobenchmarkScope.visitScoreScreens() {
    openFromMenu("Leaderboard")
    back()
    openFromMenu("Statistics")
    back()
}

internal fun MacrobenchmarkScope.visitAchievements() {
    openFromMenu("Achievements")
    scrollDown(2)
    back()
}

/**
 * The Styles page: the costliest to open the first time (BENCHMARKS.md, "The Styles page"), so
 * every row is swiped along and the page scrolled down, which composes each tile once.
 */
internal fun MacrobenchmarkScope.visitStyles() {
    openFromMenu("Styles")
    // Dice gallery on (every tile at once), down the page and back, then off for the rows below.
    tapDesc("Dice gallery")
    scrollDown(2)
    scrollUp(2)
    tapDesc("Dice gallery")
    for (fraction in listOf(0.25f, 0.45f, 0.65f, 0.85f)) scrollRow(fraction, 2)
    scrollDown(1)
    for (fraction in listOf(0.25f, 0.45f, 0.65f, 0.85f)) scrollRow(fraction, 2)
    back()
}

private const val ROLL_SETTLE_MS = 2_000L
private const val PICKER_CLOSE_MS = 1_000L
private const val CLOSE_ATTEMPTS = 4
private const val TAP_ATTEMPTS = 4
private const val CLOSE_WAIT_MS = 3_000L

/** Heights above the wordmark's top to tap, best guess first: 16dp gap plus half a 34dp die, then either side. */
private val DICE_PROBE_HEIGHTS_DP = listOf(33, 25, 41, 17, 49)
private const val BANNER_APPEAR_MS = 2_000L
private const val GLOW_MS = 2_000L
private const val LONG_PRESS_STEPS = 200
private const val BANNER_ATTEMPTS = 3
private const val SHORT_IDLE_WAIT_MS = 100L
