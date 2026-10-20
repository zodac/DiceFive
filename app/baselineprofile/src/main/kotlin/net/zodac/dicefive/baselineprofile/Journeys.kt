package net.zodac.dicefive.baselineprofile

import android.content.res.Resources
import android.graphics.Rect
import java.io.File
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
        ?: error("Baseline Profile journey: '$what' never appeared (on screen: ${screenText()})")

/**
 * Saves the screen and its UI hierarchy as a lap fails, while the app still shows where it stuck: the library
 * force-stops it straight after, so a screenshot taken once Gradle has finished shows only the launcher. Written to
 * the run's additional test output, which Gradle pulls to `build/outputs/connected_android_test_additional_output`.
 * The screen is taken with the shell's `screencap`: UiAutomator's own screenshot comes back black on the emulator.
 * Best effort: a capture that fails must not hide the journey's own failure.
 */
internal fun MacrobenchmarkScope.captureFailure(lap: Int) {
    val dir = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let(::File) ?: return
    try {
        dir.mkdirs()
        device.executeShellCommand("screencap -p ${File(dir, "journey-failure-lap$lap.png").absolutePath}")
        device.dumpWindowHierarchy(File(dir, "journey-failure-lap$lap.xml"))
    } catch (_: Exception) {
        // Nothing saved - the failure's message still names what was on screen.
    }
}

/** The text on screen, for a failure's message: what the journey was looking at instead. */
private fun MacrobenchmarkScope.screenText(): String =
    try {
        device.findObjects(By.text(Pattern.compile(".+"))).joinToString(" | ") { it.text }.take(SCREEN_TEXT_CHARS)
    } catch (_: StaleObjectException) {
        "the screen was changing"
    }

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
    openGameSetup()
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

    // Back out, think better of it ("Cancel"), then leave for real.
    cancelLeaving()
    leaveGame()
}

/**
 * From the menu: Play (or New Game, when a game is saved), to the game's setup. Waited for, not just looked
 * for: the menu may still be drawing, and "Play" is only offered without a saved game.
 */
private fun MacrobenchmarkScope.openGameSetup() {
    await(By.text(Pattern.compile("Play|New Game")), "the menu's Play or New Game")
    openFromMenu(if (hasText("New Game")) "New Game" else "Play")
}

/**
 * From the menu, with a game saved: Continue, wait for the board, roll once in landscape, then back out. The tap is
 * tried again if the board doesn't come: the system drops a touch that lands while the last dialog's dim layer is
 * still fading out over the menu ("Untrusted touch due to occlusion" in logcat).
 */
internal fun MacrobenchmarkScope.resumeGame() {
    val board = By.descStartsWith("Dice cup")
    await(By.text("Continue"), "Continue")
    repeat(CLOSE_ATTEMPTS) {
        device.findObject(By.text("Continue"))?.click()
        if (device.wait(Until.hasObject(board), TIMEOUT_MS)) {
            device.waitForIdle()
            rollInLandscape()
            leaveGame()
            return
        }
    }
    error("Baseline Profile journey: 'the board after Continue' never appeared")
}

/**
 * On the board: turned to landscape (the app follows the device unless its Orientation setting says otherwise), a
 * roll there, then turned back. Each turn recreates the activity, so the board is waited for after both.
 */
