package tv.hsrui.bolo.download

import java.io.File
import java.io.FileOutputStream
import java.awt.Desktop
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

actual class DownloadFiles actual constructor() {
    private val temporary = File(System.getProperty("java.io.tmpdir"), "bolo-downloads")

    actual suspend fun openFile(output: DownloadOutput): Boolean = openOutput(output, showDirectory = false)
    actual suspend fun showFile(output: DownloadOutput): Boolean = openOutput(output, showDirectory = true)

    private suspend fun openOutput(output: DownloadOutput, showDirectory: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(output.location)
            if (!file.isAbsolute || !file.isFile || !file.canRead()) return@withContext false
            val target = if (showDirectory) file.parentFile ?: return@withContext false else file
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(target)
                true
            } else if (System.getProperty("os.name").startsWith("Linux")) {
                val process = ProcessBuilder("xdg-open", target.absolutePath)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start()
                val finished = process.waitFor(5, TimeUnit.SECONDS)
                currentCoroutineContext().ensureActive()
                !finished || process.exitValue() == 0
            } else false
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            false
        }
    }

    actual suspend fun requestPermission(): Boolean = true
    actual suspend fun clearTemporaryFiles() = withContext(Dispatchers.IO) {
        temporary.listFiles()?.filter(File::isDirectory)?.forEach { cleanPending(it.name) }
        check(!temporary.exists() || temporary.deleteRecursively()) { "下载临时文件清理失败" }
        Unit
    }
    actual suspend fun prepare(id: String) = withContext(Dispatchers.IO) {
        check(File(temporary, id).mkdirs()) { "无法创建下载临时目录" }
    }
    actual fun path(id: String, name: String): String = File(File(temporary, id), name).absolutePath
    actual suspend fun writeStream(id: String, name: String, producer: suspend (suspend (ByteArray, Int) -> Unit) -> Unit) =
        withContext(Dispatchers.IO) {
            FileOutputStream(path(id, name)).use { output ->
                producer { bytes, count -> output.write(bytes, 0, count) }
                output.fd.sync()
            }
        }

    private fun directory(): File {
        if (!System.getProperty("os.name").startsWith("Linux")) {
            return File(checkNotNull(DownloadNative.downloadsDirectory()) { "无法获取系统下载目录" }.decodeToString())
        }
        val home = System.getProperty("user.home")
        val config = File(System.getenv("XDG_CONFIG_HOME")?.takeIf { File(it).isAbsolute } ?: "$home/.config", "user-dirs.dirs")
        val configured = config.takeIf(File::isFile)?.readLines()?.firstNotNullOfOrNull { line ->
            val raw = Regex("""^\s*XDG_DOWNLOAD_DIR\s*=\s*"((?:\\.|[^"\\])*)"\s*(?:#.*)?$""")
                .matchEntire(line)?.groupValues?.get(1) ?: return@firstNotNullOfOrNull null
            parseDirectory(raw, home)
        }
        return File(configured ?: "$home/Downloads")
    }

    private fun parseDirectory(raw: String, home: String): String? {
        val result = StringBuilder()
        var index = 0
        while (index < raw.length) {
            val char = raw[index]
            when {
                char == '\\' && index + 1 < raw.length && raw[index + 1] in "\\\"\$`" -> result.append(raw[++index])
                char == '$' -> {
                    if (index != 0 || !(raw == "\$HOME" || raw.startsWith("\$HOME/"))) return null
                    result.append(home)
                    index += 4
                }
                char == '`' -> return null
                else -> result.append(char)
            }
            index++
        }
        return result.toString().takeIf { it.startsWith('/') }
    }

    actual suspend fun publish(id: String, fileName: String, onPublished: suspend (DownloadOutput) -> Unit) = withContext(Dispatchers.IO) {
        val directory = directory()
        check(directory.isDirectory || directory.mkdirs()) { "无法创建系统下载目录" }
        val source = File(path(id, "output.mp4"))
        val pending = File(directory, ".bolo-download-$id.pending")
        File(path(id, "publishing")).writeText(pending.absolutePath)
        check(pending.createNewFile()) { "下载保存临时文件已存在" }
        var target: File? = null
        var committed = false
        try {
            source.inputStream().use { input ->
                FileOutputStream(pending).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            }
            currentCoroutineContext().ensureActive()
            withContext(NonCancellable) {
                var suffix = 0
                while (true) {
                    val candidate = File(directory, if (suffix == 0) fileName else "${fileName.removeSuffix(".mp4")}_${suffix}.mp4")
                    val result = DownloadNative.publishFile(pending.absolutePath.encodeToByteArray(), candidate.absolutePath.encodeToByteArray())
                    if (result == 0) { target = candidate; break }
                    check(result == 1) { "MP4 文件保存失败（$result）" }
                    suffix++
                }
                val saved = checkNotNull(target)
                onPublished(DownloadOutput(saved.name, saved.absolutePath, saved.length()))
                committed = true
            }
        } finally {
            if (!committed) target?.let { check(it.delete() || !it.exists()) { "未完成文件清理失败：${it.absolutePath}" } }
            check(pending.delete() || !pending.exists()) { "下载保存临时文件清理失败" }
        }
    }

    actual suspend fun clean(id: String) = withContext(Dispatchers.IO) {
        cleanPending(id)
        val directory = File(temporary, id)
        check(!directory.exists() || directory.deleteRecursively()) { "下载临时文件清理失败" }
        Unit
    }

    private fun cleanPending(id: String) {
        val marker = File(path(id, "publishing"))
        if (marker.isFile) {
            val pending = File(marker.readText())
            check(pending.name == ".bolo-download-$id.pending") { "下载临时路径无效" }
            check(pending.delete() || !pending.exists()) { "下载保存临时文件清理失败" }
        }
    }
}
