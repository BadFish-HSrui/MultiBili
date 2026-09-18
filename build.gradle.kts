import tv.hsrui.bolo.buildlogic.AppVersionMetadataTask
import tv.hsrui.bolo.buildlogic.GitMetadataValueSource
import tv.hsrui.bolo.buildlogic.PrintAppVersionMetadataTask
import tv.hsrui.bolo.buildlogic.WriteBuildMetadataTask
import tv.hsrui.bolo.buildlogic.loadVersionConfig
import tv.hsrui.bolo.buildlogic.resolveVersionMetadata

plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.kotlinAndroid) apply false
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.composeHotReload) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.buildkonfig) apply false
}

val appVersionConfig = loadVersionConfig(layout.projectDirectory.file("version.properties").asFile)
val localGitMetadata = providers.of(GitMetadataValueSource::class) {
    parameters.repositoryDirectory.set(layout.projectDirectory)
    parameters.gitStateFiles.from(
        layout.projectDirectory.file(".git/HEAD"),
        layout.projectDirectory.file(".git/index"),
        layout.projectDirectory.file(".git/packed-refs"),
        layout.projectDirectory.file(".git/shallow"),
        layout.projectDirectory.dir(".git/refs"),
    )
}.get()

val resolvedVersionMetadata = resolveVersionMetadata(
    versionConfig = appVersionConfig,
    gitMetadata = localGitMetadata,
)

rootProject.extra["appVersionMetadata"] = resolvedVersionMetadata

allprojects {
    version = resolvedVersionMetadata.releaseVersion
}

fun AppVersionMetadataTask.configureVersionMetadataInputs() {
    coreVersion.set(resolvedVersionMetadata.coreVersion)
    releaseChannel.set(resolvedVersionMetadata.releaseChannel.name)
    prereleaseNumber.set(resolvedVersionMetadata.prereleaseNumber)
    releaseVersion.set(resolvedVersionMetadata.releaseVersion)
    appDisplayVersion.set(resolvedVersionMetadata.appDisplayVersion)
    artifactVersion.set(resolvedVersionMetadata.artifactVersion)
    buildNumber.set(resolvedVersionMetadata.buildNumber)
    commitSha.set(resolvedVersionMetadata.commitSha)
    gitDirty.set(resolvedVersionMetadata.isDirty)
}

val writeBuildMetadata = tasks.register<WriteBuildMetadataTask>("writeBuildMetadata") {
    group = "versioning"
    description = "Writes the resolved application version metadata as JSON."
    configureVersionMetadataInputs()
    outputFile.set(layout.buildDirectory.file("version/build-metadata.json"))
}

tasks.register<PrintAppVersionMetadataTask>("printAppVersionMetadata") {
    group = "versioning"
    description = "Prints the resolved application version metadata."
    configureVersionMetadataInputs()
}

allprojects {
    tasks.matching { task ->
        task.name == "assembleRelease" ||
            task.name == "bundleRelease" ||
            task.name.startsWith("package")
    }.configureEach {
        dependsOn(rootProject.tasks.named("writeBuildMetadata"))
    }
}
