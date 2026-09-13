package tv.hsrui.bolo.player.base

import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest

internal actual fun loadBoloMpvLibrary() {
    val manifest = checkNotNull(BoloMpvNative::class.java.getResourceAsStream("/bolo-native/files.txt")) {
        "缺少播放器原生资源，请先运行 :nativePlayer:prepareDesktopNative"
    }.use { it.readBytes() }
    val digest = sha256(manifest)
    val directory = Path.of(System.getProperty("java.io.tmpdir"), "bolo-native", digest)
    Files.createDirectories(directory)
    FileChannel.open(directory.resolve(".lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE).use { channel ->
        channel.lock().use {
            manifest.toString(Charsets.UTF_8).lineSequence().filter(String::isNotBlank).forEach { line ->
                val (hash, name) = line.split("  ", limit = 2)
                require(name == Path.of(name).fileName.toString() && name != "." && name != "..")
                val target = directory.resolve(name)
                if (!Files.exists(target) || sha256(Files.readAllBytes(target)) != hash) {
                    val bytes = checkNotNull(BoloMpvNative::class.java.getResourceAsStream("/bolo-native/$name")).use { it.readBytes() }
                    check(sha256(bytes) == hash) { "原生库校验失败：$name" }
                    val temporary = Files.createTempFile(directory, name, ".tmp")
                    try {
                        Files.write(temporary, bytes)
                        Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                    } finally { Files.deleteIfExists(temporary) }
                }
            }
        }
    }
    System.load(directory.resolve(System.mapLibraryName("bolo_mpv")).toAbsolutePath().toString())
}

private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
