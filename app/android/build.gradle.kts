import com.android.build.api.variant.impl.VariantOutputImpl
import org.gradle.process.ExecOperations
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.ByteArrayOutputStream
import java.util.Properties
import java.util.zip.ZipFile
import javax.inject.Inject

plugins {
    // No org.jetbrains.kotlin.android: AGP 9 compiles Kotlin itself ("built-in Kotlin").
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.aboutlibraries.android)
    alias(libs.plugins.androidx.baselineprofile)
}

// Single source of truth for the app version - bump the root VERSION file to release a new one.
val appVersionName = rootProject.file("VERSION").readText().trim()
val appVersionCode = appVersionName.split(".").map { it.toInt() }
    .let { (major, minor, patch) -> major * 10_000 + minor * 100 + patch }

// Single source of truth for the Java version: `toolchainVersion` in gradle/gradle-daemon-jvm.properties
// (written by `./gradlew updateDaemonJvm --jvm-version=N --jvm-vendor=ADOPTIUM`). Gradle runs itself on
// that JDK, compiles and tests with it (the toolchain below), and the app is compiled FOR it - and it
// downloads that JDK wherever it is missing (the foojay resolver in settings.gradle.kts), so a build no
// longer depends on which java is installed: this sandbox, CI and Android Studio all get the same one.
// The sandbox's JDK follows it as a head start (the update script's consistency check fails its run
// if they drift), and CI's setup-java reads it from that file at run time.
val javaVersion: Int = Properties()
    .apply { rootProject.file("gradle/gradle-daemon-jvm.properties").inputStream().use(::load) }
    .getProperty("toolchainVersion").trim().toInt()

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
    // Compiles against Android 37.2: minor SDK releases add APIs and are their own SDK platform
    // (android-37.2, pinned to match in sandbox/Dockerfile; the workflows read it from here via
    // .github/scripts/android_sdk_packages.sh). Only compileSdk has a minor - targetSdk/minSdk are
    // whole API levels, so targetSdk 37 already covers every 37.x.
    compileSdk = 37
    compileSdkMinor = 2
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

    // Which languages the app offers, for Android's per-app language setting (Android 13+): worked out from the languages
    // that have a res/values-xx folder. The app's text lives in :app:shared's composeResources, so each translation also
    // has a values-xx/strings.xml here with app_name, only to be listed. See .claude/I18N.md.
    androidResources {
        generateLocaleConfig = true
    }

    defaultConfig {
        applicationId = "net.zodac.dicefive"
        minSdk = 26
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // Explicit, repo-committed debug key (app/android/debug.keystore) rather than Android Gradle
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
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "r8-rules.pro"
            )
            if (hasReleaseSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(javaVersion)
        targetCompatibility = JavaVersion.toVersion(javaVersion)
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        // Prebuilt libraries (Compose's graphics-path, DataStore's shared counter) ship .so files with
        // no symbol table AGP can strip, so it warns on every build and packages them as they are.
        // Saying so here is the same outcome without the warning.
        jniLibs {
            keepDebugSymbols += setOf("**/libandroidx.graphics.path.so", "**/libdatastore_shared_counter.so")
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // A handful of AndroidX artifacts each bundle their own full copy of the same Apache
            // license text under their own package path - none of it is read at runtime. The license
            // still ships, once: Settings > Licences (see "Open-source licenses" below).
            excludes += "META-INF/androidx/**/LICENSE.txt"
            // ...and so do JetBrains' multiplatform wrappers of them (Compose Multiplatform, lifecycle).
            excludes += "META-INF/org/jetbrains/**/LICENSE.txt"
            // Kotlin's reflection metadata (only kotlin-reflect reads it, and the app doesn't use it)
            // and kotlinx-coroutines' debug-agent file (only a JVM debugger loads it).
            excludes += "kotlin/**.kotlin_builtins"
            excludes += "DebugProbesKt.bin"
        }
    }

    testOptions {
        // Robolectric UI tests (e.g. LicensesDialogTest) render real screens on the JVM, reading the
        // app's merged resources - including the generated aboutlibraries.json.
        unitTests.isIncludeAndroidResources = true
    }

    lint {
        // Deprecated and obsolete usages fail lint - and so CI, which runs it - rather than piling up
        // as warnings. The Kotlin compiler flag below does the same for deprecated APIs in code, and
        // gradle.properties' org.gradle.kotlin.dsl.allWarningsAsErrors for the build scripts.
        error += setOf("Deprecated", "ObsoleteSdkInt")
        // Any other warning fails lint too, for the same reason: nothing is left to pile up unread. Fix it,
        // or suppress it where it occurs with a comment saying why (CLAUDE.md's working agreements).
        warningsAsErrors = true
        // The exception: "a newer version of a library exists" goes off the day a release is published,
        // failing CI with no change on our side. update-dependencies.yml is what tracks new versions.
        disable += "GradleDependency"
        // Most of the app's code - the game, its UI, its storage - lives in :app:shared, which has no
        // lint run of its own for its main code. Checking dependencies makes this module's lintDebug
        // (the one CI runs) analyse it too, under these same rules.
        checkDependencies = true
    }
}

