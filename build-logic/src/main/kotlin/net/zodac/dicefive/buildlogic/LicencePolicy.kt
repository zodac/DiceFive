package net.zodac.dicefive.buildlogic

/**
 * Which licences an app may ship third-party code and assets under - the copyleft guard every app's licence report
 * (on Android, the AboutLibraries plugin's strict mode) enforces. Anything else fails the build.
 *
 * Only permissive licences - those that ask for attribution and nothing more - are allowed outright, because they place
 * no conditions on how this app itself is licensed. A copyleft (GPL, LGPL, AGPL, MPL, EPL, CDDL...) or unrecognised
 * licence has to be looked at by a person before it can ship: add it here only once its terms are understood - see
 * .claude/PUBLISHING.md.
 */
object LicencePolicy {

    val ALLOWED: List<String> = listOf("Apache-2.0", "MIT", "BSD-2-Clause", "BSD-3-Clause")

    /** Licences allowed only for the components listed against them, never for anything else that turns up under one. */
    val ALLOWED_FOR: Map<String, List<String>> = mapOf(
        // The SIL Open Font License is copyleft on the font alone (a modified Sora must stay OFL and can't be sold by
        // itself) and places no conditions on the app it's bundled in.
        "OFL-1.1" to listOf("sora", "mathjax-fonts"),
        // The sound effects' Freesound sources (see app/licensing/asset-sources.json). CC0 asks for nothing; CC-BY asks
        // for a credit, a link to the source and license, and a note of any changes - all in its libraries/ entry.
        // Neither places any condition on the app itself. (Never CC-BY-SA - adaptations must stay under it - nor
        // anything NC, which rules out ads and Pro; neither is allowed here.)
        "CC0-1.0" to listOf("freesound-588198", "freesound-695731", "freesound-185986", "freesound-596051", "freesound-456803"),
        "CC-BY-4.0" to listOf("freesound-140147", "freesound-446043"),
        // Google's own terms for the Play services SDKs (Play Games Services and what it pulls in): proprietary, free to
        // ship in an app, with no condition on the app's own licence.
        "ASDKL" to listOf(
            "com.google.android.gms:play-services-base",
            "com.google.android.gms:play-services-basement",
            "com.google.android.gms:play-services-games-v2",
            "com.google.android.gms:play-services-tasks",
        ),
    )
}
