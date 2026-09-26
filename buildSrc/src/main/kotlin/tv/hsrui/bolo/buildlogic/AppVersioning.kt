package tv.hsrui.bolo.buildlogic

import java.io.File
import java.io.Serializable
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.Locale
import java.util.Properties
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

enum class ReleaseChannel {
    ALPHA,
    BETA,
    RC,
    STABLE,
}

data class VersionConfig(
    val core: String,
    val channel: ReleaseChannel,
    val prereleaseNumber: Int,
) : Serializable {
    val coreVersion: String
        get() = core

    val releaseChannel: ReleaseChannel
        get() = channel
}

data class GitMetadata(
    val commitSha: String,
    val buildNumber: Int,
    val isDirty: Boolean,
) : Serializable {
    val commitSha7: String
        get() = commitSha.take(7)

    val commitSha12: String
        get() = commitSha.take(12)
}

data class ResolvedVersionMetadata(
    val releaseVersion: String,
    val appDisplayVersion: String,
    val versionConfig: VersionConfig,
    val gitMetadata: GitMetadata,
) : Serializable {
    val coreVersion: String
        get() = versionConfig.coreVersion

    val releaseChannel: ReleaseChannel
        get() = versionConfig.releaseChannel

    val prereleaseNumber: Int
        get() = versionConfig.prereleaseNumber

    val buildNumber: Int
        get() = gitMetadata.buildNumber

    val commitSha: String
        get() = gitMetadata.commitSha

    val commitSha7: String
        get() = gitMetadata.commitSha7

    val commitSha12: String
        get() = gitMetadata.commitSha12

    val isDirty: Boolean
        get() = gitMetadata.isDirty
}

private val coreVersionPattern = Regex("^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)$")
private val commitShaPattern = Regex("^[0-9a-f]{40}$")
private val supportedVersionPropertyNames = setOf(
    "APP_VERSION_CORE",
    "APP_RELEASE_CHANNEL",
    "APP_PRERELEASE_NUMBER",
)

fun loadVersionConfig(file: File): VersionConfig {
    require(file.isFile) { "Version configuration file does not exist: ${file.absolutePath}" }

    val properties = Properties().apply {
        file.inputStream().buffered().use(::load)
    }
    val unexpectedPropertyNames = properties.stringPropertyNames() - supportedVersionPropertyNames
    require(unexpectedPropertyNames.isEmpty()) {
        "Unsupported version properties: ${unexpectedPropertyNames.sorted().joinToString()}"
    }

    fun requiredProperty(name: String): String =
        requireNotNull(properties.getProperty(name)?.trim()?.takeIf(String::isNotEmpty)) {
            "Missing required version property: $name"
        }

    val releaseChannelValue = requiredProperty("APP_RELEASE_CHANNEL")
    val releaseChannel = ReleaseChannel.entries.singleOrNull {
        it.name.lowercase(Locale.ROOT) == releaseChannelValue
    }
    requireNotNull(releaseChannel) {
        "APP_RELEASE_CHANNEL must be one of alpha, beta, rc, stable: $releaseChannelValue"
    }

    val prereleaseNumberValue = requiredProperty("APP_PRERELEASE_NUMBER")
    val prereleaseNumber = prereleaseNumberValue.toIntOrNull()
    requireNotNull(prereleaseNumber) {
        "APP_PRERELEASE_NUMBER must be an integer: $prereleaseNumberValue"
    }

    return VersionConfig(
        core = requiredProperty("APP_VERSION_CORE"),
        channel = releaseChannel,
        prereleaseNumber = prereleaseNumber,
    ).also(::validateVersionConfig)
}

fun resolveGitMetadata(repositoryDirectory: File): GitMetadata {
    require(repositoryDirectory.isDirectory) {
        "Git repository directory does not exist: ${repositoryDirectory.absolutePath}"
    }
    require(runGit(repositoryDirectory, "rev-parse", "--is-inside-work-tree") == "true") {
        "Not a Git work tree: ${repositoryDirectory.absolutePath}"
    }
    require(runGit(repositoryDirectory, "rev-parse", "--is-shallow-repository") == "false") {
        "A complete Git history is required to calculate APP_BUILD_NUMBER"
    }

    val buildNumberValue = runGit(repositoryDirectory, "rev-list", "--count", "HEAD")
    val buildNumber = buildNumberValue.toIntOrNull()
    requireNotNull(buildNumber) { "Invalid Git commit count: $buildNumberValue" }

    return GitMetadata(
        commitSha = runGit(repositoryDirectory, "rev-parse", "HEAD"),
        buildNumber = buildNumber,
        isDirty = runGit(
            repositoryDirectory,
            "status",
            "--porcelain",
            "--untracked-files=normal",
        ).isNotEmpty(),
    ).also(::validateGitMetadata)
}

