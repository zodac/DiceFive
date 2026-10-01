package net.zodac.dicefive.baselineprofile

import android.content.res.Resources
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiObject2
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
 * The dice are drawn with a raw tap gesture and have no label to find them by, so this probes: it
 * taps up the column above the menu buttons, where the logo sits, until a banner appears.
 */
internal fun MacrobenchmarkScope.visitAchievementBanner() {
    val density = Resources.getSystem().displayMetrics.density
    // The logo ends 48dp above the first button (MenuScreen's Spacer), so start there and work up.
    val buttonsTop = await(By.text("Achievements"), "the menu's buttons").visibleBounds.top
    val x = device.displayWidth / 2
    val banner = By.descStartsWith("Achievement unlocked: Not Those Dice!")

    var found: UiObject2? = null
    var offsetDp = LOGO_PROBE_START_DP
    while (found == null && offsetDp <= LOGO_PROBE_END_DP) {
        device.click(x, buttonsTop - (offsetDp * density).toInt())
        found = device.wait(Until.findObject(banner), BANNER_APPEAR_MS)
        offsetDp += LOGO_PROBE_STEP_DP
    }
    (found ?: error("Baseline Profile journey: tapping the logo never raised the 'Not Those Dice!' banner"))
        .longClick()

    // Lands on the Achievements screen; give the row's gold flash time to play out before leaving.
    await(By.text("Achievements"), "the Achievements screen after the banner's long-press")
    Thread.sleep(GLOW_MS)
    back()
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

private const val LOGO_PROBE_START_DP = 48
private const val LOGO_PROBE_END_DP = 220
private const val LOGO_PROBE_STEP_DP = 24
private const val BANNER_APPEAR_MS = 800L
private const val GLOW_MS = 2_000L
