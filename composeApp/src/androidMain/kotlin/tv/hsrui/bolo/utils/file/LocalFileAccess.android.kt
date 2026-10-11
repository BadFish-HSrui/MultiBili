package tv.hsrui.bolo.utils.file

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.lang.ref.WeakReference
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.koin.mp.KoinPlatform.getKoin

actual class LocalFileAccess actual constructor() {
    private val context get() = getKoin().get<Context>().applicationContext

    actual suspend fun temporaryPath(fileName: String): String = withContext(Dispatchers.IO) {
        require(fileName == File(fileName).name)
        val directory = File(context.cacheDir, "bolo-statistics-${UUID.randomUUID()}")
        check(directory.mkdirs()) { "无法创建临时目录" }
        File(directory, fileName).absolutePath
    }

    actual suspend fun deleteTemporaryFile(path: String) = withContext(Dispatchers.IO) {
        val directory = checkNotNull(File(path).parentFile)
        check(directory.name.startsWith("bolo-statistics-") &&
            directory.parentFile?.canonicalFile == context.cacheDir.canonicalFile)
        check(!directory.exists() || directory.deleteRecursively()) { "临时文件清理失败" }
    }

    private suspend fun select(intent: Intent): Uri? = withContext(Dispatchers.Main.immediate) {
        check(pending == null) { "文件选择器已打开" }
        val currentLauncher = checkNotNull(launcher) { "无法打开文件选择器" }
        val waiting = CompletableDeferred<Uri?>()
        pending = waiting
        try {
            currentLauncher.launch(intent)
        } catch (error: Exception) {
            pending = null
            throw error
        }
        try { waiting.await() } catch (error: CancellationException) {
            // 保留槽位直到旧窗口返回，避免旧结果交给后续请求。
            waiting.cancel()
            throw error
        }
    }

    actual suspend fun saveFile(sourcePath: String, suggestedName: String): String? {
        val uri = select(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_TITLE, suggestedName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }) ?: return null
        try {
            withContext(Dispatchers.IO) {
                checkNotNull(context.contentResolver.openOutputStream(uri, "w")) { "无法写入所选文件" }.use { output ->
                    File(sourcePath).inputStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                        }
                    }
                    output.flush()
                }
            }
        } catch (error: Exception) {
            withContext(NonCancellable + Dispatchers.IO) {
                runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
            }
            throw error
        }
        return uri.toString()
    }

    actual suspend fun importFile(): String? {
        val uri = select(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }) ?: return null
        val path = temporaryPath("restore.db")
        try {
            withContext(Dispatchers.IO) {
                checkNotNull(context.contentResolver.openInputStream(uri)) { "无法读取所选文件" }.use { input ->
                    File(path).outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                        }
                    }
                }
            }
            currentCoroutineContext().ensureActive()
            return path
        } catch (error: Exception) {
            withContext(NonCancellable) { deleteTemporaryFile(path) }
            throw error
        }
    }

    actual suspend fun showFileInFolder(location: String): Boolean = withContext(Dispatchers.Main.immediate) {
        try {
            val uri = Uri.parse(location)
            if (DocumentsContract.isTreeUri(uri)) {
                val directory = DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
                context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(directory, DocumentsContract.Document.MIME_TYPE_DIR)
                    clipData = ClipData.newRawUri("文件位置", directory)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                })
                true
            } else if (DocumentsContract.isDocumentUri(context, uri) && Build.VERSION.SDK_INT >= 26) {
                val activity = host?.get() ?: return@withContext false
                checkNotNull(browserLauncher).launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    putExtra(DocumentsContract.EXTRA_INITIAL_URI, uri)
                })
                activity.isFinishing.not()
            } else false
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        private var host: WeakReference<ComponentActivity>? = null
        private var launcher: ActivityResultLauncher<Intent>? = null
        private var browserLauncher: ActivityResultLauncher<Intent>? = null
        private var pending: CompletableDeferred<Uri?>? = null

        fun attach(activity: ComponentActivity) {
            host = WeakReference(activity)
            launcher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                val waiting = pending
                pending = null
                waiting?.complete(result.data?.data?.takeIf { result.resultCode == Activity.RESULT_OK })
            }
            browserLauncher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
            activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    if (host?.get() === activity) {
                        pending?.complete(null)
                        pending = null
                        launcher = null
                        browserLauncher = null
                        host = null
                    }
                }
            })
        }
    }
}
