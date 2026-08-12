package tv.hsrui.bolo.buildlogic

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AppVersioningTest {
    private val commitSha = "0123456789abcdef0123456789abcdef01234567"

    @Test
    fun `loads the three supported version properties`() {
        val file = Files.createTempFile("version", ".properties").toFile()
        try {
            file.writeText(
                """
                APP_VERSION_CORE=1.2.3
                APP_RELEASE_CHANNEL=beta
                APP_PRERELEASE_NUMBER=4
                """.trimIndent(),
            )

            assertEquals(
                VersionConfig(
                    core = "1.2.3",
                    channel = ReleaseChannel.BETA,
                    prereleaseNumber = 4,
                ),
                loadVersionConfig(file),
            )
        } finally {
            file.delete()
        }
    }

    @Test
    fun `rejects a build number in version properties`() {
        val file = Files.createTempFile("version", ".properties").toFile()
        try {
            file.writeText(
                """
                APP_VERSION_CORE=1.2.3
                APP_RELEASE_CHANNEL=alpha
                APP_PRERELEASE_NUMBER=1
                APP_BUILD_NUMBER=7
                """.trimIndent(),
            )

            assertFailsWith<IllegalArgumentException> {
                loadVersionConfig(file)
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun `formats alpha and beta display versions with sha but without prerelease number`() {
        val gitMetadata = GitMetadata(
            commitSha = commitSha,
            buildNumber = 42,
            isDirty = false,
        )

        listOf(
            ReleaseChannel.ALPHA to "1.2.3-alpha-0123456",
            ReleaseChannel.BETA to "1.2.3-beta-0123456",
        ).forEach { (channel, expectedDisplayVersion) ->
            val versionConfig = VersionConfig(
                core = "1.2.3",
                channel = channel,
                prereleaseNumber = 5,
            )
            val metadata = resolveVersionMetadata(versionConfig, gitMetadata)

            assertEquals("1.2.3-${channel.name.lowercase()}.5", metadata.releaseVersion)
            assertEquals(expectedDisplayVersion, metadata.appDisplayVersion)
            assertEquals(
                "1.2.3-${channel.name.lowercase()}.5-b42-g0123456789ab",
                metadata.artifactVersion,
            )
        }
    }

    @Test
    fun `formats rc and stable versions`() {
        val gitMetadata = GitMetadata(
            commitSha = commitSha,
            buildNumber = 42,
            isDirty = false,
        )
        val rc = resolveVersionMetadata(
            VersionConfig(
                core = "1.2.3",
                channel = ReleaseChannel.RC,
                prereleaseNumber = 2,
            ),
            gitMetadata,
        )
        val stable = resolveVersionMetadata(
            VersionConfig(
                core = "1.2.3",
                channel = ReleaseChannel.STABLE,
                prereleaseNumber = 0,
            ),
            gitMetadata,
        )

        assertEquals("1.2.3-rc.2", rc.releaseVersion)
        assertEquals("1.2.3-rc2", rc.appDisplayVersion)
        assertEquals("1.2.3-rc.2-b42-g0123456789ab", rc.artifactVersion)
        assertEquals("1.2.3", stable.releaseVersion)
        assertEquals("1.2.3", stable.appDisplayVersion)
        assertEquals("1.2.3-b42-g0123456789ab", stable.artifactVersion)
    }

    @Test
    fun `rejects malformed core channel and prerelease combinations`() {
        listOf(
            VersionConfig("1.2", ReleaseChannel.ALPHA, 1),
            VersionConfig("01.2.3", ReleaseChannel.ALPHA, 1),
            VersionConfig("1.2.3-beta", ReleaseChannel.BETA, 1),
            VersionConfig("1.2.3", ReleaseChannel.BETA, 0),
            VersionConfig("1.2.3", ReleaseChannel.RC, -1),
            VersionConfig("1.2.3", ReleaseChannel.STABLE, 1),
        ).forEach { versionConfig ->
            assertFailsWith<IllegalArgumentException> {
                formatReleaseVersion(versionConfig)
            }
        }

        val file = Files.createTempFile("version-invalid-channel", ".properties").toFile()
        try {
            file.writeText(
                """
                APP_VERSION_CORE=1.2.3
                APP_RELEASE_CHANNEL=preview
                APP_PRERELEASE_NUMBER=1
                """.trimIndent(),
            )
            assertFailsWith<IllegalArgumentException> {
                loadVersionConfig(file)
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun `appends dirty only to artifact version`() {
        val metadata = resolveVersionMetadata(
            VersionConfig(
                core = "1.0.0",
                channel = ReleaseChannel.ALPHA,
                prereleaseNumber = 1,
            ),
            GitMetadata(
                commitSha = commitSha,
                buildNumber = 12,
                isDirty = true,
            ),
        )

        assertEquals("1.0.0-alpha-0123456", metadata.appDisplayVersion)
        assertEquals("1.0.0-alpha.1-b12-g0123456789ab-dirty", metadata.artifactVersion)
    }

    @Test
    fun `requires zero prerelease number only for stable`() {
        assertFailsWith<IllegalArgumentException> {
            formatReleaseVersion(
                VersionConfig(
                    core = "1.0.0",
                    channel = ReleaseChannel.STABLE,
                    prereleaseNumber = 1,
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            formatReleaseVersion(
                VersionConfig(
                    core = "1.0.0",
                    channel = ReleaseChannel.ALPHA,
                    prereleaseNumber = 0,
                ),
            )
        }
    }

    @Test
    fun `validates cross-platform numeric limits`() {
        assertFailsWith<IllegalArgumentException> {
            resolveVersionMetadata(
                VersionConfig(
                    core = "256.0.0",
                    channel = ReleaseChannel.STABLE,
                    prereleaseNumber = 0,
                ),
                GitMetadata(
                    commitSha = commitSha,
                    buildNumber = 1,
                    isDirty = false,
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            resolveVersionMetadata(
                VersionConfig(
                    core = "1.0.0",
                    channel = ReleaseChannel.STABLE,
                    prereleaseNumber = 0,
                ),
                GitMetadata(
                    commitSha = commitSha,
                    buildNumber = 65_536,
                    isDirty = false,
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            resolveVersionMetadata(
                VersionConfig(
                    core = "1.0.0",
                    channel = ReleaseChannel.STABLE,
                    prereleaseNumber = 0,
                ),
                GitMetadata(
                    commitSha = commitSha,
                    buildNumber = 2_100_000_001,
                    isDirty = false,
                ),
            )
        }
    }

    @Test
    fun `rejects an invalid full commit sha`() {
        assertFailsWith<IllegalArgumentException> {
            resolveVersionMetadata(
                VersionConfig(
                    core = "1.0.0",
                    channel = ReleaseChannel.STABLE,
                    prereleaseNumber = 0,
                ),
                GitMetadata(
                    commitSha = "0123456",
                    buildNumber = 1,
                    isDirty = false,
                ),
            )
        }
    }

    @Test
    fun `rejects a non git directory`() {
        val directory = Files.createTempDirectory("app-versioning-not-git").toFile()
        try {
            assertFailsWith<IllegalStateException> {
                resolveGitMetadata(directory)
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `rejects a shallow git repository`() {
        val workspace = Files.createTempDirectory("app-versioning-shallow").toFile()
        val sourceRepository = File(workspace, "source").apply { mkdirs() }
        val shallowRepository = File(workspace, "shallow")
        try {
            runGit(sourceRepository, "init", "-b", "main")
            runGit(sourceRepository, "config", "user.name", "Version Test")
            runGit(sourceRepository, "config", "user.email", "version-test@example.com")
            commit(sourceRepository, "first.txt", "first", "first")
            commit(sourceRepository, "second.txt", "second", "second")
            runGit(
                workspace,
                "clone",
                "--no-local",
                "--depth",
                "1",
                sourceRepository.absolutePath,
                shallowRepository.absolutePath,
            )

            assertFailsWith<IllegalArgumentException> {
                resolveGitMetadata(shallowRepository)
            }
        } finally {
            workspace.deleteRecursively()
        }
    }

    @Test
    fun `counts every commit reachable from head including merged parents`() {
        val repository = Files.createTempDirectory("app-versioning-git").toFile()
        try {
            runGit(repository, "init", "-b", "main")
            runGit(repository, "config", "user.name", "Version Test")
            runGit(repository, "config", "user.email", "version-test@example.com")
            commit(repository, "base.txt", "base", "base")
            runGit(repository, "switch", "-c", "feature")
            commit(repository, "feature.txt", "feature", "feature")
            runGit(repository, "switch", "main")
            commit(repository, "main.txt", "main", "main")
            runGit(repository, "merge", "--no-ff", "feature", "-m", "merge")

            val metadata = resolveGitMetadata(repository)

            assertEquals(4, metadata.buildNumber)
            assertTrue(Regex("^[0-9a-f]{40}$").matches(metadata.commitSha))
            assertFalse(metadata.isDirty)
            assertEquals(metadata, resolveGitMetadata(repository))

            runGit(repository, "switch", "-c", "post-merge")
            commit(repository, "post-merge.txt", "post-merge", "post-merge")
            val branchMetadata = resolveGitMetadata(repository)
            assertEquals(5, branchMetadata.buildNumber)
            assertTrue(branchMetadata.commitSha != metadata.commitSha)

            runGit(repository, "switch", "main")
            assertEquals(metadata, resolveGitMetadata(repository))

            File(repository, "untracked.txt").writeText("dirty")
            assertTrue(resolveGitMetadata(repository).isDirty)
        } finally {
            repository.deleteRecursively()
        }
    }

    private fun commit(
        repository: File,
        fileName: String,
        content: String,
        message: String,
    ) {
        File(repository, fileName).writeText(content)
        runGit(repository, "add", fileName)
        runGit(repository, "commit", "-m", message)
    }

    private fun runGit(repository: File, vararg arguments: String) {
        val process = ProcessBuilder(listOf("git", "-C", repository.absolutePath) + arguments)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()
        check(exitCode == 0) {
            "Git command failed (${arguments.joinToString(" ")}): $output"
        }
    }
}