fun resolveVersionMetadata(
    versionConfig: VersionConfig,
    gitMetadata: GitMetadata,
): ResolvedVersionMetadata = ResolvedVersionMetadata(
    releaseVersion = formatReleaseVersion(versionConfig),
    appDisplayVersion = formatAppDisplayVersion(versionConfig, gitMetadata),
    versionConfig = versionConfig,
    gitMetadata = gitMetadata,
).also(::validateVersionMetadata)

fun formatReleaseVersion(versionConfig: VersionConfig): String {
    validateVersionConfig(versionConfig)
    return when (versionConfig.releaseChannel) {
        ReleaseChannel.ALPHA,
        ReleaseChannel.BETA,
        ReleaseChannel.RC,
        -> "${versionConfig.coreVersion}-${versionConfig.releaseChannel.propertyValue}.${versionConfig.prereleaseNumber}"

        ReleaseChannel.STABLE -> versionConfig.coreVersion
    }
}

fun formatAppDisplayVersion(
    versionConfig: VersionConfig,
    gitMetadata: GitMetadata,
): String {
    validateVersionConfig(versionConfig)
    validateGitMetadata(gitMetadata)
    return when (versionConfig.releaseChannel) {
        ReleaseChannel.ALPHA,
        ReleaseChannel.BETA,
        -> "${versionConfig.coreVersion}-${versionConfig.releaseChannel.propertyValue}-${gitMetadata.commitSha7}"

        ReleaseChannel.RC -> "${versionConfig.coreVersion}-rc${versionConfig.prereleaseNumber}"
        ReleaseChannel.STABLE -> versionConfig.coreVersion
    }
}

fun validateVersionMetadata(metadata: ResolvedVersionMetadata) {
    validateVersionConfig(metadata.versionConfig)
    validateGitMetadata(metadata.gitMetadata)
    require(metadata.releaseVersion == formatReleaseVersion(metadata.versionConfig)) {
        "Resolved releaseVersion does not match the version configuration"
    }
    require(metadata.appDisplayVersion == formatAppDisplayVersion(metadata.versionConfig, metadata.gitMetadata)) {
        "Resolved appDisplayVersion does not match the version and Git metadata"
    }
}

private val ReleaseChannel.propertyValue: String
    get() = name.lowercase(Locale.ROOT)

private fun validateVersionConfig(versionConfig: VersionConfig) {
    val match = requireNotNull(coreVersionPattern.matchEntire(versionConfig.coreVersion)) {
        "APP_VERSION_CORE must use MAJOR.MINOR.PATCH without leading zeroes: ${versionConfig.coreVersion}"
    }
    val major = match.groupValues[1].toIntOrNull()
    val minor = match.groupValues[2].toIntOrNull()
    requireNotNull(major) { "APP_VERSION_CORE major component is too large" }
    requireNotNull(minor) { "APP_VERSION_CORE minor component is too large" }
    require(major <= 255) { "APP_VERSION_CORE major component must not exceed 255" }
    require(minor <= 255) { "APP_VERSION_CORE minor component must not exceed 255" }
    if (versionConfig.channel == ReleaseChannel.STABLE) {
        require(versionConfig.prereleaseNumber == 0) {
            "APP_PRERELEASE_NUMBER must be 0 for stable releases"
        }
    } else {
        require(versionConfig.prereleaseNumber > 0) {
            "APP_PRERELEASE_NUMBER must be a positive integer for prereleases"
        }
    }
}

private fun validateGitMetadata(gitMetadata: GitMetadata) {
    require(gitMetadata.buildNumber > 0) { "APP_BUILD_NUMBER must be a positive integer" }
    require(gitMetadata.buildNumber <= 2_100_000_000) {
        "APP_BUILD_NUMBER exceeds the Android versionCode limit"
    }
    require(gitMetadata.buildNumber <= 65_535) {
        "APP_BUILD_NUMBER exceeds the Windows package build limit"
    }
    require(commitShaPattern.matches(gitMetadata.commitSha)) {
        "APP_COMMIT_SHA must be a full 40-character lowercase hexadecimal Git SHA"
    }
}

