package tv.hsrui.bolo.buildlogic

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity

abstract class GitMetadataValueSource : ValueSource<GitMetadata, GitMetadataValueSource.Parameters> {
    interface Parameters : ValueSourceParameters {
        val repositoryDirectory: DirectoryProperty

        @get:InputFiles
        @get:PathSensitive(PathSensitivity.RELATIVE)
        val gitStateFiles: ConfigurableFileCollection
    }

    override fun obtain(): GitMetadata = resolveGitMetadata(parameters.repositoryDirectory.get().asFile)
}
