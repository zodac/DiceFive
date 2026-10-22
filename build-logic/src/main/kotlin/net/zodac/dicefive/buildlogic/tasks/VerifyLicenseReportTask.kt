package net.zodac.dicefive.buildlogic.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * The checks the plugin's own strict mode doesn't make, run over the aboutlibraries.json it just
 * generated for one variant:
 * - every license some library uses has its full text - offline, the plugin only knows its name
 *   (see offlineMode in app/android/build.gradle.kts), and a license is only honoured if its text ships with the app;
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
