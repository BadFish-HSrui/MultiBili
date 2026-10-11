package tv.hsrui.bolo.utils.file

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.FileKitDialogParent
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.dialogs.openFileSaver
import java.awt.Desktop
import java.awt.Window
import java.io.File
import java.lang.ref.WeakReference
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext

actual class LocalFileAccess actual constructor() {
    actual suspend fun temporaryPath(fileName: String): String = withContext(Dispatchers.IO) {
        require(fileName == File(fileName).name)
        Files.createTempDirectory("bolo-statistics-").resolve(fileName).toString()
    }

    actual suspend fun deleteTemporaryFile(path: String) = withContext(Dispatchers.IO) {
        val file = File(path)
        val directory = file.parentFile
        check(directory != null && directory.name.startsWith("bolo-statistics-") &&
            directory.parentFile.canonicalFile == File(System.getProperty("java.io.tmpdir")).canonicalFile)
        check(!directory.exists() || directory.deleteRecursively()) { "临时文件清理失败" }
    }

    private suspend fun dialogSettings(title: String): FileKitDialogSettings = withContext(Dispatchers.Swing) {
        val window = checkNotNull(host?.get()?.takeIf { it.isDisplayable }) { "无法打开文件选择器" }
        FileKitDialogSettings(title = title, parent = FileKitDialogParent.awt(window))
    }

    actual suspend fun saveFile(sourcePath: String, suggestedName: String): String? {
        val selected = FileKit.openFileSaver(
            suggestedName = suggestedName.removeSuffix(".db"), defaultExtension = "db", allowedExtensions = setOf("db"),
            dialogSettings = dialogSettings("导出数据"),
        ) ?: return null
        return withContext(Dispatchers.IO) {
            val target = selected.file.toPath()
            val pending = Files.createTempFile(target.parent, ".multibili-statistics-", ".tmp")
            try {
                Files.copy(File(sourcePath).toPath(), pending, StandardCopyOption.REPLACE_EXISTING)
                currentCoroutineContext().ensureActive()
                Files.move(pending, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                target.toString()
            } finally {
                Files.deleteIfExists(pending)
            }
        }
    }

    actual suspend fun importFile(): String? {
        val selected = FileKit.openFilePicker(
            type = FileKitType.File(extensions = listOf("db")), dialogSettings = dialogSettings("恢复数据"),
        ) ?: return null
        val path = temporaryPath("restore.db")
        try {
            withContext(Dispatchers.IO) { Files.copy(selected.file.toPath(), File(path).toPath()) }
            currentCoroutineContext().ensureActive()
            return path
        } catch (error: Exception) {
            withContext(kotlinx.coroutines.NonCancellable) { deleteTemporaryFile(path) }
            throw error
        }
    }

    actual suspend fun showFileInFolder(location: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val directory = File(location).parentFile?.takeIf { it.isDirectory } ?: return@withContext false
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(directory)
                true
            } else if (System.getProperty("os.name").startsWith("Linux")) {
                val process = ProcessBuilder("xdg-open", directory.absolutePath)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start()
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

    companion object {
        private var host: WeakReference<Window>? = null
        fun attach(window: Window) { host = WeakReference(window) }
        fun detach(window: Window) { if (host?.get() === window) host = null }
    }
}
