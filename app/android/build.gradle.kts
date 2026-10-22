import com.android.build.api.variant.impl.VariantOutputImpl
import net.zodac.dicefive.buildlogic.LicencePolicy
import net.zodac.dicefive.buildlogic.MinutesSinceEpoch
import net.zodac.dicefive.buildlogic.appVersion
import net.zodac.dicefive.buildlogic.tasks.CollectThirdPartyNoticesTask
import net.zodac.dicefive.buildlogic.tasks.NormalizeOggAudioTask
import net.zodac.dicefive.buildlogic.tasks.VerifyAssetSourcesTask
import net.zodac.dicefive.buildlogic.tasks.VerifyLicenseReportTask

plugins {
    // SDK levels, Java version, and the strict compiler and lint rules: build-logic's AndroidApplicationConventionPlugin.
    // No org.jetbrains.kotlin.android: AGP 9 compiles Kotlin itself ("built-in Kotlin").
    id("dicefive.android.application")
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.aboutlibraries.android)
    alias(libs.plugins.androidx.baselineprofile)
}

// Single source of truth for the app version - bump the repo root's VERSION file to release a new one.
val appVersionName = appVersion.name
val appVersionCode = appVersion.code

// Release signing is optional locally (assembleRelease then produces an unsigned APK) but
// required in CI, which supplies these via secrets - see .github/workflows/android-release.yml.
val releaseKeystorePath = System.getenv("ANDROID_RELEASE_KEYSTORE_PATH")
val releaseKeystorePassword = System.getenv("ANDROID_RELEASE_KEYSTORE_PASSWORD")
val releaseKeyAlias = System.getenv("ANDROID_RELEASE_KEY_ALIAS")
val releaseKeyPassword = System.getenv("ANDROID_RELEASE_KEY_PASSWORD")
// The secret the github flavour seals progress files with (online/ProgressFileCodec), as 64 hex digits: CI's
// PROGRESS_FILE_KEY secret, which never appears in the source, so a file can't be rewritten with the key read from
// GitHub. Permanent: another key can't read the files made with this one. A build without it (a local or debug build)
// uses this all-zero key instead, which can't read a release's files - android-release.yml refuses to release with it.
val progressFileKey: String = System.getenv("PROGRESS_FILE_KEY")?.takeIf { it.isNotBlank() }?.trim()?.lowercase()
    ?.also { require(Regex("[0-9a-f]{64}").matches(it)) { "PROGRESS_FILE_KEY must be 64 hex digits" } }
    ?: "0".repeat(64)
val hasReleaseSigningConfig = !releaseKeystorePath.isNullOrBlank() &&
    !releaseKeystorePassword.isNullOrBlank() &&
    !releaseKeyAlias.isNullOrBlank() &&
    !releaseKeyPassword.isNullOrBlank()

android {
    namespace = "net.zodac.dicefive"
    // compileSdk, buildToolsVersion and ndkVersion: gradle/android-sdk.properties, through build-logic.

    // Output names start DiceFive- instead of app- (the APKs' are set in full below).
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
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // The launcher's name (the manifest's android:label): the translated app_name, but see the debug build type.
        manifestPlaceholders["appLabel"] = "@string/app_name"
    }

    // Two apps from one source, one per way the app reaches players (.claude/DESIGN.md, "Distribution"):
    // - play: the App Bundle for Google Play, with Google Play Games (src/play) and no progress files;
    // - github: the APK on GitHub releases, a separate app (its own applicationId, so no data of its own can reach the
    //   play app's) with progress files (src/github) and no Play Games SDK at all.
    flavorDimensions += "distribution"
    productFlavors {
        create("play") {
            dimension = "distribution"
        }
        create("github") {
            dimension = "distribution"
            applicationIdSuffix = ".github"
            buildConfigField("String", "PROGRESS_FILE_KEY", "\"$progressFileKey\"")
        }
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
        // "DiceFive Debug" on the launcher, in every language, so a tester's debug build is never taken for a release.
        // Only the label: the GitHub APK and the Play bundle (release builds) keep app_name.
        debug {
            manifestPlaceholders["appLabel"] = "DiceFive Debug"
        }
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
            // The App Bundle carries the native libraries' symbol tables (BUNDLE-METADATA), so Play
            // symbolicates native crashes and ANRs without a separate upload.
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        // The native libraries (Compose's graphics-path, DataStore's shared counter) are stripped with the
        // NDK (ndkVersion, in gradle/android-sdk.properties). None may be listed in jniLibs.keepDebugSymbols:
        // AGP also leaves a kept library out of the symbol tables it extracts for Play.
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

    // The strict rules are build-logic's (StrictChecks.kt).
    lint {
        // Most of the app's code - the game, its UI, its storage - lives in :app:shared, which has no
        // lint run of its own for its main code. Checking dependencies makes this module's lintDebug
        // (the one CI runs) analyse it too, under these same rules.
        checkDependencies = true
    }
}

