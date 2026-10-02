package net.zodac.dicefive.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records only what a cold start runs, up to the menu being on screen, for the startup profile
 * (`startup-prof.txt`): R8 lays the dex out so this code is loaded first. Kept apart from
 * [BaselineProfileGenerator], whose journey covers every screen - marked as startup too, it made the
 * startup profile the whole app, which leaves R8 nothing to put first. Stops at the menu, before
 * `StylesWarmUp` starts pre-drawing the Styles page in the menu's idle frames: that is after startup.
 * Generated with the rest by `./gradlew :app:android:generateBaselineProfile`.
 */
@RunWith(AndroidJUnit4::class)
class StartupProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = PACKAGE,
        maxIterations = intArgument("journeyLaps") ?: 15,
        stableIterations = intArgument("journeyLaps") ?: 3,
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
        await(By.text("DiceFive"), "the menu's DiceFive wordmark")
    }
}
