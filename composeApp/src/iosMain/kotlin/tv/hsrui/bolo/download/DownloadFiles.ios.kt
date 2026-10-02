@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package tv.hsrui.bolo.download

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSUserDomainMask
import platform.posix.EEXIST
import platform.posix.errno
import platform.posix.fclose
import platform.posix.fflush
import platform.posix.fileno
import platform.posix.fopen
import platform.posix.fsync
import platform.posix.fwrite
import platform.posix.link

actual class DownloadFiles actual constructor() {
    private val manager = NSFileManager.defaultManager
    private val temporary: String by lazy {
        val base = checkNotNull(manager.URLForDirectory(NSCachesDirectory, NSUserDomainMask, null, true, null)?.path)
        "$base/bolo-downloads"
    }
    actual suspend fun requestPermission(): Boolean = true
    actual suspend fun clearTemporaryFiles() = withContext(Dispatchers.Default) {
        check(!manager.fileExistsAtPath(temporary) || manager.removeItemAtPath(temporary, null)) { "下载临时文件清理失败" }
    }
    actual suspend fun prepare(id: String) = withContext(Dispatchers.Default) {
        check(manager.createDirectoryAtPath("$temporary/$id", true, null, null)) { "无法创建下载临时目录" }
    }
    actual fun path(id: String, name: String): String = "$temporary/$id/$name"
    actual suspend fun writeStream(id: String, name: String, producer: suspend (suspend (ByteArray, Int) -> Unit) -> Unit) =
        withContext(Dispatchers.Default) {
            val file = checkNotNull(fopen(path(id, name), "wb")) { "无法创建下载文件" }
            try {
                producer { bytes, count ->
                    val written = bytes.usePinned { fwrite(it.addressOf(0), 1u, count.toULong(), file) }
                    check(written == count.toULong()) { "下载文件写入失败" }
                }
                check(fflush(file) == 0 && fsync(fileno(file)) == 0) { "下载文件保存失败" }
            } finally {
                check(fclose(file) == 0) { "下载文件关闭失败" }
            }
        }

    actual suspend fun publish(id: String, fileName: String, onPublished: suspend (DownloadOutput) -> Unit) = withContext(Dispatchers.Default) {
        val base = checkNotNull(manager.URLForDirectory(NSDocumentDirectory, NSUserDomainMask, null, true, null)?.path)
        val directory = "$base/Downloads"
        check(manager.createDirectoryAtPath(directory, true, null, null)) { "无法创建下载目录" }
        val source = path(id, "output.mp4")
        val size = (manager.attributesOfItemAtPath(source, null)?.get(NSFileSize) as? NSNumber)?.longLongValue ?: error("无法读取文件大小")
        currentCoroutineContext().ensureActive()
        // 同一应用容器内发布完整文件；link 原子创建且不会覆盖同名文件。
        withContext(NonCancellable) {
            var suffix = 0
            while (true) {
                val name = if (suffix == 0) fileName else "${fileName.removeSuffix(".mp4")}_${suffix}.mp4"
                val target = "$directory/$name"
                if (link(source, target) == 0) {
                    try {
                        onPublished(DownloadOutput(name, target, size))
                    } catch (error: Exception) {
                        check(manager.removeItemAtPath(target, null)) { "未完成文件清理失败：$target" }
                        throw error
                    }
                    break
                }
                check(errno == EEXIST) { "MP4 文件保存失败" }
                suffix++
            }
        }
    }
    actual suspend fun clean(id: String) = withContext(Dispatchers.Default) {
        val directory = "$temporary/$id"
        check(!manager.fileExistsAtPath(directory) || manager.removeItemAtPath(directory, null)) { "下载临时文件清理失败" }
    }
}
