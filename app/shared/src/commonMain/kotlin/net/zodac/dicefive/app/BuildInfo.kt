package net.zodac.dicefive.app

/** Facts about this build of the app, supplied by the platform's own build (on Android, `BuildConfig`). */
data class BuildInfo(
    val versionName: String,
    /** A debug build - unlocks the tester-only superuser modes. Always false in a release build. */
    val isDebug: Boolean,
)