// The toolchain (the JDK that compiles Kotlin and Java and runs the unit tests) and the Kotlin
// counterpart of compileOptions above, all on javaVersion. (compilerOptions replaces
// android.kotlinOptions, which Kotlin 2.3+ rejects outright as deprecated.)
kotlin {
    jvmToolchain(javaVersion)
    compilerOptions {
        jvmTarget = JvmTarget.fromTarget(javaVersion.toString())
        // Any compiler warning fails the build (main, unit-test and instrumented-test code alike),
        // rather than piling up unread - a deprecated API, an unnecessary `!!`, a missing `@OptIn`,
        // and so on. A dependency bump that deprecates something the app uses therefore breaks the
        // build until the call is migrated (the update script holds that bump back).
        allWarningsAsErrors = true
    }
}

// Ad hoc debug builds are handed straight to testers (not distributed via Play, and not tied to
// the VERSION-file release flow release/CI uses), so give every debug build its own
// always-increasing versionCode. Otherwise two debug builds sharing one VERSION-derived versionCode
// (the common case: nothing bumped VERSION between them) are logged by Android as the "same"
// version but signed identically, and re-signing per build is what actually determines whether an
// install-over-existing succeeds - the versionCode/name below just makes each one unambiguous.
// Computed once at script-eval time (not inside onVariants) so the output-filename block below can
// reuse the exact same string without reading it back off the variant.
val debugVersionCode = (System.currentTimeMillis() / 60_000L).toInt()
val debugVersionName = "$appVersionName-dev$debugVersionCode"

