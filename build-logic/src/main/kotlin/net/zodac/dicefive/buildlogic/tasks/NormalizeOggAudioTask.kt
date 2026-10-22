package net.zodac.dicefive.buildlogic.tasks

import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations

/**
 * Brings every sound effect source ([sourceFiles], the .ogg files in app/audio) to the same peak level with [script]
 * (scripts/normalize-audio.sh, whose header says why), writing the copies to [outputDirectory]'s `raw/`. A build
 * packages those, never the sources: `git status` only ever shows edits to the original clips, never the
 * machine-generated, gain-adjusted bytes.
 */
abstract class NormalizeOggAudioTask @Inject constructor(
    private val execOperations: ExecOperations,
) : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val sourceFiles: ConfigurableFileCollection

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val script: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun normalize() {
        val rawDir = outputDirectory.get().asFile.resolve("raw")
        rawDir.deleteRecursively()
        execOperations.exec {
            commandLine(
                listOf(script.get().asFile.absolutePath, rawDir.absolutePath) +
                    sourceFiles.files.sortedBy { it.name }.map { it.absolutePath },
            )
        }
    }
}
