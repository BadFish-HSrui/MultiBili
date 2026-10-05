package tv.hsrui.bolo.download

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import tv.hsrui.bolo.player.base.loadBoloMpvLibrary

internal object DownloadNative {
    init { loadBoloMpvLibrary() }
    external fun create(): Long
    external fun run(handle: Long, video: ByteArray, audio: ByteArray?, output: ByteArray): Int
    external fun cancel(handle: Long)
    external fun progress(handle: Long): Int
    external fun error(handle: Long): ByteArray
    external fun destroy(handle: Long)
    external fun downloadsDirectory(): ByteArray?
    external fun publishFile(source: ByteArray, target: ByteArray): Int
}

actual class DownloadMuxer actual constructor() {
    actual suspend fun merge(video: String, audio: String?, output: String, onProgress: suspend (Float) -> Unit) = coroutineScope {
        val handle = DownloadNative.create()
        check(handle != 0L) { "无法创建 MP4 封装器" }
        val worker = async(Dispatchers.IO) {
            val result = DownloadNative.run(handle, video.encodeToByteArray(), audio?.encodeToByteArray(), output.encodeToByteArray())
            check(result >= 0) { "MP4 合成失败：${DownloadNative.error(handle).decodeToString()}" }
        }
        try {
            while (!worker.isCompleted) {
                onProgress(DownloadNative.progress(handle) / 10000f)
                delay(200)
            }
            worker.await()
            onProgress(1f)
        } finally {
            DownloadNative.cancel(handle)
            withContext(NonCancellable) { worker.join() }
            DownloadNative.destroy(handle)
        }
    }
}
