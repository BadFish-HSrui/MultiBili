@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package tv.hsrui.bolo.utils.file

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIAdaptivePresentationControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIPresentationController
import platform.UIKit.UISceneActivationStateForegroundActive
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UIKit.presentationController
import platform.UniformTypeIdentifiers.UTTypeItem
import platform.darwin.NSObject

actual class LocalFileAccess actual constructor() {
    private val manager = NSFileManager.defaultManager
    private val cacheDirectory: String
        get() = checkNotNull(manager.URLForDirectory(NSCachesDirectory, NSUserDomainMask, null, true, null)?.path)

    actual suspend fun temporaryPath(fileName: String): String = withContext(Dispatchers.Default) {
        require(fileName.isNotEmpty() && '/' !in fileName && fileName != "." && fileName != "..")
        val directory = "$cacheDirectory/bolo-statistics-${NSUUID().UUIDString}"
        check(manager.createDirectoryAtPath(directory, true, null, null)) { "无法创建临时目录" }
        "$directory/$fileName"
    }

    actual suspend fun deleteTemporaryFile(path: String) = withContext(Dispatchers.Default) {
        val directory = checkNotNull(NSURL.fileURLWithPath(path).URLByDeletingLastPathComponent)
        require(directory.lastPathComponent?.startsWith("bolo-statistics-") == true &&
            directory.URLByDeletingLastPathComponent?.path == cacheDirectory)
        val directoryPath = checkNotNull(directory.path)
        check(!manager.fileExistsAtPath(directoryPath) || manager.removeItemAtPath(directoryPath, null)) {
            "临时文件清理失败"
        }
    }

    actual suspend fun saveFile(sourcePath: String, suggestedName: String): String? = withContext(Dispatchers.Main.immediate) {
        val source = NSURL.fileURLWithPath(sourcePath)
        require(source.lastPathComponent == suggestedName)
        localFilePresentation.select(
            UIDocumentPickerViewController(forExportingURLs = listOf(source), asCopy = true),
        )?.absoluteString
    }

    actual suspend fun importFile(): String? = withContext(Dispatchers.Main.immediate) {
        val source = localFilePresentation.select(
            UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeItem), asCopy = true),
        ) ?: return@withContext null
        val path = temporaryPath("restore.db")
        val scoped = source.startAccessingSecurityScopedResource()
        try {
            withContext(Dispatchers.Default) {
                check(manager.copyItemAtURL(source, NSURL.fileURLWithPath(path), null)) { "无法读取所选文件" }
                currentCoroutineContext().ensureActive()
            }
            path
        } catch (error: Exception) {
            withContext(NonCancellable) { deleteTemporaryFile(path) }
            throw error
        } finally {
            if (scoped) source.stopAccessingSecurityScopedResource()
        }
    }

    actual suspend fun showFileInFolder(location: String): Boolean = try {
        withContext(Dispatchers.Main.immediate) {
            val file = if (location.startsWith('/')) NSURL.fileURLWithPath(location) else NSURL.URLWithString(location)
            val directory = file?.URLByDeletingLastPathComponent ?: return@withContext false
            localFilePresentation.showDirectory(directory)
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        false
    }
}

private val localFilePresentation by lazy { LocalFilePresentation() }

private class LocalFilePresentation : NSObject(), UIDocumentPickerDelegateProtocol,
    UIAdaptivePresentationControllerDelegateProtocol {
    // UIKit 的 delegate 为弱引用；选择完成或取消前保留控制器。
    private var picker: UIDocumentPickerViewController? = null
    private var pending: CompletableDeferred<NSURL?>? = null

    private fun presenter(): UIViewController? {
        val window = UIApplication.sharedApplication.connectedScenes.filterIsInstance<UIWindowScene>()
            .filter { it.activationState == UISceneActivationStateForegroundActive }
            .flatMap { it.windows.filterIsInstance<UIWindow>() }
            .firstOrNull { it.isKeyWindow() } ?: return null
        var controller = window.rootViewController ?: return null
        while (true) {
            if (controller.isBeingDismissed() || controller.isBeingPresented()) return null
            controller = controller.presentedViewController ?: return controller
        }
    }

    private fun present(controller: UIDocumentPickerViewController): Boolean {
        if (picker != null) return false
        val host = presenter() ?: return false
        controller.allowsMultipleSelection = false
        controller.shouldShowFileExtensions = true
        controller.delegate = this
        picker = controller
        host.presentViewController(controller, animated = true, completion = null)
        controller.presentationController?.delegate = this
        return true
    }

    suspend fun select(controller: UIDocumentPickerViewController): NSURL? {
        check(picker == null && pending == null) { "已有文件窗口打开" }
        val waiting = CompletableDeferred<NSURL?>()
        pending = waiting
        try {
            check(present(controller)) { "无法打开文件窗口" }
            return waiting.await()
        } finally {
            if (picker == controller) withContext(NonCancellable) {
                val dismissed = CompletableDeferred<Unit>()
                controller.dismissViewControllerAnimated(true) {
                    release(controller, null)
                    dismissed.complete(Unit)
                }
                dismissed.await()
            }
            if (pending === waiting) pending = null
        }
    }

    fun showDirectory(directory: NSURL): Boolean {
        val controller = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeItem), asCopy = false)
        controller.directoryURL = directory
        return present(controller)
    }

    private fun release(controller: UIDocumentPickerViewController, url: NSURL?) {
        if (picker != controller) return
        picker = null
        controller.delegate = null
        controller.presentationController?.delegate = null
        val waiting = pending
        pending = null
        waiting?.complete(url)
    }

    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
        controller.dismissViewControllerAnimated(true) { release(controller, url) }
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        controller.dismissViewControllerAnimated(true) { release(controller, null) }
    }

    override fun presentationControllerDidDismiss(presentationController: UIPresentationController) {
        (presentationController.presentedViewController as? UIDocumentPickerViewController)?.let { release(it, null) }
    }
}
