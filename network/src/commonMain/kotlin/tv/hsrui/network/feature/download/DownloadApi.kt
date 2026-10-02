package tv.hsrui.network.feature.download

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import tv.hsrui.network.constant.BOLO_UA
import kotlin.time.TimeSource

// 媒体传输生命周期独立于页面，并且不携带 API 客户端的账号 Cookie。
private val downloadClient by lazy {
    HttpClient {
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 30_000
            requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
        }
    }
}

suspend fun downloadVideoStream(
    url: String,
    write: suspend (ByteArray, Int) -> Unit,
    onProgress: suspend (Long, Long?) -> Unit,
) = coroutineScope {
    require(url.startsWith("https://") || url.startsWith("http://")) { "下载地址无效" }
    // 同时约束不提供连接超时能力的引擎，收到响应后不限制整个文件的传输时长。
    val connectionDeadline = launch {
        delay(15_000)
        error("下载连接超时")
    }
    try { downloadClient.prepareGet(url) {
        header(HttpHeaders.UserAgent, BOLO_UA)
        header(HttpHeaders.Referrer, "https://www.bilibili.com")
        header(HttpHeaders.AcceptEncoding, "identity")
    }.execute { response ->
        connectionDeadline.cancel()
        check(response.status.value == 200) { "下载失败：HTTP ${response.status.value}" }
        val total = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()?.takeIf { it >= 0 }
        val channel = response.bodyAsChannel()
        val buffer = ByteArray(64 * 1024)
        var received = 0L
        var lastProgress = TimeSource.Monotonic.markNow()
        onProgress(received, total)
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = try { withTimeout(30_000) { channel.readAvailable(buffer) } }
            catch (_: TimeoutCancellationException) { error("下载超过 30 秒未收到数据") }
            if (count < 0) break
            if (count == 0) continue
            write(buffer, count)
            received += count
            if (lastProgress.elapsedNow().inWholeMilliseconds >= 200) {
                onProgress(received, total)
                lastProgress = TimeSource.Monotonic.markNow()
            }
        }
        check(received > 0 && (total == null || received == total)) { "下载文件不完整" }
        // 无 Content-Length 时，只有正常读到 EOF 后才能确认该轨道的实际大小。
        onProgress(received, total ?: received)
    } } finally { connectionDeadline.cancel() }
}
