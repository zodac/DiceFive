package net.zodac.dicefive.baselineprofile

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Frame times while the Styles page opens and its rows are swiped for the first time - the cold
 * path the page's warm-up and prefetching are there to hide - with no ahead-of-time compilation
 * against the generated Baseline Profile. Run on a device with
 * `./gradlew :app:baselineprofile:connectedBenchmarkAndroidTest`; the number to compare is
 * `frameDurationCpuMs` P90/P99 (and `frameOverrunMs`, where negative means frames on time).
 */
@RunWith(AndroidJUnit4::class)
class StylesBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun stylesNoCompilation() = styles(CompilationMode.None())

    @Test
    fun stylesWithBaselineProfile() = styles(CompilationMode.Partial())

    private fun styles(mode: CompilationMode) = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = mode,
        startupMode = StartupMode.COLD,
        iterations = 10,
    ) {
        pressHome()
        startActivityAndWait()
        visitStyles()
    }
}
