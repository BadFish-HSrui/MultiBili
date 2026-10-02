@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package tv.hsrui.bolo.download

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSFileType
import platform.Foundation.NSFileTypeRegular
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIAdaptivePresentationControllerDelegateProtocol
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIDevice
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIModalPresentationPopover
import platform.UIKit.UIPresentationController
import platform.UIKit.UISceneActivationStateForegroundActive
import platform.UIKit.UIUserInterfaceIdiomPad
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UIKit.popoverPresentationController
import platform.UIKit.presentationController
import platform.UniformTypeIdentifiers.UTTypeItem
import platform.darwin.NSObject
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

    actual suspend fun openFile(output: DownloadOutput): Boolean = openOutput(output, showDirectory = false)
    actual suspend fun showFile(output: DownloadOutput): Boolean = openOutput(output, showDirectory = true)

    private suspend fun openOutput(output: DownloadOutput, showDirectory: Boolean): Boolean = try {
        val url = withContext(Dispatchers.Default) {
            check(output.location.startsWith('/'))
            // 安装更新可能迁移容器；旧的默认下载路径按最终文件名定位到当前目录。
            val location = if (manager.isReadableFileAtPath(output.location)) output.location else {
                val original = NSURL.fileURLWithPath(output.location)
                val directory = original.URLByDeletingLastPathComponent
                check(original.lastPathComponent == output.fileName)
                check(directory?.lastPathComponent == "Downloads")
                check(directory.URLByDeletingLastPathComponent?.lastPathComponent == "Documents")
                val base = checkNotNull(manager.URLForDirectory(NSDocumentDirectory, NSUserDomainMask, null, false, null)?.path)
                "$base/Downloads/${output.fileName}"
            }
            check(manager.isReadableFileAtPath(location))
            check(manager.attributesOfItemAtPath(location, null)?.get(NSFileType) == NSFileTypeRegular)
            NSURL.fileURLWithPath(location)
        }
        withContext(Dispatchers.Main.immediate) {
            if (showDirectory) downloadFilePresentation.showDirectory(url)
            else downloadFilePresentation.openFile(url)
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        false
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

private val downloadFilePresentation by lazy { DownloadFilePresentation() }

private class DownloadFilePresentation : NSObject(), UIDocumentPickerDelegateProtocol,
    UIAdaptivePresentationControllerDelegateProtocol {
    // 系统 delegate 为弱引用；交接完成前保留控制器，不依赖下载页面的生命周期。
    // 回调可能重新包装 Obj-C 对象，控制器匹配使用其原生对象相等比较。
    private var activityController: UIActivityViewController? = null
    private var picker: UIDocumentPickerViewController? = null

    private fun presenter(): UIViewController? {
        val window = UIApplication.sharedApplication.connectedScenes
            .filterIsInstance<UIWindowScene>()
            .filter { it.activationState == UISceneActivationStateForegroundActive }
            .flatMap { it.windows.filterIsInstance<UIWindow>() }
            .firstOrNull { it.isKeyWindow() } ?: return null
        var controller = window.rootViewController ?: return null
        while (true) {
            if (controller.isBeingDismissed() || controller.isBeingPresented()) return null
            controller = controller.presentedViewController ?: return controller
        }
    }

    fun openFile(url: NSURL): Boolean {
        if (activityController != null || picker != null) return false
        val host = presenter() ?: return false
        val view = host.view
        if (view.window == null) return false
        val controller = UIActivityViewController(activityItems = listOf(url), applicationActivities = null)
        if (UIDevice.currentDevice.userInterfaceIdiom == UIUserInterfaceIdiomPad) {
            controller.modalPresentationStyle = UIModalPresentationPopover
            val popover = controller.popoverPresentationController ?: return false
            popover.sourceView = view
            popover.sourceRect = view.bounds.useContents {
                CGRectMake(origin.x + size.width / 2 - 0.5, origin.y + size.height / 2 - 0.5, 1.0, 1.0)
            }
            popover.permittedArrowDirections = 0uL
            popover.canOverlapSourceViewRect = true
        }
        activityController = controller
        controller.completionWithItemsHandler = { _, _, _, _ -> releaseActivity(controller) }
        controller.presentationController?.delegate = this
        host.presentViewController(controller, animated = true, completion = null)
        return true
    }

    fun showDirectory(url: NSURL): Boolean {
        if (activityController != null || picker != null) return false
        val host = presenter() ?: return false
        val directory = url.URLByDeletingLastPathComponent ?: return false
        val controller = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeItem), asCopy = false)
        controller.directoryURL = directory
        controller.allowsMultipleSelection = false
        controller.shouldShowFileExtensions = true
        controller.delegate = this
        picker = controller
        host.presentViewController(controller, animated = true, completion = null)
        controller.presentationController?.delegate = this
        return true
    }

    private fun releaseActivity(controller: UIActivityViewController) {
        if (activityController == controller) {
            controller.completionWithItemsHandler = null
            controller.presentationController?.delegate = null
            activityController = null
        }
    }

    private fun dismissPicker(controller: UIDocumentPickerViewController) {
        controller.dismissViewControllerAnimated(true) {
            if (picker == controller) picker = null
        }
    }

    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        dismissPicker(controller)
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        dismissPicker(controller)
    }

    override fun presentationControllerDidDismiss(presentationController: UIPresentationController) {
        (presentationController.presentedViewController as? UIActivityViewController)?.let(::releaseActivity)
        if (picker == presentationController.presentedViewController) picker = null
    }
}