androidComponents {
    // Release dex stored uncompressed in the APK (the default only from minSdk 28): Android then runs
    // it straight from the APK instead of extracting a second copy at install. Measured on the sandbox
    // emulator: 6.4MB installed -> 4.5MB; the APK file grows 2.5MB -> 4.4MB, which only a sideload
    // sees (Play compresses downloads itself). Not debug: its unminified dex would take the APK file
    // from 22MB to 70MB for every build handed to a tester.
    onVariants(selector().withBuildType("release")) { variant ->
        variant.packaging.dex.useLegacyPackaging.set(false)
    }
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
                    "app/android/build.gradle.kts) but could not be run - is it installed and on PATH? " +
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
// self-describing (which build they're on) without needing to open Settings > App info:
// DiceFive-<version>-<buildType>[-unsigned].apk. The public variant API has no setter for the file
// name, so this goes through its implementation class, VariantOutputImpl (an internal AGP class, not a
// deprecated one - it replaces the deprecated android.applicationVariants API this used to use). If an
// AGP upgrade moves it, this is the line that stops compiling.
androidComponents {
    onVariants { variant ->
        val versionLabel = if (variant.buildType == "debug") debugVersionName else appVersionName
        val unsignedSuffix = if (variant.buildType == "release" && !hasReleaseSigningConfig) "-unsigned" else ""
        variant.outputs.filterIsInstance<VariantOutputImpl>().forEach { output ->
            output.outputFileName.set("DiceFive-$versionLabel-${variant.buildType}$unsignedSuffix.apk")
        }
    }
}

// ── Open-source licenses ─────────────────────────────────────────────────────
// Settings > "Licences" lists every third-party library the app ships, with its license
// text - Apache-2.0 §4(a), MIT, BSD and the OFL all require that copy go out with the app. Nothing in
// that list is written by hand: the AboutLibraries Android plugin walks each variant's real runtime
// dependency graph at build time and writes it to a generated res/raw/aboutlibraries.json, so a new
// or bumped dependency shows up on the next build with no one having to remember to add it.
//
// Things that aren't Gradle dependencies (a bundled font, sound clips, artwork) can't be discovered
// that way, so each gets a hand-written entry under app/licensing/libraries/ instead - see the
// README there. That folder is the one place a new third-party asset has to be recorded.
// The hand-written licence records - asset sources, asset/library entries and license texts - live in
// app/licensing/, beside both modules rather than in this one: they cover app/shared's font and icons
// too, and an iOS build will need the same records. This module is just the one that generates the
// Android report from them.
val licensingDir: Directory = rootProject.layout.projectDirectory.dir("app/licensing")

aboutLibraries {
    // Never reach out to GitHub or SPDX at build time, so CI and the sandbox generate byte-identical
    // output. The catch: offline, the plugin knows each license's name but not its text - so every
    // allowed license's full text is committed under app/licensing/licenses/ instead, and
    // VerifyLicenseReportTask below fails the build if one is ever missing.
    offlineMode = true
    collect {
        configPath = licensingDir.asFile
        // BOMs only pin other artifacts' versions - no code or content of theirs ships in the APK.
        includePlatform = false
    }
    export {
        // Unused by the dialog, and "developers" would otherwise list individual people's names.
        excludeFields.addAll("developers", "funding", "scm", "organization")
    }
    license {
        // The copyleft guard: any license not listed here fails the build (every variant, so it's
        // caught by the first assembleDebug, not at release time). Only permissive licenses - those
        // that ask for attribution and nothing more - are allowed outright, because they place no
        // conditions on how this app itself is licensed. A copyleft (GPL, LGPL, AGPL, MPL, EPL,
        // CDDL...) or unrecognised license has to be looked at by a person before it can ship: add
        // it here only once its terms are understood - see .claude/PUBLISHING.md.
        strictMode = com.mikepenz.aboutlibraries.plugin.StrictMode.FAIL
        allowedLicenses.addAll("Apache-2.0", "MIT", "BSD-2-Clause", "BSD-3-Clause")
        // The SIL Open Font License is copyleft on the font alone (a modified Sora must stay OFL and
        // can't be sold by itself) and places no conditions on the app it's bundled in - so it's
        // allowed, but only for the fonts, not for any library that turns up under it later.
        allowedLicensesMap = mapOf(
            "OFL-1.1" to listOf("sora", "mathjax-fonts"),
            // The sound effects' Freesound sources (see app/licensing/asset-sources.json). CC0 asks for
            // nothing; CC-BY asks for a credit, a link to the source and license, and a note of any
            // changes - all in its libraries/ entry. Neither places any condition on the app itself.
            // (Never CC-BY-SA - adaptations must stay under it - nor anything NC, which rules out ads
            // and Pro; neither is allowed here.)
            "CC0-1.0" to listOf("freesound-588198", "freesound-695731", "freesound-185986", "freesound-596051"),
            "CC-BY-4.0" to listOf("freesound-140147"),
        )
    }
}

/**
 * The checks the plugin's own strict mode doesn't make, run over the aboutlibraries.json it just
 * generated for one variant:
 * - every license some library uses has its full text - offline, the plugin only knows its name
 *   (see offlineMode above), and a license is only honoured if its text ships with the app;
 * - every library under a license whose terms require reproducing the copyright notice itself
 *   (BSD, MIT, OFL) has one - the plugin never collects copyright lines, so each such library
 *   needs an entry in app/licensing/libraries/ whose description starts with it.
 */
abstract class VerifyLicenseReportTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val libraryDefinitions: RegularFileProperty

    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    @TaskAction
    fun verify() {
        @Suppress("UNCHECKED_CAST")
        val definitions = groovy.json.JsonSlurper().parse(libraryDefinitions.get().asFile) as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val libraries = definitions["libraries"] as List<Map<String, Any?>>
        @Suppress("UNCHECKED_CAST")
        val licenses = definitions["licenses"] as Map<String, Map<String, Any?>>

        val problems = mutableListOf<String>()
        for ((id, license) in licenses) {
            if ((license["content"] as String?).isNullOrBlank()) {
                problems += "License '$id' has no text - add app/licensing/licenses/$id.json (the SPDX text)."
            }
        }
        for (library in libraries) {
            val id = library["uniqueId"]
            @Suppress("UNCHECKED_CAST")
            val libraryLicenses = library["licenses"] as List<String>? ?: emptyList()
            if (libraryLicenses.isEmpty()) {
                problems += "'$id' declares no license at all - find out what it is before it ships."
            }
            val needsNotice = libraryLicenses.any { NOTICE_LICENSE_PREFIXES.any(it::startsWith) }
            val description = library["description"] as String? ?: ""
            if (needsNotice && !description.startsWith("Copyright")) {
                problems += "'$id' is ${libraryLicenses.joinToString()}, which requires its copyright notice - add " +
                    "app/licensing/libraries/<name>.json with \"uniqueId\": \"$id\" and a description starting " +
                    "with its \"Copyright ...\" line."
            }
        }
        if (problems.isNotEmpty()) {
            throw GradleException("Open-source license report is incomplete:\n - " + problems.joinToString("\n - "))
        }
        reportFile.get().asFile.writeText("${libraries.size} libraries, ${licenses.size} licenses: OK\n")
    }

    private companion object {
        val NOTICE_LICENSE_PREFIXES = listOf("BSD-", "MIT", "OFL-", "ISC", "CC-BY-")
    }
}

