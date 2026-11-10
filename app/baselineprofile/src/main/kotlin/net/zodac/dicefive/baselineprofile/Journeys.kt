package net.zodac.dicefive.baselineprofile

import android.content.res.Resources
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
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
        device.findObjects(By.text(Pattern.compile(".+", Pattern.DOTALL))).joinToString(" | ") { it.text }.take(SCREEN_TEXT_CHARS)
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

/**
 * Taps what [find] returns until [done] says the tap took. A starved emulator drops taps - two in a row went with the
 * unlock banners animating at the foot of the screen - and the step after one then waits for what never comes. Tapped
 * again only after [TIMEOUT_MS] without [done]. The first tap is on [found] when the caller has just looked it up,
 * saving a lookup; later ones look again.
 *
 * Never tapped while [done] already holds, read once the screen is idle - before the first tap as before each retry.
 * [done] read straight after a tap can still see the screen from before it, so one that held already would pass before
 * the tap landed, and the tap could then undo it (a card or switch tapped again closes): a Statistics card the page had
 * opened was closed that way on a 7th lap. Before a retry, the wait for idle lets a slow tap land, so it isn't doubled.
 */
private fun MacrobenchmarkScope.tapUntil(what: String, find: () -> UiObject2, found: UiObject2? = null, done: () -> Boolean) {
    var next = found
    repeat(TAP_ATTEMPTS) {
        if (settledHolds(done)) return
        val target = next ?: find()
        next = null
        try {
            target.click()
        } catch (_: StaleObjectException) {
            // Re-composed between the lookup and the tap - look again.
            return@repeat
        }
        if (waitUntil(done)) {
            device.waitForIdle()
            return
        }
    }
    error("Baseline Profile journey: tapping '$what' never took (on screen: ${screenText()})")
}

/** Whether [done] holds once the screen has gone idle (the usual wait, not [withShortIdleWait]'s); gone stale, it doesn't yet. */
private fun MacrobenchmarkScope.settledHolds(done: () -> Boolean): Boolean {
    device.waitForIdle()
    return try {
        done()
    } catch (_: StaleObjectException) {
        false
    }
}

private fun MacrobenchmarkScope.tapTextUntil(text: String, done: () -> Boolean) = tapUntil(text, { await(By.text(text), text) }, done = done)

private fun MacrobenchmarkScope.tapDescUntil(description: String, done: () -> Boolean) =
    tapUntil(description, { await(By.desc(description), description) }, done = done)

/**
 * True once [done] is, within [TIMEOUT_MS]; a node gone stale while it's read counts as not yet. Read without waiting
 * for the screen to go idle (see [withShortIdleWait]): it is read again until it holds, and the tap that follows waits
 * for idle as usual.
 */
private fun waitUntil(done: () -> Boolean): Boolean {
    val deadline = System.currentTimeMillis() + TIMEOUT_MS
    while (System.currentTimeMillis() < deadline) {
        try {
            if (withShortIdleWait(done)) return true
        } catch (_: StaleObjectException) {
            // Re-composed while it was read - read it again.
        }
        Thread.sleep(POLL_MS)
    }
    return false
}

/**
 * Sets the switch, segment or chip around [label] to [checked], tapping until it says so. Already [checked], it isn't
 * tapped: a second tap would undo the first.
 */
private fun MacrobenchmarkScope.setChecked(label: BySelector, what: String, checked: Boolean) {
    val control = await(By.checkable(true).hasDescendant(label), what)
    if (control.isChecked == checked) return
    // Within the node just found, so without another wait for idle; gone stale, the tap looks it up afresh.
    val found = try {
        control.findObject(label)
    } catch (_: StaleObjectException) {
        null
    }
    tapUntil(what, { await(label, what) }, found) {
        device.hasObject(By.checkable(true).checked(checked).hasDescendant(label))
    }
}

/** Whether the switch around [text] is on. */
private fun MacrobenchmarkScope.isChecked(text: String): Boolean = await(By.checkable(true).hasDescendant(By.text(text)), text).isChecked

/**
 * Selects the tab [text], the topmost match (see [topmost]), tapping until it is selected; it isn't tapped if it
 * already is.
 */
internal fun MacrobenchmarkScope.selectTab(text: String) {
    val selected = By.selected(true).hasDescendant(By.text(text))
    await(By.text(text), text)
    if (device.hasObject(selected)) return
    tapUntil(text, { topmost(text) }) { device.hasObject(selected) }
}

