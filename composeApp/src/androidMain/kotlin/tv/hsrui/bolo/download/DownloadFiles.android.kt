package tv.hsrui.bolo.download

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.io.FileOutputStream
import java.lang.ref.WeakReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import tv.hsrui.bolo.utils.url.AppContext

actual class DownloadFiles actual constructor() {
    private val context get() = AppContext.instance
    private val temporary get() = File(context.cacheDir, "bolo-downloads")

    actual suspend fun requestPermission(): Boolean = withContext(Dispatchers.Main.immediate) {
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

    actual suspend fun clearTemporaryFiles() = withContext(Dispatchers.IO) {
        temporary.listFiles()?.filter(File::isDirectory)?.forEach { cleanPending(it.name) }
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

    actual suspend fun publish(id: String, fileName: String, onPublished: suspend (DownloadOutput) -> Unit) = withContext(Dispatchers.IO) {
        val source = File(path(id, "output.mp4"))
        if (Build.VERSION.SDK_INT >= 29) {
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
            @Suppress("DEPRECATION")
            val directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
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

    companion object {
        private var host: WeakReference<ComponentActivity>? = null
        private var permissionLauncher: ActivityResultLauncher<String>? = null
        private var pendingPermission: CompletableDeferred<Boolean>? = null

        fun attach(activity: ComponentActivity) {
            host = WeakReference(activity)
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
                        host = null
                    }
                }
            })
        }
    }
}
