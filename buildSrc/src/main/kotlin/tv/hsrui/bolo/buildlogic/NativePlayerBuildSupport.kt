package tv.hsrui.bolo.buildlogic

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.gradle.api.GradleException
import org.gradle.api.logging.Logger
import java.io.File
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.COPY_ATTRIBUTES
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.WRITE
import java.nio.file.attribute.PosixFilePermission
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import java.util.zip.GZIPInputStream
import kotlin.concurrent.withLock

internal fun nativePath(file: File): String = file.absoluteFile.invariantSeparatorsPath
internal fun nativeSha(file: File): String = file.inputStream().use { input ->
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(128 * 1024)
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        digest.update(buffer, 0, count)
    }
    digest.digest().joinToString("") { "%02x".format(it) }
}
internal fun nativeHash(text: String): String = MessageDigest.getInstance("SHA-256")
    .digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

private fun orderedJson(value: Any?): Any? = when (value) {
    is Map<*, *> -> value.entries.associate { it.key.toString() to orderedJson(it.value) }.toSortedMap()
    is Iterable<*> -> value.map(::orderedJson)
    else -> value
}
internal fun nativeJson(value: Any?): String = JsonOutput.toJson(orderedJson(value))
@Suppress("UNCHECKED_CAST")
internal fun nativeReadJson(file: File): Map<String, Any?> = JsonSlurper().parse(file) as Map<String, Any?>
internal fun nativeWriteJson(file: File, value: Any?) {
    file.parentFile.mkdirs()
    val temporary = Files.createTempFile(file.parentFile.toPath(), ".json-", ".tmp")
    try {
        Files.writeString(temporary, JsonOutput.prettyPrint(nativeJson(value)) + "\n")
        Files.move(temporary, file.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
    } finally {
        Files.deleteIfExists(temporary)
    }
}

// FileChannel 不允许同一 JVM 的重叠锁；先在 JVM 内协调，再与其他 Gradle 进程协调。
private val nativeLocks = ConcurrentHashMap<String, ReentrantLock>()
internal fun <T> nativeLock(file: File, shared: Boolean = false, action: () -> T): T =
    nativeLocks.computeIfAbsent(file.canonicalPath) { ReentrantLock() }.withLock {
        file.parentFile.mkdirs()
        FileChannel.open(file.toPath(), CREATE, READ, WRITE).use { channel ->
            channel.lock(0, Long.MAX_VALUE, shared).use { action() }
        }
    }

internal fun nativeFiles(root: File): List<File> = if (!root.exists()) emptyList() else
    Files.walk(root.toPath()).use { paths -> paths.filter { it != root.toPath() }.map { it.toFile() }.toList() }

internal fun nativeCopyFile(source: File, target: File) {
    target.parentFile.mkdirs()
    Files.copy(source.toPath(), target.toPath(), REPLACE_EXISTING, COPY_ATTRIBUTES, NOFOLLOW_LINKS)
}
internal fun nativeCopyTree(source: File, target: File) {
    target.mkdirs()
    nativeFiles(source).forEach { file ->
        val destination = target.resolve(file.relativeTo(source).path)
        if (Files.isDirectory(file.toPath(), NOFOLLOW_LINKS)) destination.mkdirs()
        else nativeCopyFile(file, destination)
    }
}
internal fun nativeDelete(file: File) {
    if (Files.exists(file.toPath(), NOFOLLOW_LINKS) && !file.deleteRecursively()) {
        throw GradleException("Cannot remove native build directory: $file")
    }
}

internal fun nativeManifest(directory: File): Map<String, String> = nativeFiles(directory)
    .filter { it.isFile && it != directory.resolve("manifest.json") }
    .associate { it.relativeTo(directory).invariantSeparatorsPath to nativeSha(it) }.toSortedMap()

internal fun nativeValidBundle(directory: File, fingerprint: String): Boolean = try {
    val manifest = nativeReadJson(directory.resolve("manifest.json"))
    val expected = manifest["files"] as? Map<*, *>
    manifest["fingerprint"] == fingerprint && !expected.isNullOrEmpty() &&
        nativeFiles(directory).none { Files.isSymbolicLink(it.toPath()) } &&
        expected.keys.all { key ->
            key is String && !File(key).isAbsolute && '\\' !in key &&
                key.split('/').none { it == ".." || it.isEmpty() }
        } && expected == nativeManifest(directory)
} catch (_: Exception) {
    false
}

internal fun nativePublish(source: File, destination: File, fingerprint: String) {
    nativeWriteJson(source.resolve("manifest.json"), mapOf("fingerprint" to fingerprint, "files" to nativeManifest(source)))
    check(nativeValidBundle(source, fingerprint)) { "Invalid staged native artifact: $source" }
    destination.parentFile.mkdirs()
    val stage = Files.createTempDirectory(destination.parentFile.toPath(), ".staging-").toFile()
    try {
        nativeCopyTree(source, stage)
        nativeDelete(destination)
        Files.move(stage.toPath(), destination.toPath(), ATOMIC_MOVE)
    } finally {
        nativeDelete(stage)
    }
}

internal fun nativeUnpack(archive: File, destination: File) {
    destination.mkdirs()
    val root = destination.toPath().toAbsolutePath().normalize()
    val links = mutableListOf<Triple<File, String, Boolean>>()
    TarArchiveInputStream(GZIPInputStream(archive.inputStream().buffered())).use { tar ->
        while (true) {
            val entry = tar.nextEntry ?: break
            val path = root.resolve(entry.name).normalize()
            require(path.startsWith(root) && !entry.name.contains('\\')) { "Unsafe archive entry: ${entry.name}" }
            when {
                entry.isDirectory -> Files.createDirectories(path)
                entry.isSymbolicLink || entry.isLink -> links += Triple(path.toFile(), entry.linkName, entry.isLink)
                entry.isFile -> {
                    Files.createDirectories(path.parent)
                    Files.copy(tar, path, REPLACE_EXISTING)
                    if (!System.getProperty("os.name").startsWith("Windows")) {
                        val permissions = PosixFilePermission.entries.filterIndexed { index, _ ->
                            entry.mode and (1 shl (8 - index)) != 0
                        }.toSet()
                        Files.setPosixFilePermissions(path, permissions)
                    }
                }
                else -> throw GradleException("Unsupported archive entry: ${entry.name}")
            }
        }
    }
    // 最后创建链接，防止归档通过先创建的目录链接把后续文件写到解压目录之外。
    links.forEach { (file, name, hard) ->
        val path = file.toPath()
        val target = (if (hard) root else path.parent).resolve(name).normalize()
        require(target.startsWith(root) && !name.contains('\\')) { "Unsafe archive link: $file -> $name" }
        require(generateSequence(path.parent) { it.parent }.takeWhile { it.startsWith(root) }
            .none { Files.isSymbolicLink(it) }) { "Archive link traverses another link: $file" }
        Files.createDirectories(path.parent)
        if (hard) Files.createLink(path, target) else Files.createSymbolicLink(path, path.parent.relativize(target))
    }
}

internal fun nativeQuote(value: String): String = if (value.matches(Regex("[A-Za-z0-9_@%+=:,./-]+"))) value
    else "'" + value.replace("'", "'\"'\"'") + "'"
internal fun nativeShellSplit(value: String): List<String> {
    val result = mutableListOf<String>()
    val word = StringBuilder()
    var quote: Char? = null
    var escape = false
    for (char in value) {
        when {
            escape -> { word.append(char); escape = false }
            char == '\\' && quote != '\'' -> escape = true
            quote != null -> if (char == quote) quote = null else word.append(char)
            char == '\'' || char == '"' -> quote = char
            char.isWhitespace() -> if (word.isNotEmpty()) { result += word.toString(); word.clear() }
            else -> word.append(char)
        }
    }
    require(quote == null && !escape) { "Invalid pkg-config output: $value" }
    if (word.isNotEmpty()) result += word.toString()
    return result
}

internal class NativePlayerEnvironment(
    val root: File,
    private val logger: Logger,
) {
    val windows = System.getProperty("os.name").startsWith("Windows")
    val mac = System.getProperty("os.name").startsWith("Mac")
    val environment: MutableMap<String, String> = System.getenv().toMutableMap()
    private val brew = if (System.getProperty("os.arch") in listOf("aarch64", "arm64")) "/opt/homebrew" else "/usr/local"
    val tools = linkedMapOf<String, File>()
    val identity = linkedMapOf<String, Any?>()

    init {
        val search = mutableListOf<String>()
        if (mac) search += listOf("$brew/bin", "$brew/sbin")
        search += environment["PATH"].orEmpty().split(File.pathSeparator).filter(String::isNotBlank)
        environment["PATH"] = search.distinct().joinToString(File.pathSeparator)
        environment["LC_ALL"] = "C"
        environment["PYTHONHASHSEED"] = "0"
        environment["SOURCE_DATE_EPOCH"] = "1750000000"
        identity["host"] = listOf(System.getProperty("os.name"), System.getProperty("os.version"), System.getProperty("os.arch"))
    }

    private fun find(name: String): File? {
        val suffixes = if (windows) listOf(".exe", "", ".cmd", ".bat") else listOf("")
        return environment["PATH"].orEmpty().split(File.pathSeparator).asSequence().flatMap { folder ->
            suffixes.asSequence().map { File(folder, name + it) }
        }.firstOrNull { it.isFile && (windows || it.canExecute()) }
    }

    fun tool(name: String): File = tools.getOrPut(name) {
        find(name) ?: throw GradleException("Missing native build tool: $name. Install the tools listed in docs/build-guide.md; " +
            "Gradle does not install tools.")
    }

    fun run(vararg args: Any, cwd: File = root, env: Map<String, String> = environment, capture: Boolean = false): String =
        run(args.toList(), cwd, env, capture)

    fun run(args: List<Any>, cwd: File = root, env: Map<String, String> = environment, capture: Boolean = false): String {
        val command = args.map { if (it is File) nativePath(it) else it.toString() }.toMutableList()
        if (!File(command[0]).isAbsolute) command[0] = nativePath(tool(command[0]))
        logger.info("+ {}", command.joinToString(" ") { nativeQuote(it) })
        val process = ProcessBuilder(command).directory(cwd).redirectErrorStream(true).apply {
            environment().clear()
            environment().putAll(env)
        }.start()
        val output = StringBuilder()
        val tail = ArrayDeque<String>()
        try {
            process.inputStream.bufferedReader().use { reader ->
                if (capture) output.append(reader.readText()) else reader.forEachLine { line ->
                    logger.lifecycle(line)
                    tail.addLast(line)
                    if (tail.size > 30) tail.removeFirst()
                }
            }
            val exit = process.waitFor()
            if (exit != 0) throw GradleException("Native command failed ($exit): ${command.joinToString(" ")}\n" +
                if (capture) output.toString().takeLast(8000) else tail.joinToString("\n"))
            return output.toString()
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
    }

    fun prepare(targets: List<String>) {
        val requirements = linkedMapOf("meson" to "1.8.3", "ninja" to "1.11.1", "pkg-config" to "0.29", "python3" to "3.10")
        if (targets.any { it.startsWith("android-") || it.startsWith("linux-") }) requirements["cmake"] = "3.31"
        if (targets.any { it.endsWith("-x64") || it.endsWith("-x86") }) requirements["nasm"] = "2.16"
        for (name in listOf("git", "curl", "make", "patch", "bash") + requirements.keys) tool(name)
        for ((name, minimum) in requirements) {
            val executable = tool(name)
            val version = run(executable, "--version", capture = true).trim()
            requireVersion(name, version, minimum)
            identity[name] = mapOf("path" to nativePath(executable.canonicalFile), "sha256" to nativeSha(executable), "version" to version)
            logger.lifecycle("Native tool: {} -> {} ({})", name, executable, version.lineSequence().first())
        }
        // Homebrew 的 Jinja2 模块由 jinja2-cli 提供，构建只读取已安装模块，不创建 venv 或执行 pip。
        if (mac) {
            val library = File("$brew/opt/jinja2-cli/libexec/lib")
            val modulePaths = library.listFiles().orEmpty().map { it.resolve("site-packages") }.filter(File::isDirectory)
            if (modulePaths.isNotEmpty()) environment["PYTHONPATH"] =
                (modulePaths.map(::nativePath) + listOfNotNull(environment["PYTHONPATH"])).joinToString(File.pathSeparator)
            environment["SDKROOT"] = run("xcrun", "--sdk", "macosx", "--show-sdk-path", capture = true).trim()
            // Xcode 会同时导出多平台部署版本；宿主工具不能继承，目标版本由 -target 指定。
            listOf("MACOSX", "IPHONEOS", "TVOS", "WATCHOS", "XROS", "DRIVERKIT").forEach {
                environment.remove("${it}_DEPLOYMENT_TARGET")
            }
        }
        val modules = try {
            run(tool("python3"), "-c", "import importlib.metadata as m, jinja2, markupsafe; " +
                "print(m.version('Jinja2')); print(m.version('MarkupSafe')); print(jinja2.__file__); print(markupsafe.__file__)", capture = true).trim()
        } catch (error: Exception) {
            throw GradleException("The selected Python cannot import Jinja2/MarkupSafe. On macOS run brew install jinja2-cli; " +
                "see docs/build-guide.md for other platforms. ${error.message}", error)
        }
        val moduleHashes = modules.lines().drop(2).associate { file ->
            val folder = File(file).parentFile
            folder.name to nativeHash(nativeJson(nativeFiles(folder).filter { it.isFile && "__pycache__" !in it.path }
                .associate { it.relativeTo(folder).invariantSeparatorsPath to nativeSha(it) }))
        }
        identity["pythonModules"] = mapOf("details" to modules, "content" to moduleHashes)
        for (name in listOf("git", "curl", "make", "patch", "bash")) {
            val executable = tool(name)
            identity[name] = mapOf("path" to nativePath(executable.canonicalFile), "sha256" to nativeSha(executable))
        }
    }

    private fun requireVersion(name: String, actual: String, minimum: String) {
        val value = Regex("\\d+(?:\\.\\d+)+").find(actual)?.value
            ?: throw GradleException("Cannot determine $name version: $actual")
        val left = value.split('.').map(String::toInt)
        val right = minimum.split('.').map(String::toInt)
        val comparison = (0 until maxOf(left.size, right.size)).asSequence()
            .map { left.getOrElse(it) { 0 }.compareTo(right.getOrElse(it) { 0 }) }.firstOrNull { it != 0 } ?: 0
        if (comparison < 0) throw GradleException("$name $minimum+ is required; found $value at ${tool(name)}. See docs/build-guide.md.")
    }
}
