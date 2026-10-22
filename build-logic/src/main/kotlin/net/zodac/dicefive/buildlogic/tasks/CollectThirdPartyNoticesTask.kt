package net.zodac.dicefive.buildlogic.tasks

import java.util.zip.ZipFile
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

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