/** The dice cup's description, which counts the turn's rolls left, or null without a cup on screen. */
private fun MacrobenchmarkScope.cupDescription(): String? = device.findObject(CUP)?.contentDescription

/**
 * Taps what [find] returns (first [found], if given) until the dice cup's description changes from [before]: a roll
 * uses one of the turn's rolls, and a score hands the next turn a full cup. A cup not found counts as unchanged: a
 * lookup that doesn't wait for idle can miss it mid-recomposition.
 */
private fun MacrobenchmarkScope.tapChangingCup(
    what: String,
    before: String? = await(CUP, "the dice cup").contentDescription,
    found: UiObject2? = null,
    find: () -> UiObject2,
) = tapUntil(what, find, found) { cupDescription().let { it != null && it != before } }

/** Rolls the dice: the cup, tapped until a roll has been used. The cup read for its rolls left is the one first tapped. */
private fun MacrobenchmarkScope.roll() {
    val cup = await(CUP, "the dice cup")
    tapChangingCup("the dice cup", cup.contentDescription, cup) { await(CUP, "the dice cup") }
}

/** Starts the game from the setup (or the tutorial from How to Play) with [label], tapped until the board is up. */
private fun MacrobenchmarkScope.startGame(label: String = "Start Game") = tapTextUntil(label) { device.hasObject(CUP) }

/** True if [text] is on screen right now, without waiting. */
internal fun MacrobenchmarkScope.hasText(text: String): Boolean = device.hasObject(By.text(text))

/** The menu's "DiceFive" wordmark (AppLogo): only the menu shows it, so its going says the menu has. */
private val MENU = By.text("DiceFive")
private val NOT_THOSE_DICE_BANNER = By.descStartsWith("Achievement earned: Not Those Dice!")
private val ANY_BANNER = By.desc(Pattern.compile("(Achievement earned|Styles? unlocked): .*", Pattern.DOTALL))

/**
 * Taps the menu's [label] button until the menu has gone. A tap can be dropped - one that lands while
 * a dialog's dim layer is still fading out over the menu ("Untrusted touch due to occlusion" in logcat),
 * or that a slow emulator doesn't take - and the steps after it would then run on the menu: their back
 * press leaves the app. Nor can the screen be told by its title, as the menu has a button of that name.
 */
