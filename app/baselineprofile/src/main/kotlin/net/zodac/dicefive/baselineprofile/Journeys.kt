package net.zodac.dicefive.baselineprofile

import android.content.res.Resources
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.Until

internal const val PACKAGE = "net.zodac.dicefive"
private const val TIMEOUT_MS = 5_000L

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

internal fun MacrobenchmarkScope.tapText(text: String) {
    await(By.text(text), text).click()
    device.waitForIdle()
}

internal fun MacrobenchmarkScope.tapDesc(description: String, startsWith: Boolean = false) {
    val selector = if (startsWith) By.descStartsWith(description) else By.desc(description)
    await(selector, description).click()
    device.waitForIdle()
}

/** True if [text] is on screen right now, without waiting. */
internal fun MacrobenchmarkScope.hasText(text: String): Boolean = device.hasObject(By.text(text))

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
    if (hasText("New Game")) tapText("New Game") else tapText("Play")
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

/** From the menu, with a game saved: Continue, wait for the board, then back out. */
internal fun MacrobenchmarkScope.resumeGame() {
    tapText("Continue")
    await(By.descStartsWith("Dice cup"), "the board after Continue")
    device.waitForIdle()
    leaveGame()
}

/** Back out of a game: the back press asks first (unless the setting is off), and "Leave" confirms. */
private fun MacrobenchmarkScope.leaveGame() {
    back()
    if (device.wait(Until.hasObject(By.text("Leave game?")), LEAVE_DIALOG_MS)) tapText("Leave")
    await(By.text("Settings"), "the menu after leaving a game")
}

/** Settings, flipping one switch (and flipping it back) and opening the Licences and Credits dialogs. */
internal fun MacrobenchmarkScope.visitSettings() {
    tapText("Settings")
    tapText("Sound effects")
    tapText("Sound effects")

    // Each dialog is waited for by its Close button and left by it: the Settings page behind is
    // itself scrollable and has the same "Licences" text, so neither a scrollable nor a label says
    // the dialog is up, and a back press sent too early would leave Settings altogether.
    tapText("Licences")
    await(By.desc("Close"), "the Licences dialog's Close button")
    scrollDown(3)
    tapDesc("Close")

    tapText("Credits")
    await(By.desc("Close"), "the Credits dialog's Close button")
    scrollDown(1)
    tapDesc("Close")

    back()
}

/**
 * Locks every achievement again (Settings > Reset achievements), so the "Not Those Dice!" unlock in
 * [visitAchievementBanner] fires on every lap of the run - it can only be earned once, and the app's
 * data survives from one lap to the next.
 */
internal fun MacrobenchmarkScope.resetAchievements() {
    tapText("Settings")
    tapText("Reset achievements")
    tapText("Reset")
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
    val density = Resources.getSystem().displayMetrics.density
    val wordmark = By.text("DiceFive")
    val wordmarkTop = await(wordmark, "the menu's DiceFive wordmark").visibleBounds.top
    val x = device.displayWidth / 2
    val banner = By.descStartsWith("Achievement unlocked: Not Those Dice!")

    var raised = false
    for (heightDp in DICE_PROBE_HEIGHTS_DP) {
        device.click(x, wordmarkTop - (heightDp * density).toInt())
        raised = device.wait(Until.hasObject(banner), BANNER_APPEAR_MS)
        if (raised) break
        check(device.hasObject(wordmark)) {
            "Baseline Profile journey: a tap ${heightDp}dp above the wordmark left the menu"
        }
    }
    check(raised) { "Baseline Profile journey: tapping the logo's dice never raised the 'Not Those Dice!' banner" }
    longClickBanner(banner)

    // Lands on the Achievements screen; give the row's gold flash time to play out before leaving.
    await(By.text("Achievements"), "the Achievements screen after the banner's long-press")
    Thread.sleep(GLOW_MS)
    back()
}

/**
 * Long-presses the banner. It is still fading and sliding in when it is first seen, so the node
 * found a moment ago can be gone by the time it is pressed (a StaleObjectException): look it up
 * again for each attempt, rather than holding on to the first one.
 */
private fun MacrobenchmarkScope.longClickBanner(banner: BySelector) {
    Thread.sleep(BANNER_SETTLE_MS)
    repeat(LONG_CLICK_ATTEMPTS) {
        val node = device.findObject(banner)
        if (node != null) {
            try {
                node.longClick()
                return
            } catch (_: StaleObjectException) {
                // Moved or re-composed between the lookup and the press - look again.
            }
        }
        Thread.sleep(BANNER_SETTLE_MS)
    }
    error("Baseline Profile journey: the 'Not Those Dice!' banner could not be long-pressed")
}

/** A long page of text: the Rules. */
internal fun MacrobenchmarkScope.visitRules() {
    tapText("Rules")
    scrollDown(4)
    back()
}

/** Leaderboard and Statistics, which only need opening. */
internal fun MacrobenchmarkScope.visitScoreScreens() {
    tapText("Leaderboard")
    back()
    tapText("Statistics")
    back()
}

internal fun MacrobenchmarkScope.visitAchievements() {
    tapText("Achievements")
    scrollDown(2)
    back()
}

/**
 * The Styles page: the costliest to open the first time (BENCHMARKS.md, "The Styles page"), so
 * every row is swiped along and the page scrolled down, which composes each tile once.
 */
internal fun MacrobenchmarkScope.visitStyles() {
    tapText("Styles")
    device.waitForIdle()
    for (fraction in listOf(0.25f, 0.45f, 0.65f, 0.85f)) scrollRow(fraction, 2)
    scrollDown(1)
    for (fraction in listOf(0.25f, 0.45f, 0.65f, 0.85f)) scrollRow(fraction, 2)
    back()
}

private const val ROLL_SETTLE_MS = 2_000L
private const val LEAVE_DIALOG_MS = 1_500L

/** Heights above the wordmark's top to tap, best guess first: 16dp gap plus half a 34dp die, then either side. */
private val DICE_PROBE_HEIGHTS_DP = listOf(33, 25, 41, 17, 49)
private const val BANNER_APPEAR_MS = 800L
private const val GLOW_MS = 2_000L
private const val BANNER_SETTLE_MS = 400L
private const val LONG_CLICK_ATTEMPTS = 5