// R8 lays the release dex out from the startup profile (startup-prof.txt, StartupProfileGenerator's cold start to the
// menu), so the code a launch runs is loaded first, from the primary dex. The release workflow generates the profile
// for every APK; a build without one (a local release) is laid out as before.
baselineProfile {
    dexLayoutOptimization = true
}

// Ad hoc debug builds are handed straight to testers (not distributed via Play, and not tied to
// the VERSION-file release flow release/CI uses), so a debug APK built to install gets its own
// always-increasing versionCode: the minute it was built. Otherwise two debug builds sharing one VERSION-derived
// versionCode (the common case: nothing bumped VERSION between them) are logged by Android as the "same" version but
// signed identically, and re-signing per build is what actually determines whether an install-over-existing succeeds -
// the versionCode/name below just makes each one unambiguous.
//
// Only with `-PdevStamp` (the debug-apk and emulator skills pass it). Without it a debug build keeps VERSION's
// versionCode and "<version>-dev", so BuildConfig and the manifest stay the same from one build to the next and a test
// or lint run is up to date rather than recompiling the app and re-running every test each new minute.
//
// The minute is read when a build runs, through a ValueSource wired in lazily: a value worked out while the script is
// evaluated would be stored in the configuration cache and handed to every later build unchanged. The output-filename
// block below reuses the same provider, so the name and the versionCode always agree.
// Each lambda captures only a local value: the configuration cache can't store one holding the script or a variant.
val debugVersionCode: Provider<Int> = appVersionCode.let { code ->
    if (providers.gradleProperty("devStamp").isPresent) providers.of(MinutesSinceEpoch::class) {} else providers.provider { code }
}
val debugVersionName: Provider<String> = appVersionName.let { name ->
    if (providers.gradleProperty("devStamp").isPresent) debugVersionCode.map { "$name-dev$it" } else providers.provider { "$name-dev" }
}

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

// `-PgenerateStoreAssets` makes the store-asset generators (src/test/.../store/) render the Play Store listing's icon,
// feature graphic, screenshots and video frames - scripts/generate-store-assets.sh runs them and finishes the job (see
// .claude/STORE_ASSETS.md). They check nothing, so they're skipped otherwise rather than costing every test run - CI's
// included - minutes of whole-app screens. `-PstoreAssetParts=icon,phone,...` picks parts; `-PstoreAssetsDir` is where
// the raw renders go (build/store-assets/raw by default).
tasks.withType<Test>().configureEach {
    systemProperty("dicefive.generateStoreAssets", providers.gradleProperty("generateStoreAssets").isPresent)
    systemProperty("dicefive.storeAssetParts", providers.gradleProperty("storeAssetParts").getOrElse(""))
    systemProperty("dicefive.storeAssetsDir", providers.gradleProperty("storeAssetsDir").getOrElse(""))
    // A generator run always runs: its output is outside Gradle's view, so an up-to-date skip would leave it stale.
    if (providers.gradleProperty("generateStoreAssets").isPresent) outputs.upToDateWhen { false }
    // `-PexportPlayConsole` makes PlayConsoleExportTest write the achievements sheet and icons for Play Console to the
    // gitignored assets/play-console/ folder (scripts/export-play-console.sh).
    systemProperty("dicefive.exportPlayConsole", providers.gradleProperty("exportPlayConsole").isPresent)
    systemProperty("dicefive.playConsoleDir", rootProject.file("assets/play-console").absolutePath)
    if (providers.gradleProperty("exportPlayConsole").isPresent) outputs.upToDateWhen { false }
    // The test classes run in two JVMs. More gain nothing: each builds its own Robolectric sandbox, and on a 16-core
    // machine 1, 2 and 3 took 29s, 27s and 28s, 4 took 30s and 8 took 38s. The generators and the export keep to one.
    val generating = providers.gradleProperty("generateStoreAssets").isPresent || providers.gradleProperty("exportPlayConsole").isPresent
    maxParallelForks = if (generating) 1 else 2
}

