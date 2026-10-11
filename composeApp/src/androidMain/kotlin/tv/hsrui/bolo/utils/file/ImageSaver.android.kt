package tv.hsrui.bolo.utils.file

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
import java.lang.ref.WeakReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import tv.hsrui.bolo.utils.url.AppContext

actual class ImageSaver actual constructor() {
    actual suspend fun requestPermission(): Boolean = withContext(Dispatchers.Main.immediate) {
        if (Build.VERSION.SDK_INT >= 29 || AppContext.instance.checkSelfPermission(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) == PackageManager.PERMISSION_GRANTED
        ) return@withContext true
        val launcher = checkNotNull(permissionLauncher) { "无法申请保存图片权限" }
        val waiting = pendingPermission ?: CompletableDeferred<Boolean>().also {
            pendingPermission = it
            try {
                launcher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } catch (error: Exception) {
                pendingPermission = null
                throw error
            }
        }
        // 直到系统返回才释放槽位，关闭查看器不会把旧权限结果交给新请求。
        waiting.await()
    }

    actual suspend fun saveImage(image: ImageSaveData): Boolean = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        if (Build.VERSION.SDK_INT >= 29) {
            val resolver = AppContext.instance.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, image.fileName)
                put(MediaStore.Images.Media.MIME_TYPE, image.mimeType)
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/MultiBili")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = checkNotNull(resolver.insert(
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values,
            )) { "无法创建图片文件" }
            try {
                checkNotNull(resolver.openOutputStream(uri, "w")) { "无法写入图片文件" }.use { stream ->
                    var offset = 0
                    while (offset < image.bytes.size) {
                        currentCoroutineContext().ensureActive()
                        val count = minOf(64 * 1024, image.bytes.size - offset)
                        stream.write(image.bytes, offset, count)
                        offset += count
                    }
                }
                currentCoroutineContext().ensureActive()
                check(resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null) == 1) {
                    "无法发布图片到相册"
                }
            } catch (error: Exception) {
                try { resolver.delete(uri, null, null) } catch (cleanup: Exception) { error.addSuppressed(cleanup) }
                throw error
            }
        } else {
            @Suppress("DEPRECATION")
            val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "MultiBili")
            check(directory.isDirectory || directory.mkdirs()) { "无法创建图片目录" }
            val extension = image.fileName.substringAfterLast('.')
            val stem = image.fileName.substringBeforeLast('.')
            var file = File(directory, image.fileName)
            var suffix = 1
            while (!file.createNewFile()) {
                currentCoroutineContext().ensureActive()
                file = File(directory, "$stem (${suffix++}).$extension")
            }
            try {
                file.outputStream().use { stream ->
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
                MediaScannerConnection.scanFile(AppContext.instance, arrayOf(file.path), arrayOf(image.mimeType), null)
            } catch (error: Exception) {
                if (!file.delete() && file.exists()) error.addSuppressed(IllegalStateException("未完成的图片清理失败"))
                throw error
            }
        }
        true
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
