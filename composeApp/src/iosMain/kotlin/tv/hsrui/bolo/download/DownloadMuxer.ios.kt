@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package tv.hsrui.bolo.download

import kotlinx.cinterop.toKString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import tv.hsrui.bolo.player.nativeinterop.bolo_download_cancel
import tv.hsrui.bolo.player.nativeinterop.bolo_download_create
import tv.hsrui.bolo.player.nativeinterop.bolo_download_destroy
import tv.hsrui.bolo.player.nativeinterop.bolo_download_error
import tv.hsrui.bolo.player.nativeinterop.bolo_download_progress
import tv.hsrui.bolo.player.nativeinterop.bolo_download_run

actual class DownloadMuxer actual constructor() {
    actual suspend fun merge(video: String, audio: String?, output: String, onProgress: suspend (Float) -> Unit) = coroutineScope {
        val handle = checkNotNull(bolo_download_create()) { "无法创建 MP4 封装器" }
        val worker = async(Dispatchers.Default) {
            val result = bolo_download_run(handle, video, audio, output)
            check(result >= 0) { "MP4 合成失败：${bolo_download_error(handle)?.toKString()}" }
        }
        try {
            while (!worker.isCompleted) {
                onProgress(bolo_download_progress(handle) / 10000f)
                delay(200)
            }
            worker.await()
            onProgress(1f)
        } finally {
            bolo_download_cancel(handle)
            withContext(NonCancellable) { worker.join() }
            bolo_download_destroy(handle)
        }
    }
}