// Both flavours' debug checks under the names they had before there were flavours, so `./gradlew testDebugUnitTest
// lintDebug compileDebugAndroidTestKotlin` from the root (CI, the update script, the test skill's gate) still covers the
// whole app rather than quietly matching nothing here. One flavour's own task takes --tests: testPlayDebugUnitTest.
// lintRelease is both release builds' full lint: what only they have (src/release, src/playRelease, the release
// manifests), which lintDebug never sees and the release build's own lintVital only checks for fatal issues.
for (check in listOf("test%sDebugUnitTest", "lint%sDebug", "lint%sRelease", "compile%sDebugAndroidTestKotlin")) {
    tasks.register(check.format("")) {
        group = "verification"
        description = "Runs ${check.format("<Flavour>")} for every flavour."
        dependsOn(check.format("Play"), check.format("Github"))
    }
}

// ── Audio loudness normalisation ──────────────────────────────────────────────
// The sound effects' sources are app/audio/*.ogg. scripts/normalize-audio.sh brings every
// clip to the same peak level (its header says why) and writes the copies this module packages into a build/ dir,
// registered as an extra generated res source set below. Never src/main/res/raw/: that stays empty, so `git status`
// only ever shows edits to the original clips, never the machine-generated, gain-adjusted bytes in the APK.
val normalizeOggAudio = tasks.register<NormalizeOggAudioTask>("normalizeOggAudio") {
    group = "build"
    description = "Normalises app/audio/*.ogg to a common peak level into a generated res/raw/."
    sourceFiles.from(rootProject.fileTree("app/audio") { include("*.ogg") })
    script.set(rootProject.layout.projectDirectory.file("scripts/normalize-audio.sh"))
    outputDirectory.set(layout.buildDirectory.dir("generated/normalizedAudioRes"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.res?.addGeneratedSourceDirectory(normalizeOggAudio, NormalizeOggAudioTask::outputDirectory)
    }
}

// Bake the version into the output filename too, so a debug APK handed to a tester is
// self-describing (which build they're on) without needing to open Settings > App info:
// DiceFive-<version>-<flavour>-<buildType>[-unsigned].apk. The public variant API has no setter for the file
// name, so this goes through its implementation class, VariantOutputImpl (an internal AGP class, not a
// deprecated one - it replaces the deprecated android.applicationVariants API this used to use). If an
// AGP upgrade moves it, this is the line that stops compiling.
androidComponents {
    onVariants { variant ->
        val releaseName = appVersionName
        val versionLabel = if (variant.buildType == "debug") debugVersionName else providers.provider { releaseName }
        val unsignedSuffix = if (variant.buildType == "release" && !hasReleaseSigningConfig) "-unsigned" else ""
        val suffix = "-${variant.flavorName}-${variant.buildType}$unsignedSuffix.apk"
        variant.outputs.filterIsInstance<VariantOutputImpl>().forEach { output ->
            output.outputFileName.set(versionLabel.map { "DiceFive-$it$suffix" })
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
// too. This module is just the one that generates the Android report from them.
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
        // The copyleft guard: any license not allowed by build-logic's LicencePolicy fails the build (every variant, so
        // it's caught by the first assembleDebug, not at release time).
        strictMode = com.mikepenz.aboutlibraries.plugin.StrictMode.FAIL
        allowedLicenses.addAll(LicencePolicy.ALLOWED)
        allowedLicensesMap = LicencePolicy.ALLOWED_FOR
    }
}

val verifyAssetSources = tasks.register<VerifyAssetSourcesTask>("verifyAssetSources") {
    group = "verification"
    description = "Checks every bundled asset has a recorded source and license."
    assetFiles.from(fileTree("src") {
        include("*/res/**", "*/assets/**")
        exclude("*/res/values*/**")
    })
    // The sound effects.
    assetFiles.from(rootProject.fileTree("app/audio") { include("*.ogg") })
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
    // Google Play Games Services v2: sign-in, achievements, leaderboards and saved games (online/PlayGamesServices).
    // The play flavour's alone: the github APK has none of it.
    "playImplementation"(libs.play.services.games.v2)
    constraints {
        // play-services-base asks for androidx.fragment 1.1.0, which Play Console flags as an outdated SDK.
        "playImplementation"(libs.androidx.fragment)
    }

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
