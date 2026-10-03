package tv.hsrui.bolo.download

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.MediaStore
import android.provider.DocumentsContract
import android.content.ContentUris
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.util.AtomicFile
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.io.FileOutputStream
import java.io.FileNotFoundException
import java.lang.ref.WeakReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import tv.hsrui.bolo.utils.url.AppContext

class DownloadFileProvider : FileProvider()

actual class DownloadFiles actual constructor() {
    private val context get() = AppContext.instance
    private val temporary get() = File(context.cacheDir, "bolo-downloads")

    actual suspend fun exists(output: DownloadOutput): Boolean = withContext(Dispatchers.IO) {
        if (output.location.startsWith("content://")) mediaFileExists(outputUri(output))
        else fileExists(outputFile(output))
    }

    actual suspend fun delete(output: DownloadOutput) = withContext(Dispatchers.IO) {
        if (output.location.startsWith("content://")) {
            val uri = outputUri(output)
            deleteContentFile(uri)
        } else {
            val file = outputFile(output)
            if (fileExists(file)) {
                try { Os.remove(file.absolutePath) }
                catch (error: ErrnoException) { if (error.errno != OsConstants.ENOENT) throw error }
            }
        }
    }

    private fun outputUri(output: DownloadOutput): Uri = Uri.parse(output.location).also {
        require(it.scheme == "content" && (
            it.authority == MediaStore.AUTHORITY && ContentUris.parseId(it) >= 0 ||
                DocumentsContract.isTreeUri(it) && DocumentsContract.isDocumentUri(context, it)
            )) { "下载文件 URI 无效" }
    }

    private fun mediaEntryExists(uri: Uri): Boolean =
        checkNotNull(context.contentResolver.query(uri, arrayOf(
            if (DocumentsContract.isDocumentUri(context, uri)) DocumentsContract.Document.COLUMN_DOCUMENT_ID
            else MediaStore.MediaColumns._ID,
        ), null, null, null)) {
            "无法检查下载文件"
        }.use { it.moveToFirst() }

    private fun mediaFileExists(uri: Uri): Boolean {
        if (!mediaEntryExists(uri)) return false
        return try {
            checkNotNull(context.contentResolver.openFileDescriptor(uri, "r")) { "无法读取下载文件" }.use { }
            true
        } catch (error: FileNotFoundException) {
            val cause = generateSequence(error.cause) { it.cause }.filterIsInstance<ErrnoException>().firstOrNull()
            // Provider 可能将其他打开错误包装为 FileNotFoundException，不能一律判为缺失。
            if (cause?.errno != OsConstants.ENOENT && mediaEntryExists(uri)) throw error
            false
        }
    }

    private fun deleteContentFile(uri: Uri) {
        if (!mediaEntryExists(uri)) return
        val deleted = if (DocumentsContract.isDocumentUri(context, uri)) {
            DocumentsContract.deleteDocument(context.contentResolver, uri)
        } else context.contentResolver.delete(uri, null, null) > 0
        check(deleted || !mediaEntryExists(uri)) { "下载文件删除失败" }
    }

    private fun outputFile(output: DownloadOutput): File = File(output.location).also {
        require(it.isAbsolute && '\u0000' !in output.location) { "下载文件路径无效" }
    }

    private fun fileExists(file: File): Boolean = try {
        check(OsConstants.S_ISREG(Os.lstat(file.absolutePath).st_mode)) { "下载文件路径不是普通文件" }
        true
    } catch (error: ErrnoException) {
        if (error.errno != OsConstants.ENOENT) throw error
        false
    }

    actual suspend fun openFile(output: DownloadOutput): Boolean = openOutput(output, showDirectory = false)
    actual suspend fun showFile(output: DownloadOutput): Boolean = openOutput(output, showDirectory = true)

    private suspend fun openOutput(output: DownloadOutput, showDirectory: Boolean): Boolean = try {
        val uri = withContext(Dispatchers.IO) {
            val uri = if (output.location.startsWith("content://")) {
                outputUri(output)
            } else {
                val file = outputFile(output)
                check(fileExists(file) && file.canRead())
                FileProvider.getUriForFile(context, "${context.packageName}.download.files", file)
            }
            checkNotNull(context.contentResolver.openFileDescriptor(uri, "r")).use { }
            uri
        }
        withContext(Dispatchers.Main.immediate) {
            val intent = if (showDirectory) {
                if (DocumentsContract.isTreeUri(uri)) {
                    val directory = DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(directory, DocumentsContract.Document.MIME_TYPE_DIR)
                        clipData = ClipData.newRawUri("下载位置", directory)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                } else Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS)
            } else {
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "video/mp4")
                    clipData = ClipData.newRawUri(output.fileName, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        false
    }

    actual suspend fun pickDirectory(initialDirectory: String): String? {
        val selected = withContext(Dispatchers.Main.immediate) {
            check(pendingDirectory == null) { "文件夹选择器已打开" }
            val launcher = checkNotNull(directoryLauncher) { "无法打开文件夹选择器" }
            val waiting = CompletableDeferred<Uri?>()
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
                if (Build.VERSION.SDK_INT >= 26) {
                    val initial = if (initialDirectory.isEmpty() || initialDirectory == directory().absolutePath) {
                        DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:${Environment.DIRECTORY_DOWNLOADS}")
                    } else Uri.parse(initialDirectory)
                    putExtra(DocumentsContract.EXTRA_INITIAL_URI, initial)
                }
            }
            pendingDirectory = waiting
            try {
                launcher.launch(intent)
            } catch (error: Exception) {
                pendingDirectory = null
                throw error
            }
            waiting.await()
        } ?: return null
        withContext(Dispatchers.IO) { writableTree(selected.toString()) }
        return selected.toString()
    }

    actual suspend fun directoryDisplayName(directory: String): String = withContext(Dispatchers.IO) {
        if (directory.isEmpty()) return@withContext directory().absolutePath
        val tree = Uri.parse(directory)
        if (tree.authority != "com.android.externalstorage.documents" || !DocumentsContract.isTreeUri(tree)) {
            return@withContext directory
        }
        val documentId = DocumentsContract.getTreeDocumentId(tree)
        val volume = documentId.substringBefore(':')
        @Suppress("DEPRECATION")
        val root = if (volume == "primary") Environment.getExternalStorageDirectory()
        else if (Build.VERSION.SDK_INT >= 30) {
            context.getSystemService(StorageManager::class.java)?.storageVolumes
                ?.firstOrNull { it.uuid.equals(volume, ignoreCase = true) }?.directory
        } else null
        root?.let { File(it, documentId.substringAfter(':', "")).absolutePath } ?: directory
    }

    @Suppress("DEPRECATION")
    private fun directory(): File = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

    private fun documentName(uri: Uri): String =
        checkNotNull(context.contentResolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)) {
            "无法读取下载目录或文件"
        }.use {
            check(it.moveToFirst()) { "下载目录或文件已不存在" }
            checkNotNull(it.getString(0)) { "无法读取下载目录或文件名称" }
        }

    private fun writableTree(directory: String): Uri {
        val tree = Uri.parse(directory)
        require(tree.scheme == "content" && DocumentsContract.isTreeUri(tree)) { "下载目录 URI 无效" }
        check(context.contentResolver.persistedUriPermissions.any {
            it.uri == tree && it.isReadPermission && it.isWritePermission
        }) { "下载目录授权已失效，请重新选择" }
        val document = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        checkNotNull(context.contentResolver.query(document, arrayOf(
            DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_FLAGS,
        ), null, null, null)) { "无法检查下载目录" }.use {
            check(it.moveToFirst() && it.getString(0) == DocumentsContract.Document.MIME_TYPE_DIR &&
                it.getInt(1) and DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE != 0) {
                "下载目录不可用或不可写，请重新选择"
            }
        }
        return document
    }

    actual suspend fun requestPermission(directory: String): Boolean = withContext(Dispatchers.Main.immediate) {
        if (directory.isNotEmpty() && directory != directory().absolutePath) {
            withContext(Dispatchers.IO) { writableTree(directory) }
            return@withContext true
        }
        if (Build.VERSION.SDK_INT >= 29 || context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            return@withContext true
        }
        val launcher = permissionLauncher ?: return@withContext false
        val waiting = pendingPermission ?: CompletableDeferred<Boolean>().also {
            pendingPermission = it
            launcher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        waiting.await()
    }

    actual suspend fun clearTemporaryFiles(completedOutputs: List<DownloadOutput>) = withContext(Dispatchers.IO) {
        val completedLocations = completedOutputs.mapTo(mutableSetOf()) { it.location }
        temporary.listFiles()?.filter(File::isDirectory)?.forEach { cleanPending(it.name, completedLocations) }
        check(!temporary.exists() || temporary.deleteRecursively()) { "下载临时文件清理失败" }
        if (Build.VERSION.SDK_INT >= 29) {
            val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            @Suppress("DEPRECATION")
            val includingPending = MediaStore.setIncludePending(collection)
            context.contentResolver.query(includingPending, arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.IS_PENDING}=1 AND ${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ? AND ${MediaStore.MediaColumns.OWNER_PACKAGE_NAME}=?",
                arrayOf("bolo-download-%.pending.mp4", context.packageName), null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val uri = android.content.ContentUris.withAppendedId(includingPending, cursor.getLong(0))
                    check(context.contentResolver.delete(uri, null, null) > 0) { "未完成下载清理失败" }
                }
            }
        }
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

    actual suspend fun publish(id: String, fileName: String, directory: String, onPublished: suspend (DownloadOutput) -> Unit) = withContext(Dispatchers.IO) {
        val source = File(path(id, "output.mp4"))
        if (directory.isNotEmpty() && directory != directory().absolutePath) {
            publishDocument(id, fileName, directory, source, onPublished)
        } else if (Build.VERSION.SDK_INT >= 29) {
            val resolver = context.contentResolver
            val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            val pending = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "bolo-download-$id.pending.mp4")
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = checkNotNull(resolver.insert(collection, pending)) { "无法创建下载文件" }
            var committed = false
            try {
                checkNotNull(resolver.openOutputStream(uri, "w")) { "无法写入系统下载目录" }.use { output ->
                    source.inputStream().use { input ->
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
                currentCoroutineContext().ensureActive()
                var suffix = 0
                var name: String
                while (true) {
                    name = if (suffix == 0) fileName else "${fileName.removeSuffix(".mp4")}_${suffix}.mp4"
                    val exists = resolver.query(collection, arrayOf(MediaStore.MediaColumns._ID),
                        "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?",
                        arrayOf(name, Environment.DIRECTORY_DOWNLOADS + "/"), null)?.use { it.moveToFirst() } ?: false
                    if (!exists) break
                    suffix++
                }
                withContext(NonCancellable) {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                        put(MediaStore.MediaColumns.IS_PENDING, 0)
                    }
                    check(resolver.update(uri, values, null, null) == 1) { "下载文件发布失败" }
                    val actualName = checkNotNull(resolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
                        ?.use { if (it.moveToFirst()) it.getString(0) else null }) { "无法确认最终文件名" }
                    onPublished(DownloadOutput(actualName, uri.toString(), source.length()))
                    committed = true
                }
            } finally {
                if (!committed) check(resolver.delete(uri, null, null) > 0) { "未完成下载清理失败" }
            }
        } else {
            val directory = directory()
            check(directory.isDirectory || directory.mkdirs()) { "无法创建系统下载目录" }
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
                    MediaScannerConnection.scanFile(context, arrayOf(saved.absolutePath), arrayOf("video/mp4"), null)
                }
            } finally {
                if (!committed) target?.let { check(it.delete() || !it.exists()) { "未完成文件清理失败" } }
                check(pending.delete() || !pending.exists()) { "下载保存临时文件清理失败" }
            }
        }
    }
    private suspend fun publishDocument(
        id: String,
        fileName: String,
        directory: String,
        source: File,
        onPublished: suspend (DownloadOutput) -> Unit,
    ) {
        val resolver = context.contentResolver
        val parent = writableTree(directory)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(parent, DocumentsContract.getDocumentId(parent))
        val names = mutableSetOf<String>()
        val existingIds = mutableSetOf<String>()
        checkNotNull(resolver.query(children, arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        ), null, null, null)) { "无法检查下载目录中的文件" }.use { cursor ->
            while (cursor.moveToNext()) {
                existingIds.add(cursor.getString(0))
                names.add(cursor.getString(1))
            }
        }
        var suffix = 0
        var name = fileName
        while (name in names) {
            name = "${fileName.removeSuffix(".mp4")}_${++suffix}.mp4"
        }
        val marker = AtomicFile(File(path(id, "publishing-document")))
        var created: Uri? = null
        var committed = false
        try {
            // 创建全新文档并立即持久化清理标记；中途取消也必须完成此交接。
            withContext(NonCancellable) {
                val uri = checkNotNull(DocumentsContract.createDocument(resolver, parent, "video/mp4", name)) {
                    "无法在所选目录创建下载文件"
                }
                check(DocumentsContract.getDocumentId(uri) !in existingIds) { "系统返回了已有文件，已停止保存" }
                created = uri
                val stream = marker.startWrite()
                try {
                    stream.write(uri.toString().encodeToByteArray())
                    stream.fd.sync()
                    marker.finishWrite(stream)
                } catch (error: Exception) {
                    marker.failWrite(stream)
                    throw error
                }
                check(marker.readFully().decodeToString() == uri.toString()) { "下载保存清理标记写入失败" }
            }
            val uri = checkNotNull(created)
            checkNotNull(resolver.openOutputStream(uri, "w")) { "无法写入所选下载目录" }.use { output ->
                source.inputStream().use { input ->
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
            currentCoroutineContext().ensureActive()
            withContext(NonCancellable) {
                onPublished(DownloadOutput(documentName(uri), uri.toString(), source.length()))
                committed = true
            }
        } finally {
            withContext(NonCancellable) {
                if (!committed) created?.let(::deleteContentFile)
                marker.delete()
            }
        }
    }

    actual suspend fun clean(id: String, completedOutput: DownloadOutput?) = withContext(Dispatchers.IO) {
        cleanPending(id, setOfNotNull(completedOutput?.location))
        val directory = File(temporary, id)
        check(!directory.exists() || directory.deleteRecursively()) { "下载临时文件清理失败" }
        Unit
    }

    private fun cleanPending(id: String, completedLocations: Set<String>) {
        val documentFile = File(path(id, "publishing-document"))
        val documentMarker = AtomicFile(documentFile)
        if (documentFile.exists() || File(documentFile.path + ".bak").exists()) {
            val location = documentMarker.readFully().decodeToString()
            if (location !in completedLocations) {
                deleteContentFile(outputUri(DownloadOutput("", location, 0)))
            }
            documentMarker.delete()
        }
        val marker = File(path(id, "publishing"))
        if (marker.isFile) {
            val pending = File(marker.readText())
            check(pending.name == ".bolo-download-$id.pending") { "下载临时路径无效" }
            check(pending.delete() || !pending.exists()) { "下载保存临时文件清理失败" }
        }
    }

    companion object {
        private var host: WeakReference<ComponentActivity>? = null
        private var permissionLauncher: ActivityResultLauncher<String>? = null
        private var pendingPermission: CompletableDeferred<Boolean>? = null
        private var directoryLauncher: ActivityResultLauncher<Intent>? = null
        private var pendingDirectory: CompletableDeferred<Uri?>? = null

        fun attach(activity: ComponentActivity) {
            host = WeakReference(activity)
            directoryLauncher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                val waiting = pendingDirectory
                pendingDirectory = null
                try {
                    val data = result.data
                    val uri = data?.data?.takeIf { result.resultCode == Activity.RESULT_OK }
                    if (uri != null) {
                        val requiredFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        val grantedFlags = data.flags and requiredFlags
                        check(grantedFlags == requiredFlags) { "未获得下载目录读写权限" }
                        activity.contentResolver.takePersistableUriPermission(uri, grantedFlags)
                    }
                    waiting?.complete(uri)
                } catch (error: Exception) {
                    waiting?.completeExceptionally(error)
                }
            }
            permissionLauncher = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                pendingPermission?.complete(granted)
                pendingPermission = null
            }
            activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    if (host?.get() === activity) {
                        pendingPermission?.complete(false)
                        pendingPermission = null
                        permissionLauncher = null
                        pendingDirectory?.complete(null)
                        pendingDirectory = null
                        directoryLauncher = null
                        host = null
                    }
                }
            })
        }
    }
}