private fun runGit(
    repositoryDirectory: File,
    vararg arguments: String,
): String {
    val command = listOf("git", "-C", repositoryDirectory.absolutePath) + arguments
    val process = ProcessBuilder(command)
        // Git 的标准输出用于解析版本；Xcode 工具链警告不能混入结果。
        .redirectError(ProcessBuilder.Redirect.INHERIT)
        .start()
    val output = process.inputStream.bufferedReader().use { it.readText() }.trimEnd()
    val exitCode = process.waitFor()
    check(exitCode == 0) {
        "Git command failed with exit code $exitCode (${command.joinToString(" ")}): $output"
    }
    return output
}

abstract class AppVersionMetadataTask : DefaultTask() {
    @get:Input
    abstract val coreVersion: Property<String>

    @get:Input
    abstract val releaseChannel: Property<String>

    @get:Input
    abstract val prereleaseNumber: Property<Int>

    @get:Input
    abstract val releaseVersion: Property<String>

    @get:Input
    abstract val appDisplayVersion: Property<String>

    @get:Input
    abstract val buildNumber: Property<Int>

    @get:Input
    abstract val commitSha: Property<String>

    @get:Input
    abstract val gitDirty: Property<Boolean>

    protected fun resolvedMetadata(): ResolvedVersionMetadata = ResolvedVersionMetadata(
        releaseVersion = releaseVersion.get(),
        appDisplayVersion = appDisplayVersion.get(),
        versionConfig = VersionConfig(
            core = coreVersion.get(),
            channel = ReleaseChannel.valueOf(releaseChannel.get()),
            prereleaseNumber = prereleaseNumber.get(),
        ),
        gitMetadata = GitMetadata(
            commitSha = commitSha.get(),
            buildNumber = buildNumber.get(),
            isDirty = gitDirty.get(),
        ),
    ).also(::validateVersionMetadata)
}

@CacheableTask
abstract class WriteBuildMetadataTask : AppVersionMetadataTask() {
    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun write() {
        val metadata = resolvedMetadata()
        writeTextAtomically(
            outputFile.get().asFile,
            """
            {
              "coreVersion": "${metadata.coreVersion}",
              "releaseVersion": "${metadata.releaseVersion}",
              "appDisplayVersion": "${metadata.appDisplayVersion}",
              "buildNumber": ${metadata.buildNumber},
              "commitSha": "${metadata.commitSha}"
            }
            """.trimIndent() + "\n",
        )
    }
}

@DisableCachingByDefault(because = "Console output should be produced whenever requested")
abstract class PrintAppVersionMetadataTask : AppVersionMetadataTask() {
    @TaskAction
    fun printMetadata() {
        val metadata = resolvedMetadata()
        println("APP_VERSION_CORE=${metadata.coreVersion}")
        println("APP_RELEASE_CHANNEL=${metadata.releaseChannel.name.lowercase(Locale.ROOT)}")
        println("APP_PRERELEASE_NUMBER=${metadata.prereleaseNumber}")
        println("APP_RELEASE_VERSION=${metadata.releaseVersion}")
        println("APP_DISPLAY_VERSION=${metadata.appDisplayVersion}")
        println("APP_BUILD_NUMBER=${metadata.buildNumber}")
        println("APP_COMMIT_SHA=${metadata.commitSha}")
        println("APP_COMMIT_SHA7=${metadata.commitSha7}")
        println("APP_COMMIT_SHA12=${metadata.commitSha12}")
        println("APP_GIT_DIRTY=${metadata.isDirty}")
    }
}

private fun writeTextAtomically(target: File, content: String) {
    target.parentFile.mkdirs()
    val temporaryFile = Files.createTempFile(target.parentFile.toPath(), ".${target.name}.", ".tmp")
    try {
        Files.writeString(temporaryFile, content)
        try {
            Files.move(temporaryFile, target.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporaryFile, target.toPath(), REPLACE_EXISTING)
        }
    } finally {
        Files.deleteIfExists(temporaryFile)
    }
}
