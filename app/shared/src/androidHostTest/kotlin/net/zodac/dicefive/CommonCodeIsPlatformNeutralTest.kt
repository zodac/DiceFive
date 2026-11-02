package net.zodac.dicefive

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The shared code's common source sets reach no platform API directly, so another target can be added back without
 * moving code (MULTIPLATFORM.md). Android is the one target, so the compiler lets common code see both Android's APIs
 * and the JDK's; this test is what keeps them out. A platform call goes behind an `expect` or `PlatformServices` instead.
 *
 * - Every import comes from a multiplatform library ([MULTIPLATFORM_IMPORTS]). A new multiplatform dependency adds its
 *   package there.
 * - None reaches the Android-only parts of those libraries ([ANDROID_ONLY_IMPORT]).
 * - No line names a platform API, imported or not ([PLATFORM_USE]).
 *
 * Comments are skipped: they may name a platform API.
 */
class CommonCodeIsPlatformNeutralTest {

    private val commonSources = listOf(File("src/commonMain"), File("src/commonTest"))

    @Test
    fun `no common code calls the JDK or Android directly`() {
        commonSources.forEach { assertTrue(it.isDirectory, "Run from app/shared: ${it.absolutePath}") }
        val offenders = commonSources.flatMap { root ->
            root.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .flatMap { file ->
                    file.readLines().mapIndexedNotNull { index, line ->
                        if (reachesPlatform(line)) "${file.path}:${index + 1}: ${line.trim()}" else null
                    }
                }
        }

        assertTrue(offenders.isEmpty(), "Platform APIs in common code - put them behind an expect:\n${offenders.joinToString("\n")}")
    }

    @Test
    fun `the check catches Android and JDK code and passes multiplatform code`() {
        listOf(
            "import android.content.Context",
            "import androidx.activity.compose.setContent",
            "import androidx.core.content.ContextCompat",
            "import androidx.compose.ui.platform.LocalContext",
            "import androidx.compose.ui.viewinterop.AndroidView",
            "import androidx.compose.ui.res.painterResource",
            "import androidx.compose.ui.graphics.nativeCanvas",
            "import androidx.compose.ui.test.junit4.createComposeRule",
            "import androidx.lifecycle.AndroidViewModel",
            "import androidx.datastore.preferences.preferencesDataStore",
            "import androidx.annotation.DrawableRes",
            "import java.util.Locale",
            "import org.junit.Test",
            "import org.robolectric.annotation.Config",
            "import kotlin.io.path.Path",
            "import com.example.SomeLibrary",
            "    val context = LocalContext.current",
            "    drawContext.canvas.nativeCanvas.drawText(text, 0f, 0f, paint)",
            "    val name = context.getString(R.string.app_name)",
            "    android.util.Log.d(TAG, message)",
            "    val zone = java.util.TimeZone.getDefault()",
            "    val now = System.currentTimeMillis()",
            "    val text = String.format(\"%d\", count)",
            "    val type = Score::class.java",
            "    @Synchronized fun next() = counter++",
        ).forEach { assertTrue(reachesPlatform(it), "Not caught: $it") }

        listOf(
            "import androidx.compose.ui.Modifier",
            "import androidx.compose.ui.platform.LocalDensity",
            "import androidx.compose.ui.test.onNodeWithText",
            "import androidx.compose.ui.tooling.preview.Preview",
            "import androidx.annotation.VisibleForTesting",
            "import androidx.room.Database",
            "import androidx.lifecycle.viewModelScope",
            "import kotlin.test.assertEquals",
            "import kotlinx.coroutines.flow.StateFlow",
            "import net.zodac.dicefive.resources.Res",
            "import org.jetbrains.compose.resources.stringResource",
            "import okio.Buffer",
            "    val text = stringResource(Res.string.app_name) // not android.R.string",
            "     * Formatted as android.icu would, through an expect.",
            "    val inset = SystemBars.height",
        ).forEach { assertFalse(reachesPlatform(it), "Wrongly caught: $it") }
    }

    private companion object {
        /** The packages common code may import from: Kotlin's own, and the multiplatform libraries the module uses. */
        val MULTIPLATFORM_IMPORTS = listOf(
            "kotlin.",
            "kotlinx.coroutines.",
            "net.zodac.dicefive.",
            "androidx.annotation.",
            "androidx.compose.animation.",
            "androidx.compose.foundation.",
            "androidx.compose.material3.",
            "androidx.compose.runtime.",
            "androidx.compose.ui.",
            "androidx.datastore.",
            "androidx.lifecycle.",
            "androidx.navigation.",
            "androidx.navigationevent.",
            "androidx.room.",
            "androidx.sqlite.",
            "com.mikepenz.aboutlibraries.",
            "okio.",
            "org.jetbrains.compose.",
        )

        /** The Android-only (or JVM-only) parts of the packages above. */
        val ANDROID_ONLY_IMPORT = Regex(
            listOf(
                """^androidx\.compose\.ui\.(viewinterop|res|test\.junit4)\.""",
                """^androidx\.compose\.ui\.platform\.(LocalContext|LocalView|LocalConfiguration|ComposeView)\b""",
                """^androidx\.compose\.ui\.graphics\.(nativeCanvas|NativeCanvas|NativePaint|asAndroid\w*)\b""",
                """^androidx\.lifecycle\.(AndroidViewModel|ProcessLifecycleOwner)\b""",
                """^androidx\.datastore\.preferences\.preferencesDataStore""",
                """^androidx\.annotation\.(\w+Res|RequiresApi|RequiresPermission|ChecksSdkIntAtLeast)\b""",
                """^kotlin\.(io\.path|reflect\.(full|jvm)|concurrent\.(thread|timer|fixedRateTimer))\b""",
            ).joinToString("|"),
        )

        /** A JDK or Android API named in full or by its usual entry points, or a JVM-only idiom. */
        val PLATFORM_USE = Regex(
            listOf(
                """(?<![\w.])(java|javax)\.[a-z]+\.""",
                """(?<![\w.])android\.[a-zA-Z]""",
                """(?<![\w.])R\.(string|plurals|drawable|mipmap|raw|font|color|dimen|id|layout|xml|anim|style)\.""",
                """(?<![\w.])(LocalContext|LocalView|LocalConfiguration|AndroidView|AndroidViewModel|BuildConfig)\b""",
                """\b(nativeCanvas|asAndroid[A-Z]\w*)\b""",
                """(?<![\w.])(System|Thread|Runtime)\.""",
                """String\.format\(""",
                """@Synchronized|(?<![\w.])synchronized\(""",
                """::class\.java\b""",
            ).joinToString("|"),
        )

        fun reachesPlatform(line: String): Boolean {
            val trimmed = line.trim()
            if (trimmed.startsWith("*") || trimmed.startsWith("/*")) {
                return false
            }
            val code = line.substringBefore("//")
            if (PLATFORM_USE.containsMatchIn(code)) {
                return true
            }
            if (!trimmed.startsWith("import ")) {
                return false
            }
            val imported = trimmed.removePrefix("import ").substringBefore(" as ").trim()
            return MULTIPLATFORM_IMPORTS.none { imported.startsWith(it) } || ANDROID_ONLY_IMPORT.containsMatchIn(imported)
        }
    }
}