/**
 * Apache-2.0 §4(d): a dependency that ships a NOTICE file must have that notice reproduced by
 * anything redistributing it. AGP's default packaging drops every META-INF/NOTICE* from the APK, so
 * this pulls them out of the variant's runtime dependencies first, into a generated
 * res/raw/third_party_notices.json that the licenses dialog shows. Automatic, like the library list
 * itself - nothing to remember when a dependency with a NOTICE turns up.
 */
abstract class CollectThirdPartyNoticesTask : DefaultTask() {

    /** The runtime classpath's Java resources (for an AAR, its classes.jar - where a NOTICE lives). */
    @get:Internal
    abstract val javaResourceArtifacts: Property<ArtifactCollection>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val javaResourceFiles: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun collect() {
        val notices = sortedMapOf<String, String>()
        for (artifact in javaResourceArtifacts.get().artifacts) {
            val file = artifact.file
            if (!file.isFile) continue
            ZipFile(file).use { zip ->
                val text = zip.entries().asSequence()
                    .filter { !it.isDirectory && it.name.substringAfterLast('/').uppercase().startsWith("NOTICE") }
                    .joinToString("\n\n") { zip.getInputStream(it).bufferedReader().readText().trim() }
                if (text.isNotBlank()) {
                    notices[artifact.id.componentIdentifier.displayName] = text
                }
            }
        }
        val rawDir = outputDirectory.get().asFile.resolve("raw")
        rawDir.deleteRecursively()
        rawDir.mkdirs()
        rawDir.resolve("third_party_notices.json").writeText(groovy.json.JsonOutput.toJson(notices))
    }
}

/**
 * The asset half of the report: a font, a sound or an image isn't a Gradle dependency, so nothing can
 * discover its license - someone has to write down where it came from. This makes that impossible to
 * skip: every file under any of this module's source sets' res/ (bar values*, which is text and
 * config, not creative work), rawAudioSource/ and assets/, and under :app:shared's composeResources/ (the
 * shared UI's font and icons), must have a complete entry in app/licensing/asset-sources.json -
 * what it is, where it came from and its license. That applies to the app's own
 * artwork too (license LicenseRef-DiceFive-AllRightsReserved): "we made it" is a claim that still
 * needs its session or commit behind it, and its own copyright line. A third-party asset instead
 * names the libraries/ entry for each source it's made from - which carry the license, copyright/credit
 * and source URL, and are what the licenses dialog shows. An unlisted or incomplete asset fails the
 * build - unaccounted-for is treated as unlicensed until proven otherwise.
 */