private fun MacrobenchmarkScope.rollInLandscape() {
    device.setOrientationLandscape()
    await(By.descStartsWith("Dice cup"), "the board in landscape")
    tapDesc("Dice cup", startsWith = true)
    Thread.sleep(ROLL_SETTLE_MS)
    device.setOrientationNatural()
    await(By.descStartsWith("Dice cup"), "the board back in portrait")
    device.waitForIdle()
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

/** Back press in a game, then "Cancel" on the leave dialog (if the setting asks): the game carries on. */
private fun MacrobenchmarkScope.cancelLeaving() {
    back()
    await(By.text(Pattern.compile("Leave game\\?|Settings")), "the leave dialog or the menu after a back press")
    if (hasText("Leave game?")) {
        closeDialog(By.text("Cancel"), "the leave dialog's Cancel button")
        await(By.descStartsWith("Dice cup"), "the board after cancelling the leave dialog")
    }
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
 * From the menu, with a game saved (see [playATurn]): a New Game in each of [OTHER_MODES], with a roll, then
 * out again. Afterwards the mode is put back to Standard and kept ([saveSetup]), so the game [resumeGame] goes back to
 * is Standard, and the next lap's [playATurn] - and anything else that starts a game - gets the mode it expects (the
 * setup starts from the last game's picks).
 */
internal fun MacrobenchmarkScope.playAModeGame() {
    for (mode in OTHER_MODES) {
        openFromMenu("New Game")
        chooseMode(mode)
        tapText("Start Game")
        tapDesc("Dice cup", startsWith = true)
        Thread.sleep(ROLL_SETTLE_MS)
        leaveGame()
    }

    openFromMenu("New Game")
    chooseMode("Standard")
    awaitModePickerClosed(replacedMode = OTHER_MODES.last())
    saveSetup()
}

/** The modes [playAModeGame] plays, each with its own dice, boxes or layout. */
private val OTHER_MODES = listOf("Tricolour", "D20", "Hit List")

/**
 * Waits for the mode picker's dialog to go after a pick: a tap or back press sent while it closes can be dropped or
 * leave the app (see closeDialog). Its list is the only place the mode just replaced shows ([replacedMode]), so that
 * going says it has closed; the pause after covers the rest of its fade.
 */
private fun MacrobenchmarkScope.awaitModePickerClosed(replacedMode: String) {
    await(By.text("Start Game"), "the setup after choosing a mode")
    device.wait(Until.gone(By.text(replacedMode)), TIMEOUT_MS)
    Thread.sleep(PICKER_CLOSE_MS)
}

/**
 * Keeps the setup's picks for the next game: the setup saves them only when a game is started, not when it is backed
 * out of, so a game is started with them and left (saved, for Continue).
 */
private fun MacrobenchmarkScope.saveSetup() {
    tapText("Start Game")
    await(By.descStartsWith("Dice cup"), "the board after starting a game to keep the setup")
    leaveGame()
}

/**
 * From the menu: unlocks the setup's game modes and modifiers, which stay locked until a game has been finished (as on
 * the fresh install CI runs on), by playing one solo game of Standard to the end. The player count is put back to two
 * and kept ([saveSetup]) afterwards. Does nothing more than look when they are already unlocked.
 */
internal fun MacrobenchmarkScope.unlockModes() {
    openGameSetup()
    await(By.text("Start Game"), "the setup")
    if (!hasText(MODES_LOCKED)) {
        back()
        await(By.text("Settings"), "the menu after leaving the setup")
        return
    }
    tapDesc("1")
    tapText("Start Game")
    playToGameOver(rollsPerTurn = STANDARD_ROLLS)
    tapText("Main Menu")
    openGameSetup()
    tapDesc("2")
    saveSetup()
}

/**
 * One whole game, played to the end - the part of the app nothing else reaches: the Game Over page with its
 * fireworks, the scorecards review, and a CPU taking its turns. Three players, two of them users and one an
 * Easy CPU (a user winning is what sets off the fireworks, and against an Easy CPU one nearly always does),
 * in Quickfire with one roll a turn, so it is only six turns a player. The setup remembers its picks, so
 * they are put back at the end.
 */
internal fun MacrobenchmarkScope.playFinishedGame() {
    openGameSetup()
    tapDesc("3")
    setThirdSeatCpu(true)
    setOneRollPerTurn(true)
    chooseMode("Quickfire")
    tapText("Start Game")
    playToGameOver(rollsPerTurn = 1)
    // The fireworks are given a moment to start: the profile records the code that ran, not how long they played
    // (they run for GameOverScreen's CELEBRATION_MILLIS, as long as the fanfare).
    Thread.sleep(CELEBRATION_MS)
    tapText("Review Scorecards")
    await(By.text("Scorecards"), "the scorecards review")
    // Another player's tab: the first's name, one row, three quarters of the screen to the right (a name can be anything).
    val firstTab = await(By.text("Player 1"), "the review's first player").visibleBounds
    device.click(device.displayWidth * 3 / 4, firstTab.centerY())
    device.waitForIdle()
    back()
    await(GAME_OVER, "the Game Over page after the review")
    tapText("Main Menu")

    openGameSetup()
    setThirdSeatCpu(false)
    tapDesc("2")
    setOneRollPerTurn(false)
    chooseMode("Standard")
    awaitModePickerClosed(replacedMode = "Quickfire")
    saveSetup()
}

/**
 * On the setup: the third seat a CPU (on Easy), or a user. Its chip - the lowest "User" or "CPU" on the page -
 * names what the seat is now, and a tap turns it into the other, so it is only tapped when it isn't what's
 * wanted (the setup keeps its picks from lap to lap).
 */
private fun MacrobenchmarkScope.setThirdSeatCpu(cpu: Boolean) {
    val chip = By.text(Pattern.compile("User|CPU"))
    fun lowestChip(): UiObject2 =
        device.findObjects(chip).maxByOrNull { it.visibleBounds.top }
            ?: error("Baseline Profile journey: the third seat's User or CPU chip never appeared")
    await(chip, "the seats' User or CPU chips")
    if ((lowestChip().text == "CPU") != cpu) tap("the third seat's chip") { lowestChip() }
    if (cpu) tapText("Easy")
}

/** On the setup: the Number of Rolls modifier on at one roll a turn, or off. */
private fun MacrobenchmarkScope.setOneRollPerTurn(on: Boolean) {
    val decrease = By.desc("Decrease rolls per turn")
    // Tapped until the modifiers page is up: a tap can be dropped while a dialog's dim layer fades.
    repeat(CLOSE_ATTEMPTS) {
        device.findObject(By.desc("Modifiers"))?.click()
        if (device.wait(Until.hasObject(By.text("Number of Rolls")), CLOSE_WAIT_MS)) {
            if (device.hasObject(decrease) != on) tapText("Number of Rolls")
            if (on) {
                // Down to the lowest, whatever it was left at: a tap at the lowest does nothing.
                repeat(2) { tapDesc("Decrease rolls per turn") }
            }
            tapText("Done")
            await(By.text("Start Game"), "the setup after the modifiers")
            return
        }
    }
    error("Baseline Profile journey: the modifiers page never opened")
}

/**
 * One user's turn. The cup already reads as ready while a CPU is still taking the turn before, and a tap on it
 * then does nothing - so it is tapped again until a roll leaves a box to score, which says the turn was the user's.
 * Returns early if the Game Over page comes instead.
 */
private fun MacrobenchmarkScope.rollAndScore() {
    repeat(ROLL_ATTEMPTS) {
        // The cup may not be there: a CPU's move can have been the game's last.
        if (device.hasObject(GAME_OVER)) return
        try {
            device.findObject(By.descStartsWith("Dice cup"))?.click()
        } catch (_: StaleObjectException) {
            // Re-composed between the lookup and the tap - look again.
        }
        // A box seen can be the CPU's, as it scores the turn before: gone by the tap, it means the user hasn't rolled yet.
        if (device.wait(Until.hasObject(OPEN_BOX), ROLL_WAIT_MS)) {
            try {
                device.findObject(OPEN_BOX)?.let { box ->
                    box.click()
                    device.waitForIdle()
                    return
                }
            } catch (_: StaleObjectException) {
                // Re-composed between the lookup and the tap - roll and look again.
            }
        }
    }
    error("Baseline Profile journey: rolling never left a box to score")
}

/**
 * Rolls once and scores the first box the roll can fill, turn after turn, for every player, until the Game
 * Over page is up. The boxes are found by name and by being tappable: a disabled or filled one isn't, and
 * which of Quickfire's are disabled is drawn at random. [rollsPerTurn] is what the cup holds at a turn's start.
 */
private fun MacrobenchmarkScope.playToGameOver(rollsPerTurn: Int) = withShortIdleWait {
    playTurnsToGameOver(By.descStartsWith("Dice cup, $rollsPerTurn roll"))
}

/** [playToGameOver]'s loop: everything in it waits for what it wants, so the dice's animations needn't be sat out as well. */
private fun MacrobenchmarkScope.playTurnsToGameOver(nextTurn: BySelector) {
    repeat(MAX_TURNS) {
        // The cup is back at its full rolls when it is a user's turn - a CPU's moves go by in between. A box still
        // open to score says the last score's tap was dropped, and the turn is still the user's.
        val deadline = System.currentTimeMillis() + TURN_WAIT_MS
        while (!device.hasObject(GAME_OVER) && !device.hasObject(OPEN_BOX) && !device.wait(Until.hasObject(nextTurn), 1_000L)) {
            check(System.currentTimeMillis() < deadline) { "Baseline Profile journey: no turn and no Game Over came" }
        }
        if (device.hasObject(GAME_OVER)) return
        rollAndScore()
    }
    await(GAME_OVER, "the Game Over page")
}

/**
 * Opens the setup's mode picker and picks [mode], which must not be the current one: the field shows
 * the current mode's name too, so that name could be found twice.
 */
private fun MacrobenchmarkScope.chooseMode(mode: String) {
    tapDesc("Game Mode")
    tapText(mode)
}

/**
 * A long page of text: the Rules, starting with a few turns of the tutorial from its How to Play page, then the
 * Modes group and one of its pages.
 */
internal fun MacrobenchmarkScope.visitRules() {
    openFromMenu("Rules")
    playTutorial()
    scrollDown(4)
    tapTopText("Modes")
    tapTopText("Tricolour")
    scrollDown(2)
    back()
}

/**
 * From the Rules' How to Play page: the tutorial, its first roll and the coach card's forward arrow a few turns on,
 * then left (it always asks), back to the page.
 */
private fun MacrobenchmarkScope.playTutorial() {
    // The Rules open on the page last shown while the app has been running, so How to Play is asked for.
    tapTopText("Gameplay")
    tapTopText("How to Play")
    val start = By.text("Start Tutorial")
    // Under the page's text, which a short screen can push below the fold.
    if (!device.wait(Until.hasObject(start), CLOSE_WAIT_MS)) scrollDown(1)
    tap("Start Tutorial") { await(start, "Start Tutorial") }
    tapDesc("Dice cup", startsWith = true)
    Thread.sleep(ROLL_SETTLE_MS)
    repeat(TUTORIAL_TURNS) {
        tapDesc("Next turn")
        Thread.sleep(ROLL_SETTLE_MS)
    }
    back()
    await(By.text("Leave the tutorial?"), "the tutorial's leave dialog")
    closeDialog(By.text("Leave"), "the tutorial's leave dialog's Leave button")
    await(start, "the How to Play page after the tutorial")
}

/**
 * Leaderboard, on its Game Mode table and back on Combined, and Statistics, with the first player's card opened on
 * its By mode view.
 */
internal fun MacrobenchmarkScope.visitScoreScreens() {
    openFromMenu("Leaderboard")
    tapDesc("Game Mode")
    tapDesc("Combined")
    back()
    openFromMenu("Statistics")
    tapDesc("Player 1.", startsWith = true)
    tapText("By mode")
    back()
}

/**
 * The Achievements page: scrolled, then the "Locked only" switch on and off, and the Collection's Tally
 * long-pressed for the scores it still lacks (the page has them locked, as [resetAchievements] left them, and
 * the Leaderboard has none of the scores it wants).
 */
internal fun MacrobenchmarkScope.visitAchievements() {
    openFromMenu("Achievements")
    scrollDown(2)
    tapText("Locked only")
    // Category by category until Tally's row is on screen.
    // The scroll to a category goes on after the tap has returned, so each hop is given a moment to show it.
    var hops = 0
    while (!device.wait(Until.hasObject(By.text("Tally")), HOP_WAIT_MS) && hops++ < CATEGORY_HOPS) tapDesc("Next category")
    // A press can be dropped (the row's tooltip, a banner fading out over it), so it is repeated until the dialog is up.
    val missing = By.textStartsWith("Scores not yet on the leaderboard")
    var pressed = 0
    do {
        longPressAt(await(By.text("Tally"), "Tally's row").visibleBounds)
    } while (!device.wait(Until.hasObject(missing), CLOSE_WAIT_MS) && ++pressed < CLOSE_ATTEMPTS)
    await(missing, "Tally's missing scores")
    closeDialog(By.text("Done"), "the missing scores' Done button")
    tapText("Locked only")
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

private val GAME_OVER = By.text("Game Over")
private const val MODES_LOCKED = "Finish a game to unlock game modes"
private const val STANDARD_ROLLS = 3
private const val TUTORIAL_TURNS = 3
private val OPEN_BOX = By.clickable(true).desc(
    Pattern.compile("Ones|Twos|Threes|Fours|Fives|Sixes|3x|4x|5x|Full House|Small Straight|Large Straight|Chance"),
)
private const val SCREEN_TEXT_CHARS = 400
private const val MAX_TURNS = 14
private const val ROLL_ATTEMPTS = 12
private const val ROLL_WAIT_MS = 2_500L
private const val TURN_WAIT_MS = 60_000L
private const val CELEBRATION_MS = 1_000L
private const val CATEGORY_HOPS = 15
private const val HOP_WAIT_MS = 800L
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
