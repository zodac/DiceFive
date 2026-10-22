package net.zodac.dicefive.buildlogic.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * The asset half of the report: a font, a sound or an image isn't a Gradle dependency, so nothing can
 * discover its license - someone has to write down where it came from. This makes that impossible to
 * skip: every file in [assetFiles] - an app sets it to every file it bundles (on Android, its source sets'
 * res/ bar values*, which is text and config, not creative work, and assets/; the sound effects' sources in app/audio/;
 * and :app:shared's composeResources/ bar values*, the shared UI's font and icons) - must have a complete entry in
 * [sourcesManifest] (app/licensing/asset-sources.json): what it is, where it came from and its license. That applies to the app's own
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