internal fun MacrobenchmarkScope.openFromMenu(label: String) {
    await(By.text(label), label)
    repeat(CLOSE_ATTEMPTS) { attempt ->
        // Tapped again only if the menu is still up once the screen is idle: a slow tap may have opened the page late.
        if (attempt > 0 && settledHolds { !device.hasObject(MENU) }) return
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

/**
 * Back from a page opened from the menu, pressed again if the menu hasn't come back: a starved emulator can drop a back
 * press, and the next step would then look for the menu on that page. Pressed again only after [TIMEOUT_MS] without
 * the menu, and only if the menu is still not up once the screen is idle, so a slow press isn't doubled into one that
 * leaves the app.
 */
internal fun MacrobenchmarkScope.backToMenu() {
    repeat(CLOSE_ATTEMPTS) { attempt ->
        if (attempt > 0 && settledHolds { device.hasObject(MENU) }) return
        back()
        // The menu's wordmark found is the menu there, so it's looked for without waiting for idle.
        if (withShortIdleWait { device.wait(Until.hasObject(MENU), TIMEOUT_MS) }) return
    }
    error("Baseline Profile journey: back never returned to the menu (on screen: ${screenText()})")
}

/** Swipes up the middle of the screen [times] times - a long page or a dialog's text. */
internal fun MacrobenchmarkScope.scrollDown(times: Int) {
    val x = device.displayWidth / 2
    repeat(times) {
        device.swipe(x, device.displayHeight * 3 / 4, x, device.displayHeight / 4, 20)
        device.waitForIdle()
    }
}

/**
 * The topmost match for [text]. For a label that appears twice on a screen, such as a Rules tab
 * and the page heading or footer pill of the same name: the tab rows are above everything else.
 */
private fun MacrobenchmarkScope.topmost(text: String): UiObject2 {
    await(By.text(text), text)
    return device.findObjects(By.text(text)).minByOrNull { it.visibleBounds.top }
        ?: error("Baseline Profile journey: '$text' never appeared")
}

/**
 * Scrolls the page back to its top by its own accessibility scroll action, not a swipe: on a starved emulator a
 * swipe's moves arrive late and the tile under the finger takes them as a press, so swipes up left the Styles page where
 * it was. The page is the screen's ScrollView, the only one; it is scrolled a screenful at a time until it can go no
 * further.
 */
private fun MacrobenchmarkScope.scrollPageToTop() {
    repeat(PAGE_SCROLLS) {
        val root = InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow
        val page = findNode(root) { it.isScrollable && it.className == "android.widget.ScrollView" } ?: return
        if (!page.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) return
        device.waitForIdle()
    }
}

private fun findNode(node: AccessibilityNodeInfo?, match: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
    if (node == null) return null
    if (match(node)) return node
    for (child in 0 until node.childCount) findNode(node.getChild(child), match)?.let { return it }
    return null
}

/** From the menu: Play (or New Game, when a game is saved) -> Start Game, then back to the menu. */
internal fun MacrobenchmarkScope.playATurn() {
    openGameSetup()
    startGame()

    // Roll, hold a die, roll again, then score: the loop a player spends nearly all their time in.
    // Rolling animates, which waitForIdle can't see, so each roll is given a moment to settle. A dropped hold
    // costs nothing after it, so it alone isn't checked (nor could it be: held is said only to TalkBack).
    roll()
    Thread.sleep(ROLL_SETTLE_MS)
    tapDesc("Die 1", startsWith = true)
    roll()
    Thread.sleep(ROLL_SETTLE_MS)
    tapChangingCup("Chance") { await(By.desc("Chance"), "Chance") }
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
 * roll there, then turned back. Each turn lays the board out again, so it's waited for after both.
 */
private fun MacrobenchmarkScope.rollInLandscape() {
    device.setOrientationLandscape()
    await(By.descStartsWith("Dice cup"), "the board in landscape")
    roll()
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
    // The wordmark, not a button lower down: a stack of banners from a game just finished can cover those for longer than a wait.
    await(By.text(Pattern.compile("Leave game\\?|DiceFive")), "the leave dialog or the menu after leaving a game")
    if (hasText("Leave game?")) {
        closeDialog(By.text("Leave"), "the leave dialog's Leave button")
    }
    await(MENU, "the menu after leaving a game")
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

/**
 * Settings, flipping one switch (and flipping it back), then its Graphics page, picking an animation level (and back),
 * and back to Settings to open the Licences and About dialogs.
 */
internal fun MacrobenchmarkScope.visitSettings() {
    openFromMenu("Settings")
    val sound = By.text("Sound effects")
    val soundOn = isChecked("Sound effects")
    setChecked(sound, "Sound effects", !soundOn)
    setChecked(sound, "Sound effects", soundOn)
    tapTextUntil("Graphics") { device.hasObject(By.text("Advanced settings")) }
    // Back to High: the journeys after this one are profiling the full animations.
    setChecked(By.text("Low"), "Low", true)
    setChecked(By.text("High"), "High", true)
    back()
    await(By.text("Sound effects"), "Settings after the Graphics page")

    // Each dialog is waited for by its OK button and left by it: the Settings page behind is
    // itself scrollable and has the same "Licences" text, so neither a scrollable nor a label says
    // the dialog is up, and a back press sent too early would leave Settings altogether.
    tapTextUntil("Licences") { device.hasObject(By.text("OK")) }
    scrollDown(3)
    closeDialog(By.text("OK"), "the Licences dialog's OK button")

    tapTextUntil("About") { device.hasObject(By.text("OK")) }
    scrollDown(1)
    closeDialog(By.text("OK"), "the About dialog's OK button")

    backToMenu()
}

/**
 * Wipes the app's data, as on a fresh install: the only way a release build can lock an achievement again, and a run
 * retried on the same emulator (android-release.yml) finds the last run's data otherwise. Stops the app, if it's running.
 */
internal fun MacrobenchmarkScope.wipeAppData() {
    val result = device.executeShellCommand("pm clear $PACKAGE").trim()
    check(result == "Success") { "Baseline Profile journey: clearing the app's data said '$result'" }
}

/**
 * Taps the logo's dice on the menu (the "Not Those Dice!" easter egg) to raise an unlock banner, taps the
 * banner to pause its countdown, then long-presses it, which takes the player to that achievement on the
 * Achievements screen, where its row glows. Needs the achievement locked, so only the lap that starts from
 * [wipeAppData] can run it.
 *
 * The dice are drawn with a raw tap gesture and have no label to find them by, so they are found
 * from the wordmark under them: AppLogo puts "DiceFive" 16dp below the row of dice (each 34dp tall),
 * so the row's middle is about 33dp above the wordmark's top. A few nearby heights are tried in case
 * the fan sits a little off that. Every one of them is above the wordmark, well clear of the buttons -
 * and the run fails the moment a tap leaves the menu, rather than carrying on tapping elsewhere.
 *
 * The banners are kept up for [BANNER_TIMEOUT_MS] by the system's "Time to take action" setting, which the app's
 * banners follow: their own four seconds were often gone before a hosted runner's emulator had found one to press.
 * Any still up afterwards are swiped away, so they don't cover the rest of the journey.
 */
internal fun MacrobenchmarkScope.visitAchievementBanner() {
    withTimeToTakeAction(BANNER_TIMEOUT_MS) { pressAchievementBanner() }
    clearBanners()
}

/** [visitAchievementBanner]'s attempts, each raising the banner and pressing it. */
private fun MacrobenchmarkScope.pressAchievementBanner() {
    for (attempt in 1..BANNER_ATTEMPTS) {
        // A banner that was raised but missed can't be raised again until the achievement is locked. Any banners still
        // up are swiped away first: one the tap paused stays over the menu's buttons until it's tapped again. This runs
        // before the lap has done anything else, so wiping the data loses nothing.
        if (attempt > 1) {
            clearBanners()
            wipeAppData()
            pressHome()
            startActivityAndWait()
        }
        val bannerBounds = withShortIdleWait { raiseBanner() } ?: continue
        // A tap pauses the banner's countdown until the next tap, so the long-press isn't racing it. One that came
        // after the banner had gone pauses the styles banner moved up in its place (the egg unlocks a style), or lands
        // on the menu - on a button, which opens its page: back, and the banner is tried again.
        device.click(bannerBounds.centerX(), bannerBounds.centerY())
        if (!device.hasObject(NOT_THOSE_DICE_BANNER)) {
            if (device.wait(Until.gone(MENU), MENU_GONE_MS)) {
                back()
                await(MENU, "the menu after the banner's tap opened another page")
            }
            continue
        }
        longPressAt(bannerBounds)

        // Lands on the Achievements screen - told by the menu going, as the menu has an Achievements
        // button too; if it stays, the press missed, and the banner is tried again. Then the row's gold
        // flash is given time to play out before leaving.
        if (!device.wait(Until.gone(MENU), TIMEOUT_MS)) continue
        await(By.text("Achievements"), "the Achievements screen after the banner's long-press")
        Thread.sleep(GLOW_MS)
        backToMenu()
        return
    }
    error("Baseline Profile journey: tapping the logo's dice never raised a 'Not Those Dice!' banner it could press")
}

/**
 * Runs [block] with the system's "Time to take action" accessibility setting at [millis], then puts back what it was
 * (unset, on a fresh emulator). Settings sets both of its keys to the one choice, and so does this.
 */
private inline fun MacrobenchmarkScope.withTimeToTakeAction(millis: Long, block: () -> Unit) {
    val saved = TIME_TO_TAKE_ACTION_KEYS.associateWith { device.executeShellCommand("settings get secure $it").trim() }
    TIME_TO_TAKE_ACTION_KEYS.forEach { device.executeShellCommand("settings put secure $it $millis") }
    try {
        block()
    } finally {
        saved.forEach { (key, value) ->
            device.executeShellCommand(if (value == "null" || value.isEmpty()) "settings delete secure $key" else "settings put secure $key $value")
        }
    }
}

/**
 * Swipes away the banners on screen, front first, whether counting down or paused. On a starved emulator a swipe's
 * moves can arrive after the long-press timeout, and the banner takes it as a long press, opening Achievements or
 * Styles: back to the menu, and the banner is swiped again.
 */
private fun MacrobenchmarkScope.clearBanners() {
    repeat(MAX_BANNERS) {
        val bounds = try {
            device.findObject(ANY_BANNER)?.visibleBounds ?: return
        } catch (_: StaleObjectException) {
            return@repeat
        }
        // Across most of its width: a swipe of 15% dismisses it. The front banner covers all but the top of those
        // behind.
        val inset = bounds.width() / 8
        device.swipe(bounds.left + inset, bounds.centerY(), bounds.right - inset, bounds.centerY(), SWIPE_STEPS)
        if (device.wait(Until.gone(MENU), BANNER_LEAVE_MS)) {
            back()
            await(MENU, "the menu after a banner's swipe was taken as a long press")
        }
    }
}

/**
 * Taps the logo's dice (see [visitAchievementBanner]) until the banner appears, and returns where it is,
 * or null if no tap raised it - or it was gone again before it could be found.
 */
private fun MacrobenchmarkScope.raiseBanner(): Rect? {
    val density = Resources.getSystem().displayMetrics.density
    val wordmarkTop = await(MENU, "the menu's DiceFive wordmark").visibleBounds.top
    val x = device.displayWidth / 2

    for (heightDp in DICE_PROBE_HEIGHTS_DP) {
        device.click(x, wordmarkTop - (heightDp * density).toInt())
        val node = device.wait(Until.findObject(NOT_THOSE_DICE_BANNER), BANNER_APPEAR_MS)
        if (node != null) return boundsOf(node, NOT_THOSE_DICE_BANNER)
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
        startGame()
        roll()
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
    startGame()
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
    setPlayers(1)
    startGame()
    playToGameOver(rollsPerTurn = STANDARD_ROLLS)
    toMainMenu()
    openGameSetup()
    setPlayers(2)
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
    setPlayers(3)
    setThirdSeatCpu(true)
    setOneRollPerTurn(true)
    chooseMode("Quickfire")
    startGame()
    playToGameOver(rollsPerTurn = 1)
    // The fireworks are given a moment to start: the profile records the code that ran, not how long they played
    // (they run for GameOverScreen's CELEBRATION_MILLIS, as long as the fanfare).
    Thread.sleep(CELEBRATION_MS)
    tapTextUntil("Review Scorecards") { device.hasObject(By.text("Scorecards")) }
    // Another player's tab: the first's name, one row, three quarters of the screen to the right (a name can be anything).
    val firstTab = await(By.text("Player 1"), "the review's first player").visibleBounds
    device.click(device.displayWidth * 3 / 4, firstTab.centerY())
    device.waitForIdle()
    back()
    await(GAME_OVER, "the Game Over page after the review")
    toMainMenu()

    openGameSetup()
    setThirdSeatCpu(false)
    setPlayers(2)
    setOneRollPerTurn(false)
    chooseMode("Standard")
    awaitModePickerClosed(replacedMode = "Quickfire")
    saveSetup()
}

/**
 * On the setup: the third seat a CPU (on Easy), or a user. Its chip - the lowest "Player" or "CPU" on the page -
 * names what the seat is now, and a tap turns it into the other, so it is only tapped when it isn't what's
 * wanted (the setup keeps its picks from lap to lap).
 */
private fun MacrobenchmarkScope.setThirdSeatCpu(cpu: Boolean) {
    val chip = By.text(Pattern.compile("Player|CPU"))
    fun lowestChip(): UiObject2 =
        device.findObjects(chip).maxByOrNull { it.visibleBounds.top }
            ?: error("Baseline Profile journey: the third seat's Player or CPU chip never appeared")
    await(chip, "the seats' Player or CPU chips")
    if ((lowestChip().text == "CPU") != cpu) tapUntil("the third seat's chip", ::lowestChip) { (lowestChip().text == "CPU") == cpu }
    if (cpu) tapText("Easy")
}

/** On the setup: [count] players, its segment tapped until it is the one picked. */
private fun MacrobenchmarkScope.setPlayers(count: Int) = setChecked(By.desc("$count"), "$count players", true)

/** From the Game Over page: Main Menu, tapped until the menu is up. */
private fun MacrobenchmarkScope.toMainMenu() = tapTextUntil("Main Menu") { device.hasObject(MENU) }

/** On the setup: the Number of Rolls modifier on at one roll a turn, or off. */
private fun MacrobenchmarkScope.setOneRollPerTurn(on: Boolean) {
    val decrease = By.desc("Decrease rolls per turn")
    // Tapped until the modifiers page is up: a tap can be dropped while a dialog's dim layer fades.
    val rolls = By.text("Number of Rolls")
    repeat(CLOSE_ATTEMPTS) { attempt ->
        // Tapped again only if the page isn't up once the screen is idle: a slow tap may have opened it late.
        if (attempt == 0 || !settledHolds { device.hasObject(rolls) }) device.findObject(By.desc("Modifiers"))?.click()
        if (device.wait(Until.hasObject(rolls), CLOSE_WAIT_MS)) {
            if (device.hasObject(decrease) != on) tapTextUntil("Number of Rolls") { device.hasObject(decrease) == on }
            if (on) {
                // Down to the lowest, whatever it was left at: a tap at the lowest does nothing.
                repeat(2) { tapDesc("Decrease rolls per turn") }
            }
            tapTextUntil("Done") { device.hasObject(By.text("Start Game")) && !device.hasObject(By.text("Done")) }
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
    repeat(MAX_TURNS + DROPPED_TAP_TURNS) {
        // The cup is back at its full rolls when it is a user's turn - a CPU's moves go by in between. A box still
        // open to score says the last score's tap was dropped, and the turn is still the user's: it is played
        // again.
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
    // The picker's list is the only place another mode's name shows, so its going says the pick closed it. The list is
    // open once both names show: one alone can be there before the rest of the list, which would read as already closed.
    val otherMode = if (mode == "Standard") "Tricolour" else "Standard"
    tapDescUntil("Game Mode") { device.hasObject(By.text(mode)) && device.hasObject(By.text(otherMode)) }
    tapTextUntil(mode) { !device.hasObject(By.text(otherMode)) }
}

/**
 * A long page of text: the Rules, starting with a few turns of the tutorial from its How to Play page, then the
 * Modes group and one of its pages.
 */
internal fun MacrobenchmarkScope.visitRules() {
    openFromMenu("Rules")
    playTutorial()
    scrollDown(4)
    selectTab("Modes")
    selectTab("Tricolour")
    scrollDown(2)
    backToMenu()
}

/**
 * From the Rules' How to Play page: the tutorial, its first roll and the coach card's forward arrow a few turns on,
 * then left (it always asks), back to the page.
 */
private fun MacrobenchmarkScope.playTutorial() {
    // The Rules open on the page last shown while the app has been running, so How to Play is asked for.
    selectTab("Gameplay")
    selectTab("How to Play")
    val start = By.text("Play the Tutorial")
    // Under the page's text, which a short screen can push below the fold.
    if (!device.wait(Until.hasObject(start), CLOSE_WAIT_MS)) scrollDown(1)
    startGame("Play the Tutorial")
    roll()
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
 * Leaderboard, on Combined picked from its drop-down (every mode's rows, each coloured by mode) and back on Standard's,
 * and Statistics, with the first player's card opened, then on the Combined page (its mode table) and back on Standard's.
 */
internal fun MacrobenchmarkScope.visitScoreScreens() {
    openFromMenu("Leaderboard")
    pickFromDropDown("Leaderboard", "Combined")
    pickFromDropDown("Leaderboard", "Standard")
    backToMenu()
    openFromMenu("Statistics")
    // The page deals cards open (all of them when they fit), so this taps the card only if it came up closed.
    await(By.descStartsWith("Player 1."), "Player 1's card")
    // An opened card's numbers are spoken ("Played 3, won 1, ..."); a closed card's are silent. Player 1's card is
    // first by name, and the first card always opens, so it is tapped only if it came up closed.
    tapUntil("Player 1's card", { await(By.descStartsWith("Player 1."), "Player 1's card") }) { device.hasObject(By.descStartsWith("Played ")) }
    pickFromDropDown("Statistics", "Combined")
    pickFromDropDown("Statistics", "Standard")
    backToMenu()
}

/**
 * On the Leaderboard or Statistics (the drop-down [field] names): [option] picked from the drop-down, until the field says
 * it's the one on view. The menu is tapped open until its options show, then the option tapped; the menu can close again
 * before that tap lands (it did on a starved emulator's 10th lap), so the pair is tried again, never while [option] is
 * already picked.
 */
private fun MacrobenchmarkScope.pickFromDropDown(field: String, option: String) {
    val picked = By.desc("$field, $option")
    val choice = By.desc(option)
    repeat(TAP_ATTEMPTS) {
        if (settledHolds { device.hasObject(picked) }) return
        tapUntil("the $field drop-down", { await(By.descStartsWith("$field, "), "the $field drop-down") }) {
            device.hasObject(choice)
        }
        try {
            device.findObject(choice)?.click()
        } catch (_: StaleObjectException) {
            // Re-composed between the lookup and the tap - the menu is opened and the option looked up again.
        }
        device.wait(Until.hasObject(picked), TIMEOUT_MS)
    }
    if (!settledHolds { device.hasObject(picked) }) error("Baseline Profile journey: '$option' never picked on $field (on screen: ${screenText()})")
}

/**
 * The Achievements page: scrolled, then the "Unearned" switch on and off, and the Collection's Tally
 * long-pressed for the scores it still lacks (it wants 46 different scores on the Standard leaderboard, far more than a
 * run's few finished games of Standard can record, so it stays locked).
 */
internal fun MacrobenchmarkScope.visitAchievements() {
    openFromMenu("Achievements")
    scrollDown(2)
    // The switch's label is the filter in force: "All" turns to "Unearned" as the switch takes the tap, and back.
    tapTextUntil("All") { device.hasObject(By.text("Unearned")) }
    // The page opens where it was last left, which on the lap of visitAchievementBanner is that banner's row in Easter Eggs.
    hopToCategory("Collection")
    // A press can be dropped (the row's tooltip, a banner fading out over it), so it is repeated until the dialog is up.
    // The scores' line shares its text with the mode restriction above it ("Can only be earned in..."), so it is found
    // anywhere in that text.
    val missing = By.textContains("Scores not yet on the leaderboard")
    // Pressed again only if the dialog isn't up once the screen is idle: a slow press may have opened it late.
    for (pressed in 0 until CLOSE_ATTEMPTS) {
        if (pressed > 0 && settledHolds { device.hasObject(missing) }) break
        longPressAt(await(By.text("Tally"), "Tally's row").visibleBounds)
        if (device.wait(Until.hasObject(missing), CLOSE_WAIT_MS)) break
    }
    await(missing, "Tally's missing scores")
    closeDialog(By.text("OK"), "the missing scores' OK button")
    tapTextUntil("Unearned") { device.hasObject(By.text("All")) }
    backToMenu()
}

/**
 * On the Achievements page: hops a category at a time, by the arrows on the pinned header, until [category]'s header is
 * the one pinned. Each hop is tapped until the pinned header has changed - its glide over - so a hop never starts from
 * part way through the one before: from there, the arrow jumps relative to wherever the glide had got to.
 */
private fun MacrobenchmarkScope.hopToCategory(category: String) {
    val target = CATEGORIES.indexOf(category)
    repeat(CATEGORIES.size) {
        val pinned = pinnedCategory()
        if (pinned == category) return
        val arrow = if (CATEGORIES.indexOf(pinned) < target) "Next category" else "Previous category"
        tapUntil("$pinned's $arrow", { topmostDesc(arrow) }) { readPinnedCategory().let { it != null && it != pinned } }
    }
    error("Baseline Profile journey: the Achievements arrows never reached $category (on screen: ${screenText()})")
}

/** The category whose header is pinned at the top of the Achievements list, waiting for a [readPinnedCategory] that finds one. */
private fun MacrobenchmarkScope.pinnedCategory(): String {
    val deadline = System.currentTimeMillis() + TIMEOUT_MS
    while (System.currentTimeMillis() < deadline) {
        try {
            readPinnedCategory()?.let { return it }
        } catch (_: StaleObjectException) {
            // Re-composed while it was read - read it again.
        }
        Thread.sleep(POLL_MS)
    }
    error("Baseline Profile journey: no category's header on the Achievements page (on screen: ${screenText()})")
}

/**
 * The topmost header on screen, or null if a look finds none. A list being re-composed can show none for a moment, with
 * the header back on the next look, so a null says only "not yet".
 */
private fun MacrobenchmarkScope.readPinnedCategory(): String? =
    device.findObjects(CATEGORY_HEADER).minByOrNull { it.visibleBounds.top }?.contentDescription?.substringBefore(", ")

/** The topmost match for the description [description]: on the Achievements page, the pinned header's arrow. */
private fun MacrobenchmarkScope.topmostDesc(description: String): UiObject2 {
    await(By.desc(description), description)
    return device.findObjects(By.desc(description)).minByOrNull { it.visibleBounds.top }
        ?: error("Baseline Profile journey: '$description' never appeared")
}

/** The Achievements page's categories, in its order. */
private val CATEGORIES = listOf(
    "Milestones", "Dice feats", "Scoring", "Winning", "Game Modes", "Misfortune", "Miscellaneous", "Collection", "Easter Eggs",
)

/** A category's header, by its spoken name and count ("Collection, 0 of 10 earned"). */
private val CATEGORY_HEADER = By.desc(Pattern.compile("(${CATEGORIES.joinToString("|")}), \\d+ of \\d+ earned"))

/**
 * The Styles pages: the costliest to open the first time (BENCHMARKS.md, "The Styles page"), so every category's page is
 * opened from its field and scrolled down through, which composes each tile once.
 */
internal fun MacrobenchmarkScope.visitStyles() {
    openFromMenu("Styles")
    // Long enough on the Styles page for its warm-up of the category pages (StyleCategoriesWarmUp) to run to the end -
    // a tap on a field stops it - so its code is in the profile too.
    Thread.sleep(STYLES_WARM_UP_MS)
    for (category in STYLE_CATEGORIES) {
        // Each field is a button named for its category; its page has the category as its title instead.
        tapDesc(category)
        await(By.text(category), category)
        scrollDown(2)
        closeMisreadLockDialog()
        scrollPageToTop()
        closeMisreadLockDialog()
        back()
        await(By.desc(STYLE_CATEGORIES.last()), "the Styles page")
    }
    backToMenu()
}

// The Styles page's warm-up: 600ms to start, then about 45 passes of two frames each, with room for a slow emulator.
private const val STYLES_WARM_UP_MS = 6_000L

/** The Styles page's fields, by their spoken names. */
private val STYLE_CATEGORIES = listOf("Dice", "Dice Cup", "Mat", "Background", "Frame", "Boxes")

/**
 * Closes a locked style's dialog if a swipe opened it: on a starved emulator a swipe's moves can arrive after the
 * long-press timeout, and the tile under it takes it as a long press. The dialog's "OK" is the only one on the page.
 */
private fun MacrobenchmarkScope.closeMisreadLockDialog() {
    if (device.hasObject(LOCK_DIALOG_OK)) closeDialog(LOCK_DIALOG_OK, "a locked style's dialog")
}

private val LOCK_DIALOG_OK = By.text("OK")

private val CUP = By.descStartsWith("Dice cup")
private const val POLL_MS = 250L
/** Screenfuls [scrollPageToTop] scrolls back at most. */
private const val PAGE_SCROLLS = 12

private val GAME_OVER = By.text("Game Over")
private const val MODES_LOCKED = "Finish a game to unlock game modes"
private const val STANDARD_ROLLS = 3
private const val TUTORIAL_TURNS = 3
private val OPEN_BOX = By.clickable(true).desc(
    Pattern.compile("Ones|Twos|Threes|Fours|Fives|Sixes|3x|4x|5x|Full House|Small Straight|Large Straight|Chance"),
)
private const val SCREEN_TEXT_CHARS = 400
private const val MAX_TURNS = 14
// Turns repeated because a score's tap was dropped: a slow runner dropped two in one solo Standard game.
private const val DROPPED_TAP_TURNS = 6
private const val ROLL_ATTEMPTS = 12
private const val ROLL_WAIT_MS = 2_500L
private const val TURN_WAIT_MS = 60_000L
private const val CELEBRATION_MS = 1_000L
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
/** One of the "Time to take action" setting's own choices: far longer than finding and pressing a banner takes. */
private const val BANNER_TIMEOUT_MS = 30_000L
private val TIME_TO_TAKE_ACTION_KEYS = listOf("accessibility_interactive_ui_timeout_ms", "accessibility_non_interactive_ui_timeout_ms")
private const val MENU_GONE_MS = 3_000L
private const val MAX_BANNERS = 6
private const val SWIPE_STEPS = 10
/** A swiped banner's going, or long enough for a page a misread swipe opened to take the menu's place. */
private const val BANNER_LEAVE_MS = 1_500L
private const val SHORT_IDLE_WAIT_MS = 100L