abstract class VerifyAssetSourcesTask : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val assetFiles: ConfigurableFileCollection

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val sourcesManifest: RegularFileProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val librariesDirectory: DirectoryProperty

    /** Asset paths are recorded relative to this (the repository root), so the manifest reads the same everywhere. */
    @get:Internal
    abstract val rootDirectory: DirectoryProperty

    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    @TaskAction
    fun verify() {
        val rootDir = rootDirectory.get().asFile
        val manifestName = sourcesManifest.get().asFile.name
        @Suppress("UNCHECKED_CAST")
        val entries = (groovy.json.JsonSlurper().parse(sourcesManifest.get().asFile) as Map<String, Any?>)
            .filterKeys { !it.startsWith("_") }
        val libraries = librariesDirectory.get().asFile.listFiles { file -> file.extension == "json" }.orEmpty()
            .map { groovy.json.JsonSlurper().parse(it) as Map<*, *> }
            .associateBy { it["uniqueId"] }
        val assets = assetFiles.files.map { it.relativeTo(rootDir).invariantSeparatorsPath }.sorted()

        val problems = mutableListOf<String>()
        for (asset in assets) {
            if (asset !in entries) {
                problems += "$asset has no recorded source - add it to $manifestName (see app/licensing/README.md)."
            }
        }
        for ((path, value) in entries) {
            if (path !in assets) {
                problems += "$path is listed in $manifestName but no longer exists - remove its entry."
                continue
            }
            val entry = value as? Map<*, *> ?: emptyMap<String, Any?>()
            fun field(name: String) = (entry[name] as? String)?.takeIf { it.isNotBlank() }
            for (name in REQUIRED_FIELDS) {
                if (field(name) == null) problems += "$path has no \"$name\"."
            }
            val license = field("license") ?: continue
            val libraryIds = (entry["libraries"] as? List<*>).orEmpty().map { it.toString() }
            if (license == APP_LICENSE) {
                // The app's own work: its copyright line lives here, as nothing else records it.
                val copyright = field("copyright")
                if (copyright == null || !copyright.startsWith("Copyright")) {
                    problems += "$path is the app's own work - it needs a \"copyright\" starting \"Copyright\"."
                }
                if (libraryIds.isNotEmpty()) problems += "$path is the app's own work ($APP_LICENSE) but names libraries."
                continue
            }
            // Third-party: its copyright and credit live in the libraries/ entries the licenses dialog
            // shows - one per source (a remix of two recordings names both) - never duplicated here.
            if (field("copyright") != null) {
                problems += "$path is third-party - its copyright belongs in its libraries/ entries, not here."
            }
            if (libraryIds.isEmpty()) {
                problems += "$path is third-party ($license) - it needs \"libraries\": the libraries/ entry for " +
                    "each source it's made from, so the licenses dialog credits them."
            }
            for (libraryId in libraryIds) {
                val library = libraries[libraryId]
                when {
                    library == null -> problems += "$path points to library '$libraryId', which has no libraries/ entry."
                    license !in (library["licenses"] as? List<*>).orEmpty() ->
                        problems += "$path is $license, but libraries/ entry '$libraryId' doesn't list that license."
                    (library["description"] as? String)?.startsWith("Copyright") != true ->
                        problems += "libraries/ entry '$libraryId' (for $path) needs a description starting with its " +
                            "\"Copyright ...\" line (or \"Copyright waived ...\" for CC0)."
                    (library["website"] as? String).isNullOrBlank() ->
                        problems += "libraries/ entry '$libraryId' (for $path) needs a \"website\": where it came from."
                    library["tag"] !in ASSET_TAGS ->
                        problems += "libraries/ entry '$libraryId' (for $path) needs a \"tag\" - one of " +
                            "${ASSET_TAGS.joinToString()} - so the licences dialog doesn't call it a library."
                }
            }
        }
        if (problems.isNotEmpty()) {
            throw GradleException("Bundled assets without a complete source and license:\n - " + problems.joinToString("\n - "))
        }
        reportFile.get().asFile.writeText("${assets.size} assets: OK\n")
    }

    private companion object {
        val REQUIRED_FIELDS = listOf("description", "source", "license")

        /** The asset kinds LicensesDialog's ComponentKind knows how to name - keep the two in step. */
        val ASSET_TAGS = listOf("font", "sound", "image")

        /** The app's own work: proprietary, all rights reserved - see LICENSE at the repo root. */
        const val APP_LICENSE = "LicenseRef-DiceFive-AllRightsReserved"
    }
}

