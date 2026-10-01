pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "DiceFive"
// All the code lives under app/: app/shared is the platform-neutral Kotlin Multiplatform library (game,
// UI, persistence), app/android the Android app that packages it. They have to be two modules - AGP 9
// won't let one module be both Kotlin Multiplatform and an Android application - but they share one
// home. app/ios (the Xcode project, still to come) will sit beside them. See .claude/IOS_SUPPORT.md.
include(":app:shared")
include(":app:android")
include(":app:baselineprofile")
