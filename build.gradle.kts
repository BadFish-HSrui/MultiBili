import tv.hsrui.bolo.buildlogic.AppVersionMetadataTask
import tv.hsrui.bolo.buildlogic.GitMetadataValueSource
import tv.hsrui.bolo.buildlogic.PrintAppVersionMetadataTask
import tv.hsrui.bolo.buildlogic.VerifyAppVersionMetadataTask
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

providers.gradleProperty("appBuildNumber").orNull?.let { configuredBuildNumber ->
    val parsedBuildNumber = configuredBuildNumber.toIntOrNull()
    requireNotNull(parsedBuildNumber) {
        "Gradle property appBuildNumber must be an integer: $configuredBuildNumber"
    }
    require(parsedBuildNumber == localGitMetadata.buildNumber) {
        "Gradle property appBuildNumber ($parsedBuildNumber) does not match the full Git history " +
            "commit count (${localGitMetadata.buildNumber})"
    }
}

providers.gradleProperty("appCommitSha").orNull?.let { configuredCommitSha ->
    require(configuredCommitSha == localGitMetadata.commitSha) {
        "Gradle property appCommitSha ($configuredCommitSha) does not match HEAD " +
            "(${localGitMetadata.commitSha})"
    }
}

fun parseBooleanGradleProperty(name: String, value: String): Boolean = when (value) {
    "true" -> true
    "false" -> false
    else -> error("Gradle property $name must be true or false: $value")
}

providers.gradleProperty("appGitDirty").orNull?.let { configuredGitDirty ->
    val parsedGitDirty = parseBooleanGradleProperty("appGitDirty", configuredGitDirty)
    require(parsedGitDirty == localGitMetadata.isDirty) {
        "Gradle property appGitDirty ($parsedGitDirty) does not match the local Git state " +
            "(${localGitMetadata.isDirty})"
    }
}
val appBuildOrigin = providers.gradleProperty("appBuildOrigin").orNull ?: "local"
require(Regex("^[A-Za-z0-9._-]+$").matches(appBuildOrigin)) {
    "Gradle property appBuildOrigin may contain only letters, digits, dot, underscore, and hyphen"
}
val appOfficialBuild = providers.gradleProperty("appOfficialBuild").orNull?.let {
    parseBooleanGradleProperty("appOfficialBuild", it)
} ?: false
val appSourceRef = providers.gradleProperty("appSourceRef")
    .orElse(providers.environmentVariable("GITHUB_REF"))
    .orNull
val resolvedVersionMetadata = resolveVersionMetadata(
    versionConfig = appVersionConfig,
    gitMetadata = localGitMetadata,
)

rootProject.extra["appVersionMetadata"] = resolvedVersionMetadata
rootProject.extra["appBuildOrigin"] = appBuildOrigin
rootProject.extra["appOfficialBuild"] = appOfficialBuild

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
    buildOrigin.set(appBuildOrigin)
    officialBuild.set(appOfficialBuild)
    sourceRef.set(appSourceRef ?: "")
}

val verifyAppVersionMetadata = tasks.register<VerifyAppVersionMetadataTask>("verifyAppVersionMetadata") {
    group = "versioning"
    description = "Validates the resolved application version and Git metadata."
    configureVersionMetadataInputs()
}

val writeBuildMetadata = tasks.register<WriteBuildMetadataTask>("writeBuildMetadata") {
    group = "versioning"
    description = "Writes the resolved application version metadata as JSON."
    dependsOn(verifyAppVersionMetadata)
    configureVersionMetadataInputs()
    outputFile.set(layout.buildDirectory.file("version/build-metadata.json"))
}

tasks.register<PrintAppVersionMetadataTask>("printAppVersionMetadata") {
    group = "versioning"
    description = "Prints the resolved application version metadata."
    dependsOn(verifyAppVersionMetadata)
    configureVersionMetadataInputs()
}

allprojects {
    tasks.matching { task ->
        task.name == "assembleRelease" ||
            task.name == "bundleRelease" ||
            task.name.startsWith("package")
    }.configureEach {
        dependsOn(rootProject.tasks.named("verifyAppVersionMetadata"))
    }
}
