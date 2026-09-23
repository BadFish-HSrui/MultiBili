package tv.hsrui.bolo.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Native bundles use content fingerprints, process locks and integrity manifests")
abstract class NativePlayerBuildTask : DefaultTask() {
    @get:Internal abstract val sourceDirectory: DirectoryProperty
    @get:Internal abstract val nativeBuildDirectory: DirectoryProperty
    @get:Input abstract val command: Property<String>
    @get:Input abstract val parallelJobs: Property<Int>

    @TaskAction
    fun buildNativePlayer() {
        NativePlayerBuild(sourceDirectory.get().asFile, nativeBuildDirectory.get().asFile, logger,
            parallelJobs.get()).execute(command.get())
    }
}
