package tv.hsrui.bolo.utils.file

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.FileKitDialogParent
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.openFileSaver
import java.awt.KeyboardFocusManager
import java.awt.Window
import java.io.File
import java.lang.ref.WeakReference
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import javax.swing.JOptionPane
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext

actual class ImageSaver actual constructor() {
    actual suspend fun requestPermission(): Boolean = true

    actual suspend fun saveImage(image: ImageSaveData): Boolean {
        val parent = withContext(Dispatchers.Swing) {
            val root = checkNotNull(host?.get()?.takeIf { it.isDisplayable }) { "无法打开保存图片窗口" }
            KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow
                ?.takeIf { active -> generateSequence(active) { it.owner }.any { it === root } }
                ?: root
        }
        val extension = image.fileName.substringAfterLast('.')
        val selected = FileKit.openFileSaver(
            suggestedName = image.fileName.substringBeforeLast('.'),
            defaultExtension = extension,
            allowedExtensions = setOf(extension),
            dialogSettings = FileKitDialogSettings(title = "保存图片", parent = FileKitDialogParent.awt(parent)),
        ) ?: return false
        currentCoroutineContext().ensureActive()
        val chosen = selected.file
        val target = if (chosen.extension.equals(extension, ignoreCase = true)) chosen else {
            val name = chosen.name.substringBeforeLast('.', chosen.name).trimEnd(' ', '.').ifEmpty { "MultiBili" }
            File(chosen.parentFile, "$name.$extension")
        }
        val existed = withContext(Dispatchers.IO) { Files.exists(target.toPath(), LinkOption.NOFOLLOW_LINKS) }
        if (target != chosen && existed) {
            val replace = withContext(Dispatchers.Swing) {
                JOptionPane.showConfirmDialog(
                    parent, "${target.name} 已存在，是否替换？", "保存图片",
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE,
                ) == JOptionPane.YES_OPTION
            }
            if (!replace) return false
        }
        return withContext(Dispatchers.IO) {
            val destination = target.toPath().toAbsolutePath()
            val pending = Files.createTempFile(destination.parent, ".multibili-image-", ".tmp")
            try {
                pending.toFile().outputStream().use { stream ->
                    var offset = 0
                    while (offset < image.bytes.size) {
                        currentCoroutineContext().ensureActive()
                        val count = minOf(64 * 1024, image.bytes.size - offset)
                        stream.write(image.bytes, offset, count)
                        offset += count
                    }
                    stream.fd.sync()
                }
                currentCoroutineContext().ensureActive()
                if (existed) {
                    try {
                        Files.move(pending, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                    } catch (_: AtomicMoveNotSupportedException) {
                        Files.move(pending, destination, StandardCopyOption.REPLACE_EXISTING)
                    }
                } else {
                    // 目标在选择后被其他进程创建时，不覆盖未经用户确认的文件。
                    Files.move(pending, destination)
                }
                true
            } finally {
                Files.deleteIfExists(pending)
            }
        }
    }

    companion object {
        private var host: WeakReference<Window>? = null
        fun attach(window: Window) { host = WeakReference(window) }
        fun detach(window: Window) { if (host?.get() === window) host = null }
    }
}
