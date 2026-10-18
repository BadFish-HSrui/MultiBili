package tv.hsrui.bolo.buildlogic

import org.gradle.api.Action
import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import java.io.Serializable
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** Compose 会重新签名 JAR 内的 dylib；在生成安装包前同步清单并重新封签应用。 */
class MacNativeManifestAction(
    private val appDirectory: Provider<Directory>,
    private val signingIdentity: Provider<String>,
    private val signingKeychain: Provider<String>,
) : Action<Task>, Serializable {
    override fun execute(task: Task) {
        val app = appDirectory.get().asFile
        val jars = app.resolve("Contents/app").listFiles().orEmpty().filter { it.extension == "jar" }
        var manifests = 0
        var changed = false
        for (jar in jars) {
            ZipFile(jar).use { archive ->
                val entry = archive.getEntry("bolo-native/files.txt") ?: return@use
                manifests++
                val original = archive.getInputStream(entry).use { it.readBytes().toString(Charsets.UTF_8) }
                val names = original.lineSequence().filter(String::isNotBlank).map { line ->
                    val parts = line.split("  ", limit = 2)
                    require(parts.size == 2 && parts[0].matches(Regex("[0-9a-f]{64}"))) { "无效的原生库清单：$jar" }
                    parts[1].also { name ->
                        require(name.isNotBlank() && name !in setOf(".", "..") && '/' !in name && '\\' !in name)
                    }
                }.toList()
                require(names.isNotEmpty() && names.distinct().size == names.size) { "原生库清单为空或重复：$jar" }
                val updated = names.joinToString("\n", postfix = "\n") { name ->
                    val library = checkNotNull(archive.getEntry("bolo-native/$name")) { "缺少原生库：$name" }
                    val hash = archive.getInputStream(library).use { stream ->
                        val digest = MessageDigest.getInstance("SHA-256")
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val count = stream.read(buffer)
                            if (count < 0) break
                            digest.update(buffer, 0, count)
                        }
                        digest.digest().joinToString("") { "%02x".format(it) }
                    }
                    "$hash  $name"
                }
                if (updated != original) {
                    val temporary = Files.createTempFile(jar.parentFile.toPath(), jar.name, ".tmp")
                    try {
                        ZipOutputStream(Files.newOutputStream(temporary)).use { output ->
                            for (source in archive.entries()) {
                                output.putNextEntry(ZipEntry(source.name).apply {
                                    time = source.time
                                    comment = source.comment
                                    extra = source.extra
                                })
                                if (source.name == entry.name) output.write(updated.toByteArray(Charsets.UTF_8))
                                else archive.getInputStream(source).use { it.copyTo(output) }
                                output.closeEntry()
                            }
                        }
                        Files.setPosixFilePermissions(temporary, Files.getPosixFilePermissions(jar.toPath()))
                        Files.move(temporary, jar.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                    } finally {
                        Files.deleteIfExists(temporary)
                    }
                    changed = true
                }
            }
        }
        check(manifests == 1) { "期望一个播放器原生库清单，实际为 $manifests：$app" }
        if (changed) {
            val identity = signingIdentity.get()
            runCommand(task, listOf("/usr/bin/codesign", "--force", "--sign", identity,
                "--preserve-metadata=identifier,entitlements,requirements,flags,runtime") +
                (if (identity == "-") emptyList() else listOf("--timestamp")) +
                signingKeychain.orNull?.let { listOf("--keychain", it) }.orEmpty() + app.absolutePath)
        }
        runCommand(task, listOf("/usr/bin/codesign", "--verify", "--deep", "--strict", app.absolutePath))
        task.logger.lifecycle("macOS 原生库清单与应用签名校验通过：${app.name}")
    }

    private fun runCommand(task: Task, command: List<String>) {
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        check(process.waitFor() == 0) { "${command.first()} 失败：$output" }
        if (output.isNotBlank()) task.logger.info(output.trim())
    }
}