val verifyAssetSources = tasks.register<VerifyAssetSourcesTask>("verifyAssetSources") {
    group = "verification"
    description = "Checks every bundled asset has a recorded source and license."
    assetFiles.from(fileTree("src") {
        include("*/res/**", "*/rawAudioSource/**", "*/assets/**")
        exclude("*/res/values*/**")
    })
    // values*/ holds the shared UI's strings - the app's own text, as with res/values*/ above, not a
    // bundled asset with a source to record.
    assetFiles.from(project(":app:shared").fileTree("src") {
        include("*/composeResources/**")
        exclude("*/composeResources/values*/**")
    })
    sourcesManifest.set(licensingDir.file("asset-sources.json"))
    librariesDirectory.set(licensingDir.dir("libraries"))
    rootDirectory.set(rootProject.layout.projectDirectory)
    reportFile.set(layout.buildDirectory.file("reports/licenses/assets.txt"))
}

androidComponents {
    onVariants { variant ->
        val capitalized = variant.name.replaceFirstChar(Char::uppercase)
        tasks.matching { it.name == "generate${capitalized}Resources" }.configureEach { dependsOn(verifyAssetSources) }
        val verify = tasks.register<VerifyLicenseReportTask>("verifyLicenseReport$capitalized") {
            group = "verification"
            description = "Checks the ${variant.name} open-source license report is complete."
            dependsOn("prepareLibraryDefinitions$capitalized")
            libraryDefinitions.set(
                layout.buildDirectory.file("generated/aboutLibraries/${variant.name}/res/raw/aboutlibraries.json"),
            )
            reportFile.set(layout.buildDirectory.file("reports/licenses/${variant.name}.txt"))
        }
        // generate<Variant>Resources is on the path of every build of the variant - assemble, unit
        // tests, lint - so an incomplete report fails all of them, as strict mode does.
        tasks.matching { it.name == "generate${capitalized}Resources" }.configureEach { dependsOn(verify) }

        val javaRes = variant.runtimeConfiguration.incoming.artifactView {
            attributes.attribute(Attribute.of("artifactType", String::class.java), "android-java-res")
        }.artifacts
        val notices = tasks.register<CollectThirdPartyNoticesTask>("collectThirdPartyNotices$capitalized") {
            javaResourceArtifacts.set(javaRes)
            javaResourceFiles.from(javaRes.artifactFiles)
            outputDirectory.set(layout.buildDirectory.dir("generated/thirdPartyNotices/${variant.name}"))
        }
        variant.sources.res?.addGeneratedSourceDirectory(notices, CollectThirdPartyNoticesTask::outputDirectory)
    }
}

dependencies {
    // The game, its UI and its persistence - everything that isn't Android-specific.
    implementation(project(":app:shared"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    // Installs the Baseline Profile on the device at first launch (Compose already brings it, but the
    // generated profile is the app's own, so it is stated here), and is where the profile is generated from.
    implementation(libs.androidx.profileinstaller)
    "baselineProfile"(project(":app:baselineprofile"))
    // Compose UI and Material3 themselves arrive through :app:shared (as JetBrains' multiplatform
    // artifacts, which resolve to these same androidx ones on Android); the BOM keeps the test and
    // tooling artifacts below on matching versions.
    implementation(platform(libs.androidx.compose.bom))
    // AndroidAppContainer opens the shared module's database and preferences files.
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.datastore.preferences.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // org.json is part of the Android SDK, but unit tests run against a stub version of it (every
    // method throws) - this brings in a real implementation for JVM tests only. The app itself no
    // longer uses it, but AboutLibraries' Android parser (behind the Licences dialog) still does.
    testImplementation(libs.org.json)
    // Compose UI tests on the JVM (no device/emulator in the sandbox) - see LicensesDialogTest.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.junit)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.ui.test.junit4)
    testImplementation(libs.androidx.espresso.core)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.ui.test.junit4)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
