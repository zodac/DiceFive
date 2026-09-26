import org.gradle.api.Action
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import javax.inject.Inject

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Single source of truth for the app version - bump the root VERSION file to release a new one.
val appVersionName = rootProject.file("VERSION").readText().trim()
val appVersionCode = appVersionName.split(".").map { it.toInt() }
    .let { (major, minor, patch) -> major * 10_000 + minor * 100 + patch }

// Release signing is optional locally (assembleRelease then produces an unsigned APK) but
// required in CI, which supplies these via secrets - see .github/workflows/release.yml.
val releaseKeystorePath = System.getenv("ANDROID_RELEASE_KEYSTORE_PATH")
val releaseKeystorePassword = System.getenv("ANDROID_RELEASE_KEYSTORE_PASSWORD")
val releaseKeyAlias = System.getenv("ANDROID_RELEASE_KEY_ALIAS")
val releaseKeyPassword = System.getenv("ANDROID_RELEASE_KEY_PASSWORD")
val hasReleaseSigningConfig = !releaseKeystorePath.isNullOrBlank() &&
    !releaseKeystorePassword.isNullOrBlank() &&
    !releaseKeyAlias.isNullOrBlank() &&
    !releaseKeyPassword.isNullOrBlank()

android {
    namespace = "net.zodac.dicefive"
    compileSdk = 35
    // Pinned to match the build-tools baked into the sandbox image (sandbox/Dockerfile) so a
    // build never needs to fetch a different version over the network. Bumped to 36.0.0 for the
    // AGP 9 upgrade - AGP 9.4.1 enforces build-tools >= 36.0.0 and silently ignores/overrides a
    // lower pin here rather than failing on it, so this now just documents what AGP would use
    // anyway instead of actually choosing it.
    buildToolsVersion = "36.0.0"

    // Output APK names: DiceFive-debug.apk / DiceFive-release.apk instead of the app-*.apk default.
    base {
        archivesName = "DiceFive"
    }

    defaultConfig {
        applicationId = "net.zodac.dicefive"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // Explicit, repo-committed debug key (app/debug.keystore) rather than Android Gradle
        // Plugin's default of auto-creating ~/.android/debug.keystore on first use. That default
        // lives outside the repo and outside any of this project's persisted caches, so a fresh
        // machine or throwaway build environment (a CI runner, a new sandbox container) generates
        // its OWN random debug key - silently breaking the "debug builds always signed identically"
        // assumption the versionCode comment below depends on: installing a debug build from a
        // different environment than the one already on the device fails with "conflicts with an
        // existing package" (a signature mismatch), even though both are just debug builds.
        // Standard AGP debug-key values (alias/passwords/DN) so this behaves exactly like the
        // default it replaces - only its storage location changes.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (hasReleaseSigningConfig) {
            create("release") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets {
        getByName("main").kotlin.srcDirs("src/main/kotlin")
        getByName("test").kotlin.srcDirs("src/test/kotlin")
        getByName("androidTest").kotlin.srcDirs("src/androidTest/kotlin")
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Ad hoc debug builds are handed straight to testers (not distributed via Play, and not tied to
// the VERSION-file release flow release/CI uses), so give every debug build its own
// always-increasing versionCode. Otherwise two debug builds sharing one VERSION-derived versionCode
// (the common case: nothing bumped VERSION between them) are logged by Android as the "same"
// version but signed identically, and re-signing per build is what actually determines whether an
// install-over-existing succeeds - the versionCode/name below just makes each one unambiguous.
// Computed once at script-eval time (not inside onVariants) so the applicationVariants block below
// can reuse the exact same string for the output filename without reading it back off the variant.
val debugVersionCode = (System.currentTimeMillis() / 60_000L).toInt()
val debugVersionName = "$appVersionName-dev$debugVersionCode"

androidComponents {
    onVariants(selector().withBuildType("debug")) { variant ->
        variant.outputs.forEach { output ->
            output.versionCode.set(debugVersionCode)
            output.versionName.set(debugVersionName)
        }
    }
}

// ── Audio loudness normalisation ──────────────────────────────────────────────
// The .ogg sound effects (dice cup, hold/unhold clicks, the win fanfare) come from different
// sources and were never mixed against each other, so they land at wildly different loudnesses -
// e.g. hold.ogg peaks 16dB quieter than celebration.ogg, which reads as "some sounds are broken"
// rather than "some sounds are naturally quieter." Rather than hand-normalising each file (which
// would need re-doing by hand every time one is re-recorded or swapped), this brings every clip to
// the same PEAK level as part of the build itself - see NormalizeOggAudioTask below - so the
// source clips in src/main/rawAudioSource/ can keep being replaced freely and the next build just
// levels whatever's there.
//
// Peak normalisation, not loudness/RMS normalisation: these are all short (150ms-3.3s) one-shot
// clicks/fanfares, well below the multi-second window ffmpeg's own loudnorm (EBU R128) needs for a
// stable measurement, so matching peaks is both simpler and more predictable here - and because
// none of the peaks moved out from under their natural transient, this needs no limiter/compressor
// to avoid clipping (the applied gain is chosen to land exactly on TARGET_PEAK_DB, never past it).
//
// Source files live in src/main/rawAudioSource/, NOT src/main/res/raw/ - the normalised output
// lands in a build/ dir instead (registered as an extra generated res source set below), so
// src/main/res/raw/ stays empty and `git status` only ever shows edits to the original clips,
// never the machine-generated, gain-adjusted bytes actually packaged into the APK.
abstract class NormalizeOggAudioTask @Inject constructor(
    private val execOperations: ExecOperations,
) : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val sourceFiles: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun normalize() {
        val rawDir = outputDirectory.get().asFile.resolve("raw")
        rawDir.deleteRecursively()
        rawDir.mkdirs()

        for (source in sourceFiles.files) {
            val peakDb = measurePeakDb(source)
            val gainDb = TARGET_PEAK_DB - peakDb
            runFfmpeg(
                "-y", "-i", source.absolutePath,
                "-af", "volume=${gainDb}dB,alimiter=limit=0.99",
                "-c:a", "libvorbis", "-q:a", "6",
                rawDir.resolve(source.name).absolutePath,
            )
        }
    }

    /** Reads ffmpeg's own `volumedetect` filter output rather than hand-rolling PCM analysis -
     * it already knows how to decode every format ffmpeg does, including this project's .ogg. */
    private fun measurePeakDb(file: File): Double {
        val stderr = runFfmpeg("-i", file.absolutePath, "-af", "volumedetect", "-f", "null", "-", ignoreExitValue = true)
        return Regex("""max_volume:\s*(-?[0-9.]+) dB""").find(stderr)?.groupValues?.get(1)?.toDouble()
            ?: throw GradleException("Could not read max_volume for ${file.name} from ffmpeg output:\n$stderr")
    }

    private fun runFfmpeg(vararg args: String, ignoreExitValue: Boolean = false): String {
        val stderr = ByteArrayOutputStream()
        try {
            execOperations.exec {
                commandLine(listOf("ffmpeg") + args)
                standardOutput = stderr
                errorOutput = stderr
                isIgnoreExitValue = ignoreExitValue
            }
        } catch (cause: Exception) {
            throw GradleException(
                "ffmpeg is required to normalise the .ogg audio assets (see NormalizeOggAudioTask in " +
                    "app/build.gradle.kts) but could not be run - is it installed and on PATH? " +
                    "(the project's own sandbox/Dockerfile installs it for exactly this)",
                cause,
            )
        }
        return stderr.toString()
    }

    private companion object {
        // Matches roughly where celebration.ogg (the loudest original clip) already peaked - every
        // other clip is brought UP to this, never down, so nothing gets quieter than it shipped.
        const val TARGET_PEAK_DB = -0.8
    }
}

val normalizeOggAudio = tasks.register<NormalizeOggAudioTask>("normalizeOggAudio") {
    group = "build"
    description = "Normalises src/main/rawAudioSource/*.ogg to a common peak level into a generated res/raw/."
    sourceFiles.from(fileTree("src/main/rawAudioSource") { include("*.ogg") })
    outputDirectory.set(layout.buildDirectory.dir("generated/normalizedAudioRes"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.res?.addGeneratedSourceDirectory(normalizeOggAudio, NormalizeOggAudioTask::outputDirectory)
    }
}

// Bake the version into the output filename too, so a debug APK handed to a tester is
// self-describing (which build they're on) without needing to open Settings > App info.
// Action<T> { ... } below is the Gradle Kotlin DSL's *receiver-style* SAM helper (`this` = T, no
// declared parameter) - not a plain SAM-converted lambda - because DomainObjectCollection.all(Action)
// otherwise loses overload resolution to Kotlin's Iterable<T>.all(predicate: (T) -> Boolean).
android.applicationVariants.all(
    Action<com.android.build.gradle.api.ApplicationVariant> {
        val variant = this
        val versionLabel = if (variant.buildType.name == "debug") debugVersionName else appVersionName
        val unsignedSuffix = if (variant.buildType.name == "release" && !hasReleaseSigningConfig) "-unsigned" else ""
        variant.outputs.all(
            Action<com.android.build.gradle.api.BaseVariantOutput> {
                val output = this
                if (output is com.android.build.gradle.api.ApkVariantOutput) {
                    output.outputFileName = "DiceFive-$versionLabel-${variant.buildType.name}$unsignedSuffix.apk"
                }
            },
        )
    },
)

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    // Just for Icons.AutoMirrored.Filled.Undo (see UndoButton.kt) - material3 alone only ships the
    // small default icon set, which doesn't include Undo.
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.junit)
    testImplementation(libs.androidx.room.runtime)
    testImplementation(libs.kotlinx.coroutines.test)
    // org.json is part of the Android SDK, but unit tests run against a stub version of it
    // (every method throws) - this brings in a real implementation for JVM tests only.
    testImplementation(libs.org.json)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.ui.test.junit4)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
